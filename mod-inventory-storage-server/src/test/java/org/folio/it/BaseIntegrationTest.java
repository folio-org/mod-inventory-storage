package org.folio.it;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToIgnoreCase;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.folio.HttpStatus;
import org.folio.dataimport.testsupport.kafka.KafkaExtension;
import org.folio.dataimport.testsupport.postgres.PostgresExtension;
import org.folio.dataimport.testsupport.rest.SharedRestVerticleSupport;
import org.folio.dataimport.testsupport.rest.SharedRestVerticleSupport.SharedRestVerticle;
import org.folio.dataimport.testsupport.tenant.TenantTestSupport;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.domainevent.SettingEvent;
import org.folio.support.ResourcePaths;
import org.folio.support.kafka.FakeKafkaConsumer;
import org.folio.support.messages.SettingEventMessageChecks;
import org.folio.utility.S3Utility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

@ExtendWith(VertxExtension.class)
public abstract class BaseIntegrationTest {

  public static final String TENANT_ID = "test";
  protected static final String CONSORTIUM_ID = "0060045d-35a6-4935-a923-641bc135a47d";
  protected static final String CONSORTIUM_CENTRAL_TENANT = "central";
  protected static final String CONSORTIUM_MEMBER_TENANT = "member";

  protected static final String USER_ID = UUID.randomUUID().toString();
  protected static final String MODULE_ID = "mod-inventory-storage-1.0.0";

  @RegisterExtension
  protected static WireMockExtension wm = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort()
      .notifier(new ConsoleNotifier(true)))
    .build();

  protected static HttpClient client;
  protected static FakeKafkaConsumer KAFKA_CONSUMER;
  private static final String USER_TENANTS_PATH = "/user-tenants?limit=1";

  // inventory.optimize-updates.enabled is a persisted, tenant-wide setting (not per-test state)
  // that several *IT classes toggle to true to test the optimize-updates behavior. Several relied
  // on a sibling test happening to run afterward and setting it back to false, but JUnit doesn't
  // guarantee method execution order - left at true, it silently changes update/event-publishing
  // behavior for every later test in the whole suite that assumes the default (false), which is
  // exactly what made HoldingsItemPropagationIT flaky in CI (MODINVSTOR-1608). Resetting it here,
  // unconditionally before every single test in the module, is the only place that can guarantee
  // no test ever starts with it in a leaked state, regardless of which other *IT class ran before.
  private static final String OPTIMIZE_UPDATES_SETTING_KEY = "inventory.optimize-updates.enabled";
  private static final String OPTIMIZE_UPDATES_SETTING_PATH =
    ResourcePaths.INVENTORY_SETTINGS + "/" + OPTIMIZE_UPDATES_SETTING_KEY;

  @RegisterExtension
  private static final PostgresExtension POSTGRES = new PostgresExtension();

  @RegisterExtension
  private static final KafkaExtension KAFKA = new KafkaExtension();

  private static final List<String> MIGRATION_SEEDED_TABLES =
    List.of("hrid_settings", "instance_date_type", "subject_source", "subject_type", "settings");
  private static final List<String> ALL_TENANTS =
    List.of(TENANT_ID, CONSORTIUM_CENTRAL_TENANT, CONSORTIUM_MEMBER_TENANT);

  @RegisterExtension
  private static final SharedVerticleExtension SHARED_VERTICLE = new SharedVerticleExtension();

  private static int port;

  @SneakyThrows
  public static JsonObject pojo2JsonObject(Object entity) {
    return PostgresClient.pojo2JsonObject(entity);
  }

  /**
   * {@link WireMockExtension} resets all stub mappings before every test method (not just once
   * per class), so the stub registered in {@link #beforeAll} would otherwise only survive the
   * first test method of each class — re-register it here so every test sees it.
   */
  @BeforeEach
  public void removeAllEvents() {
    KAFKA_CONSUMER.discardAllMessages();
    mockUserTenantsForNonConsortiumMember();
    mockUserTenantsForConsortiumMember(CONSORTIUM_CENTRAL_TENANT);
    mockUserTenantsForConsortiumMember(CONSORTIUM_MEMBER_TENANT);
    mockConsortiumTenants();
  }

  /**
   * See the field comment on {@link #OPTIMIZE_UPDATES_SETTING_KEY} for why this exists. The GET
   * check keeps this a no-op (a single cheap read) for the overwhelming majority of tests, which
   * never touch the setting; only a test that runs right after one that left it at true pays for
   * the PATCH and the await below - which is not just fire-and-forget: SettingsService's read of
   * this setting is served from a cache that's only ever refreshed reactively, by consuming the
   * SettingEvent this PATCH publishes, never invalidated synchronously by the PATCH itself. Not
   * waiting for that event to actually be observed would reopen the exact race this method exists
   * to close.
   */
  @BeforeEach
  public void resetOptimizeUpdatesSetting() {
    var setting = await(doGet(client, OPTIMIZE_UPDATES_SETTING_PATH)).jsonBody();
    if (!Boolean.parseBoolean(setting.getString("value"))) {
      return;
    }
    var settingId = setting.getString("id");
    KAFKA_CONSUMER.discardAllMessages();
    await(doPatch(client, OPTIMIZE_UPDATES_SETTING_PATH, new JsonObject().put("value", false)));
    new SettingEventMessageChecks(KAFKA_CONSUMER).settingEventPublished(
      new SettingEvent(settingId, OPTIMIZE_UPDATES_SETTING_KEY, false, TENANT_ID));
  }

  protected static URL vertxUrl() {
    try {
      return new URI("http", null, "localhost", port, "", null, null).toURL();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected static Future<TestResponse> doGet(HttpClient client, String requestUri) {
    return doRequest(client, HttpMethod.GET, requestUri, null);
  }

  protected static Future<TestResponse> doGet(HttpClient client, String requestUri, String tenantId) {
    return doRequest(client, HttpMethod.GET, requestUri, tenantId, null);
  }

  protected static Future<TestResponse> doPost(HttpClient client, String requestUri, JsonObject body) {
    return doRequest(client, HttpMethod.POST, requestUri, body);
  }

  protected static Future<TestResponse> doPost(HttpClient client, String requestUri, String tenantId, JsonObject body) {
    return doRequest(client, HttpMethod.POST, requestUri, tenantId, body);
  }

  protected static Future<TestResponse> doPut(HttpClient client, String requestUri, JsonObject body) {
    return doRequest(client, HttpMethod.PUT, requestUri, body);
  }

  protected static Future<TestResponse> doPut(HttpClient client, String requestUri, String tenantId, JsonObject body) {
    return doRequest(client, HttpMethod.PUT, requestUri, tenantId, body);
  }

  protected static Future<TestResponse> doPatch(HttpClient client, String requestUri, JsonObject body) {
    return doRequest(client, HttpMethod.PATCH, requestUri, body);
  }

  protected static Future<TestResponse> doPatch(HttpClient client, String requestUri, String tenantId,
                                                JsonObject body) {
    return doRequest(client, HttpMethod.PATCH, requestUri, tenantId, body);
  }

  protected static Future<TestResponse> doDelete(HttpClient client, String requestUri) {
    return doRequest(client, HttpMethod.DELETE, requestUri, null);
  }

  protected static Future<TestResponse> doDelete(HttpClient client, String requestUri, String tenantId) {
    return doRequest(client, HttpMethod.DELETE, requestUri, tenantId, null);
  }

  protected static Future<TestResponse> doRequest(HttpClient client, HttpMethod method,
                                                  String requestUri, JsonObject body) {
    return doRequest(client, method, requestUri, TENANT_ID, body);
  }

  protected static Future<TestResponse> doRequest(HttpClient client, HttpMethod method,
                                                  String requestUri, String tenantId, JsonObject body) {
    return client.request(method, port, "localhost", requestUri)
      .compose(req -> {
        var request = addDefaultHeaders(req, tenantId);
        return (body == null ? request.send() : request.send(body.toBuffer()))
          .compose(resp -> resp.body().map(respBody -> new TestResponse(resp.statusCode(), respBody)));
      });
  }

  protected static void mockUserTenantsForNonConsortiumMember() {
    mockUserTenantsForNonConsortiumMember(TENANT_ID);
  }

  protected static void mockUserTenantsForNonConsortiumMember(String tenantId) {
    var emptyUserTenantsCollection = new JsonObject()
      .put("userTenants", JsonArray.of());
    wm.stubFor(WireMock.get(USER_TENANTS_PATH)
      .withHeader(XOkapiHeaders.TENANT, equalToIgnoreCase(tenantId))
      .willReturn(WireMock.ok().withBody(emptyUserTenantsCollection.encodePrettily())));
  }

  /**
   * Installs {@code tenantId} on the shared verticle with custom attributes, bypassing the
   * "already enabled, never touch again" tracking {@link SharedRestVerticle#enableTenantIfAbsent}
   * keeps for the default per-class tenant install in {@link #beforeAll} - for a tenant id owned
   * entirely by one test class, e.g. to install with {@code loadReference=true} (the shared
   * {@code TENANT_ID} tenant always installs with {@code loadReference=false}) or to replay a
   * {@code moduleFrom}/{@code moduleTo} upgrade, neither of which the default install supports.
   *
   * <p>Sends both {@code X-Okapi-Url} (this WireMock instance) and {@code X-Okapi-Url-to} (the
   * shared verticle's own address) on the {@code POST /_/tenant} call, matching what the legacy
   * {@code rest.api} stack's {@code HttpClient} always sent. {@code TenantLoading} (RMB, the
   * class that actually loads reference/sample data during tenant install) prefers
   * {@code X-Okapi-Url-to} for routing its own generated requests to the real module, but any
   * application code invoked while a sample-data record is being created - e.g.
   * {@code ConsortiumDataCache}, which holdings/item creation calls to check consortium
   * membership - reads plain {@code X-Okapi-Url} off that generated request instead. Without
   * this, both headers default to the same value from a bare {@code WebClient}, so that
   * consortium check misroutes to the module's own port (a bare 404, since it defines no such
   * endpoint) instead of this WireMock instance, and every holdings/item sample record silently
   * fails to load.
   */
  protected static void installTenant(String tenantId, TenantAttributes attributes) {
    var connectionUrl = SHARED_VERTICLE.shared.getConnectionUrl();
    var webClient = WebClient.create(SHARED_VERTICLE.shared.getVertx());
    webClient.addInterceptor(context -> {
      context.request().putHeader(XOkapiHeaders.URL, wm.baseUrl());
      context.request().putHeader(XOkapiHeaders.URL_TO, connectionUrl);
      context.next();
    });

    await(TenantTestSupport.enableTenant(webClient, connectionUrl, tenantId, null, attributes));
  }

  protected static void mockUserTenantsForConsortiumMember(String tenantId) {
    var userTenantsCollection = new JsonObject()
      .put("userTenants", new JsonArray()
        .add(new JsonObject()
          .put("centralTenantId", CONSORTIUM_CENTRAL_TENANT)
          .put("consortiumId", CONSORTIUM_ID)));
    wm.stubFor(WireMock.get(USER_TENANTS_PATH)
      .withHeader(XOkapiHeaders.TENANT, equalToIgnoreCase(tenantId))
      .willReturn(WireMock.ok().withBody(userTenantsCollection.encodePrettily())));
  }

  protected static void mockConsortiumTenants() {
    var tenantsCollection = new JsonObject()
      .put("tenants", new JsonArray()
        .add(new JsonObject().put("id", CONSORTIUM_CENTRAL_TENANT).put("isCentral", true))
        .add(new JsonObject().put("id", CONSORTIUM_MEMBER_TENANT).put("isCentral", false)));
    wm.stubFor(WireMock.get("/consortia/" + CONSORTIUM_ID + "/tenants")
      .willReturn(WireMock.ok().withBody(tenantsCollection.encodePrettily())));
  }

  protected static Handler<AsyncResult<TestResponse>> verifyStatus(VertxTestContext ctx, HttpStatus expectedStatus) {
    return ctx.succeeding(response -> ctx.verify(() -> assertEquals(expectedStatus.toInt(), response.status())));
  }

  /**
   * Runs a raw SQL query against the default tenant's schema on the shared verticle's
   * Postgres client, e.g. for asserting on tables (like audit tables) with no REST endpoint.
   */
  protected static RowSet<Row> runQuery(String sql) {
    return await(PostgresClient.getInstance(SHARED_VERTICLE.shared.getVertx(), TENANT_ID).select(sql));
  }

  @BeforeAll
  static void beforeAll() {
    port = SHARED_VERTICLE.shared.getPort();
    client = SHARED_VERTICLE.shared.getVertx().createHttpClient();
    for (String tenant : ALL_TENANTS) {
      SHARED_VERTICLE.shared.enableTenantIfAbsent(tenant, null, tenantAttributes());
    }

    KAFKA_CONSUMER.discardAllMessages();
  }

  /**
   * Truncates every table in each of {@link #ALL_TENANTS}' schemas once this class's tests are
   * done, except the tables a Liquibase migration seeds exactly once when the schema is first
   * created ({@link #MIGRATION_SEEDED_TABLES}) — nothing re-seeds those afterwards, so wiping them
   * would break any later class relying on their default rows (e.g. {@code hrid_settings}, which
   * {@code HridManager} expects to always exist).
   *
   * <p>The shared verticle and its Postgres/Kafka containers now live for the whole JVM (see
   * {@link SharedVerticleExtension}), so a class can no longer rely on getting a freshly
   * provisioned, empty database the way it could when each class deployed its own throwaway
   * stack. This restores that same starting point for whichever class runs next, without
   * hand-maintaining a per-table list of what *to* clear (the {@code pg_tables} lookup covers
   * every table but the excluded ones, unlike the legacy stack's {@code TestBase.clearData()}).
   */
  @AfterAll
  static void afterAll() throws InterruptedException, ExecutionException, TimeoutException {
    for (String tenant : ALL_TENANTS) {
      truncateAllTables(tenant);
    }
  }

  private static TenantAttributes tenantAttributes() {
    return new TenantAttributes()
      .withModuleTo(MODULE_ID)
      .withParameters(TenantTestSupport.dataLoadingParameters(false, false));
  }

  private static void truncateAllTables(String tenantId)
    throws InterruptedException, ExecutionException, TimeoutException {

    String schema = tenantId + "_mod_inventory_storage";
    String excluded = MIGRATION_SEEDED_TABLES.stream()
      .map(table -> "'" + table + "'")
      .collect(Collectors.joining(", "));
    String sql = "DO $$ DECLARE r RECORD; BEGIN "
                 + "FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = '" + schema + "' "
                 + "AND tablename NOT IN (" + excluded + ")) LOOP "
                 + "EXECUTE 'TRUNCATE TABLE " + schema + ".' || quote_ident(r.tablename) || ' CASCADE'; "
                 + "END LOOP; END $$;";
    PostgresClient.getInstance(SHARED_VERTICLE.shared.getVertx(), tenantId)
      .execute(sql)
      .toCompletionStage()
      .toCompletableFuture()
      .get(30, TimeUnit.SECONDS);
  }

  private static HttpClientRequest addDefaultHeaders(HttpClientRequest request, String tenantId) {
    if (isNotBlank(tenantId)) {
      request.putHeader(XOkapiHeaders.TENANT, tenantId);
      request.putHeader(XOkapiHeaders.TOKEN, "TEST_TOKEN");
      request.putHeader(XOkapiHeaders.USER_ID, USER_ID);
      request.putHeader(XOkapiHeaders.URL, wm.baseUrl());
    }
    request.putHeader(ACCEPT, APPLICATION_JSON + ", " + TEXT_PLAIN);

    return request;
  }

  public record TestResponse(int status, Buffer body) {

    public JsonObject jsonBody() {
      return body.toJsonObject();
    }

    public <T> T bodyAsClass(Class<T> targetClass) {
      return jsonBody().mapTo(targetClass);
    }
  }

  /**
   * Resolves the {@link SharedRestVerticle} for {@link #MODULE_ID} in a {@code beforeAll}
   * callback, where (unlike a plain {@code @BeforeAll} method) JUnit provides the
   * {@link ExtensionContext} needed to look it up in the JVM-wide store.
   *
   * <p>Also bridges the {@link KafkaExtension}-managed broker (published under its own
   * {@code KAFKA_HOST}/{@code KAFKA_PORT} property names) to the {@code kafka-host}/
   * {@code kafka-port} properties this module reads, and starts S3 — both need to be ready
   * before the shared verticle deploys, which is why this runs here rather than in the
   * {@code @BeforeAll} method: {@code @RegisterExtension} callbacks are guaranteed to run
   * before it, in field declaration order, so {@link #POSTGRES} and {@link #KAFKA} are already
   * up by the time this executes.
   *
   * <p>Also constructs {@link #KAFKA_CONSUMER} exactly once: it's backed by its own background
   * polling thread (not a {@link Vertx} instance), so - unlike the shared verticle - it doesn't
   * need to be anchored to anything JVM-long-lived here; it just shouldn't be constructed more
   * than once.
   */
  private static final class SharedVerticleExtension implements BeforeAllCallback {

    private SharedRestVerticle shared;
    private boolean kafkaConsumerStarted;

    @Override
    public void beforeAll(ExtensionContext context) {
      System.setProperty("KAFKA_DOMAIN_TOPIC_NUM_PARTITIONS", "1");
      System.setProperty("kafka-host", KAFKA.getSupport().getHost());
      System.setProperty("kafka-port", String.valueOf(KAFKA.getSupport().getPort()));
      S3Utility.startS3();

      shared = SharedRestVerticleSupport.getOrCreate(context, MODULE_ID);

      if (!kafkaConsumerStarted) {
        KAFKA_CONSUMER = new FakeKafkaConsumer(KAFKA.getBootstrapServers(), ALL_TENANTS);
        kafkaConsumerStarted = true;
      }
    }
  }
}
