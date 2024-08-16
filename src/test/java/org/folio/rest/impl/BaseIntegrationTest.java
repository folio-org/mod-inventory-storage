package org.folio.rest.impl;

import static java.lang.String.format;
import static java.time.Duration.ofMinutes;
import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.postgres.testing.PostgresTesterContainer.getImageName;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.folio.HttpStatus;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.RestVerticle;
import org.folio.rest.api.TestBase;
import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.folio.rest.tools.utils.Envs;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(parallel = true)
@ExtendWith(VertxExtension.class)
public class BaseIntegrationTest {

  public static final String USER_ID = UUID.randomUUID().toString();
  static final FakeKafkaConsumer KAFKA_CONSUMER = new FakeKafkaConsumer();

  @Container
  private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER = new PostgreSQLContainer<>(getImageName())
    .withDatabaseName("okapi_modules")
    .withUsername("admin_user")
    .withPassword("admin_password");

  @Container
  private static final KafkaContainer KAFKA_CONTAINER
    = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"));
  private static int port;

  protected static Future<TestResponse> doGet(HttpClient client, String requestUri) {
    return doRequest(client, HttpMethod.GET, requestUri, null);
  }

  protected static Future<TestResponse> doPost(HttpClient client, String requestUri, JsonObject body) {
    return doRequest(client, HttpMethod.POST, requestUri, body);
  }

  protected static Future<TestResponse> doPut(HttpClient client, String requestUri, JsonObject body) {
    return doRequest(client, HttpMethod.PUT, requestUri, body);
  }

  protected static Future<TestResponse> doPatch(HttpClient client, String requestUri, JsonObject body) {
    return doRequest(client, HttpMethod.PATCH, requestUri, body);
  }

  protected static Future<TestResponse> doDelete(HttpClient client, String requestUri) {
    return doRequest(client, HttpMethod.DELETE, requestUri, null);
  }

  protected static Future<TestResponse> doRequest(HttpClient client, HttpMethod method,
                                                  String requestUri, JsonObject body) {
    return client.request(method, port, "localhost", requestUri)
      .compose(req -> {
        var request = addDefaultHeaders(req, TENANT_ID);
        return (body == null ? request.send() : request.send(body.toBuffer()))
          .compose(resp -> resp.body().map(respBody -> new TestResponse(resp.statusCode(), respBody)));
      });
  }

  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext ctx) throws Throwable {
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
      .compose(s -> enableTenant(ctx, client));

    assertTrue(ctx.awaitCompletion(65, TimeUnit.SECONDS));
    if (ctx.failed()) {
      throw ctx.causeOfFailure();
    }


    KAFKA_CONSUMER.discardAllMessages();
    KAFKA_CONSUMER.consume(vertx);
    await().atMost(ofMinutes(1)).until(KAFKA_CONTAINER::isRunning);
  }

  @AfterAll
  static void afterAll() {
    if (KAFKA_CONTAINER.isRunning()) {
      KAFKA_CONTAINER.stop();
    }
    KAFKA_CONSUMER.unsubscribe();
  }

  @BeforeEach
  public void removeAllEvents() {
    KAFKA_CONSUMER.discardAllMessages();
  }

  private static Future<TestResponse> enableTenant(VertxTestContext ctx, HttpClient client) {
    return BaseIntegrationTest.doPost(client, "/_/tenant", BaseIntegrationTest.getJob(false))
      .map(buffer -> buffer.jsonBody().getString("id"))
      .compose(id -> BaseIntegrationTest.doGet(client, "/_/tenant/" + id + "?wait=60000"))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertEquals(HttpStatus.HTTP_OK.toInt(), response.status());
        assertFalse(response.body().toJsonObject().containsKey("error"));
        ctx.completeNow();
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
    }
    String baseUrl;
    try {
      var url = URI.create(request.absoluteURI()).toURL();
      baseUrl = format("%s://%s", url.getProtocol(), url.getAuthority());
    } catch (MalformedURLException e) {
      baseUrl = "http://localhost:" + port;
    }
    request.putHeader(XOkapiHeaders.URL, baseUrl);
    request.putHeader(XOkapiHeaders.URL_TO, baseUrl);
    request.putHeader(ACCEPT, APPLICATION_JSON + ", " + TEXT_PLAIN);

    return request;
  }

  public static JsonObject pojo2JsonObject(Object entity) {
    return TestBase.pojo2JsonObject(entity);
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
