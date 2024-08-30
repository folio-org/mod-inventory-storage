package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToIgnoreCase;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.time.Duration.ofMinutes;
import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.postgres.testing.PostgresTesterContainer.getImageName;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.folio.utility.RestUtility.USER_TENANTS_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.folio.HttpStatus;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.RestVerticle;
import org.folio.rest.api.TestBase;
import org.folio.rest.support.extension.EnableTenant;
import org.folio.rest.support.extension.Tenants;
import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.folio.rest.tools.utils.Envs;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.utility.KafkaUtility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@EnableTenant
@Testcontainers(parallel = true)
@ExtendWith(VertxExtension.class)
public class BaseIntegrationTest {

  public static final String USER_ID = UUID.randomUUID().toString();
  @RegisterExtension
  protected static WireMockExtension wm = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort()
      .notifier(new ConsoleNotifier(true)))
    .build();
  static final FakeKafkaConsumer KAFKA_CONSUMER = new FakeKafkaConsumer();

  @Container
  private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER = new PostgreSQLContainer<>(getImageName())
    .withDatabaseName("okapi_modules")
    .withUsername("admin_user")
    .withPassword("admin_password");

  @Container
  private static final KafkaContainer KAFKA_CONTAINER = new KafkaContainer(KafkaUtility.getImageName());
  private static int port;

  @BeforeEach
  public void removeAllEvents() {
    KAFKA_CONSUMER.discardAllMessages();
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

  protected static Handler<AsyncResult<TestResponse>> verifyStatus(VertxTestContext ctx, HttpStatus expectedStatus) {
    return ctx.succeeding(response -> ctx.verify(() -> assertEquals(expectedStatus.toInt(), response.status())));
  }

  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext ctx, @Tenants List<String> tenants) throws Throwable {
    port = NetworkUtils.nextFreePort();
    System.setProperty("KAFKA_DOMAIN_TOPIC_NUM_PARTITIONS", "1");
    System.setProperty("kafka-port", String.valueOf(KAFKA_CONTAINER.getFirstMappedPort()));
    System.setProperty("kafka-host", KAFKA_CONTAINER.getHost());
    KAFKA_CONTAINER.start();

    Envs.setEnv(POSTGRESQL_CONTAINER.getHost(),
      POSTGRESQL_CONTAINER.getFirstMappedPort(),
      POSTGRESQL_CONTAINER.getUsername(),
      POSTGRESQL_CONTAINER.getPassword(),
      POSTGRESQL_CONTAINER.getDatabaseName());
    DeploymentOptions options = new DeploymentOptions();
    options.setConfig(new JsonObject().put("http.port", port));
    HttpClient client = vertx.createHttpClient();

    vertx.deployVerticle(RestVerticle.class, options)
      .compose(s -> {
        var future = Future.succeededFuture();
        for (String tenant : tenants.isEmpty() ? List.of(TENANT_ID) : tenants) {
          future = future.eventually(() -> enableTenant(tenant, ctx, client));
        }
        return future.onComplete(event -> ctx.completeNow());
      });

    assertTrue(ctx.awaitCompletion(65, TimeUnit.SECONDS));
    if (ctx.failed()) {
      throw ctx.causeOfFailure();
    }

    KAFKA_CONSUMER.discardAllMessages();
    KAFKA_CONSUMER.consume(vertx);
    await().atMost(ofMinutes(1)).until(KAFKA_CONTAINER::isRunning);
    mockUserTenantsForNonConsortiumMember();
  }

  public static JsonObject pojo2JsonObject(Object entity) {
    return TestBase.pojo2JsonObject(entity);
  }

  private static void mockUserTenantsForNonConsortiumMember() {
    JsonObject emptyUserTenantsCollection = new JsonObject()
      .put("userTenants", JsonArray.of());
    wm.stubFor(WireMock.get(USER_TENANTS_PATH)
      .withHeader(XOkapiHeaders.TENANT, equalToIgnoreCase(TENANT_ID))
      .willReturn(WireMock.ok().withBody(emptyUserTenantsCollection.encodePrettily())));
  }

  private static Future<TestResponse> enableTenant(String tenant, VertxTestContext ctx, HttpClient client) {
    return doPost(client, "/_/tenant", tenant, BaseIntegrationTest.getJob(false))
      .map(buffer -> buffer.jsonBody().getString("id"))
      .compose(id -> doGet(client, "/_/tenant/" + id + "?wait=60000", tenant))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertEquals(HttpStatus.HTTP_OK.toInt(), response.status());
        assertFalse(response.body().toJsonObject().containsKey("error"));
      })));
  }

  private static JsonObject getJob(String moduleFrom, String moduleTo, boolean loadReference) {
    JsonArray ar = new JsonArray();
    ar.add(new JsonObject().put("key", "loadReference").put("value", Boolean.toString(loadReference)));
    ar.add(new JsonObject().put("key", "loadSample").put("value", "false"));

    JsonObject jo = new JsonObject();
    jo.put("parameters", ar);
    if (moduleFrom != null) {
      jo.put("module_from", moduleFrom);
    }
    jo.put("module_to", moduleTo);
    return jo;
  }

  private static JsonObject getJob(boolean loadSample) {
    return BaseIntegrationTest.getJob(null, "mod-inventory-storage-1.0.0", loadSample);
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
}
