package org.folio.rest;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.utility.KafkaUtility;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Check the shaded fat uber jar and Dockerfile:
 *
 * <p>Test /admin/health.
 *
 * <p>Test that logging works.
 *
 * <p>Test installation and migration with smoke test.
 *
 * <p>How to run this test class only:
 *
 * <p>mvn verify -B -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=InstallUpgradeIT 2>&1 | tee /tmp/out
 */
public class InstallUpgradeIT {
  /** set true for debugging. */
  public static final boolean IS_LOG_ENABLED = false;

  public static final Network NETWORK = Network.newNetwork();

  @ClassRule(order = 0)
  public static final KafkaContainer KAFKA =
    new KafkaContainer(KafkaUtility.getImageName())
      .withNetwork(NETWORK)
      .withNetworkAliases("mykafka");

  @ClassRule(order = 1)
  public static final PostgreSQLContainer<?> POSTGRES =
    new PostgreSQLContainer<>("postgres:16-alpine")
      .withClasspathResourceMapping("lotus-23.0.0.sql", "/lotus-23.0.0.sql", BindMode.READ_ONLY)
      .withNetwork(NETWORK)
      .withNetworkAliases("mypostgres")
      .withExposedPorts(5432)
      .withUsername("username")
      .withPassword("password")
      .withDatabaseName("postgres");

  @ClassRule(order = 2)
  public static final WireMockRule OKAPI_MOCK =
    new WireMockRule(WireMockConfiguration.wireMockConfig()
      .notifier(new ConsoleNotifier(IS_LOG_ENABLED))
      .dynamicPort());

  @ClassRule(order = 3)
  public static final GenericContainer<?> MOD_MIS =
    new GenericContainer<>(
      new ImageFromDockerfile("mod-inventory-storage").withFileFromPath(".", Path.of(".")))
      .withNetwork(NETWORK)
      .withExposedPorts(8081)
      .withAccessToHost(true)
      .withEnv("DB_HOST", "mypostgres")
      .withEnv("DB_PORT", "5432")
      .withEnv("DB_USERNAME", "username")
      .withEnv("DB_PASSWORD", "password")
      .withEnv("DB_DATABASE", "postgres")
      .withEnv("KAFKA_HOST", "mykafka")
      .withEnv("KAFKA_PORT", "9092");

  private static final Logger LOG = LoggerFactory.getLogger(InstallUpgradeIT.class);
  private static final String USER_TENANTS_PATH = "/user-tenants?limit=1";
  private static final String USER_TENANTS_FIELD = "userTenants";

  @BeforeClass
  public static void beforeClass() {
    Testcontainers.exposeHostPorts(OKAPI_MOCK.port());
    RestAssured.reset();
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    RestAssured.baseURI = "http://" + MOD_MIS.getHost() + ":" + MOD_MIS.getFirstMappedPort();
    if (IS_LOG_ENABLED) {
      KAFKA.followOutput(new Slf4jLogConsumer(LOG).withSeparateOutputStreams().withPrefix("Kafka"));
      POSTGRES.followOutput(new Slf4jLogConsumer(LOG).withSeparateOutputStreams().withPrefix("Postgres"));
      MOD_MIS.followOutput(new Slf4jLogConsumer(LOG).withSeparateOutputStreams().withPrefix("mis"));
    }

    WireMock.stubFor(WireMock.get(USER_TENANTS_PATH)
      .willReturn(WireMock.ok().withBody(new JsonObject().put(USER_TENANTS_FIELD, JsonArray.of()).encode())));
  }

  static void postgresExec(String... command) {
    Awaitility.await().atMost(Duration.ofSeconds(1)).ignoreExceptions().until(() -> {
      POSTGRES.execInContainer("true");  // sometimes throws IOException "Broken pipe"
      return true;
    });
    try {
      ExecResult execResult = POSTGRES.execInContainer(command);
      assertThat(execResult.getStdout() + "\n" + execResult.getStderr(), execResult.getExitCode(), is(0));
    } catch (InterruptedException | IOException | UnsupportedOperationException e) {
      throw new RuntimeException(e);
    }
  }

  @Before
  public void beforeEach() {
    RestAssured.requestSpecification = null;
  }

  @Test
  public void health() {
    // request without X-Okapi-Tenant
    when()
      .get("/admin/health")
      .then()
      .statusCode(200)
      .body(is("\"OK\""));
  }

  @Test
  public void installAndUpgrade() {
    setTenant("latest");

    JsonObject body = new JsonObject()
      .put("module_to", "999999.0.0")
      .put("parameters", new JsonArray()
        .add(new JsonObject().put("key", "loadReference").put("value", "true"))
        .add(new JsonObject().put("key", "loadSample").put("value", "true")));

    postTenant(body);

    // migrate from 0.0.0 to current version, installation and migration should be idempotent
    body.put("module_from", "0.0.0");
    postTenant(body);

    smokeTest();
  }

  /**
   * Test logging. It broke several times caused by dependency order in pom.xml or by configuration:
   * <a href="https://issues.folio.org/browse/EDGPATRON-90">https://issues.folio.org/browse/EDGPATRON-90</a>
   * <a href="https://issues.folio.org/browse/CIRCSTORE-263">https://issues.folio.org/browse/CIRCSTORE-263</a>
   */
  @Test
  public void canLog() {
    setTenant("logtenant");

    given()
      .header(XOkapiHeaders.REQUEST_ID, "987654321")
      .header(XOkapiHeaders.USER_ID, "itsme")
      .when()
      .get("/location-units/libraries")
      .then()
      .statusCode(400);  // tenant hasn't been created

    assertThat(MOD_MIS.getLogs(), containsString("[987654321] [logtenant] [itsme] [mod_inventory_storage]"));
  }

  private void setTenant(String tenant) {
    RestAssured.requestSpecification = new RequestSpecBuilder()
      .addHeader(XOkapiHeaders.URL_TO, "http://localhost:8081")
      .addHeader(XOkapiHeaders.URL, String.format("http://host.testcontainers.internal:%d", OKAPI_MOCK.port()))
      .addHeader(XOkapiHeaders.TENANT, tenant)
      .addHeader(XOkapiHeaders.USER_ID, "67e1ce93-e358-46ea-aed8-96e2fa73520f")
      .setContentType(ContentType.JSON)
      .build();
  }

  private void postTenant(JsonObject body) {
    String location =
      given()
        .body(body.encodePrettily())
        .when()
        .post("/_/tenant")
        .then()
        .statusCode(201)
        .extract()
        .header("Location");

    when()
      .get(location + "?wait=60000")
      .then()
      .statusCode(200)  // getting job record succeeds
      .body("complete", is(true))  // job is complete
      .body("error", is(nullValue()));  // job has succeeded without error
  }

  private void smokeTest() {
    when()
      .get("/instance-storage/instances?limit=1000")
      .then()
      .statusCode(200)
      .body("instances.size()", is(36));

    given()
      .body("{'instances':{'startNumber':9}, 'holdings':{'startNumber':7}, 'items':{'startNumber':5}}"
          .replace('\'', '"'))
      .when()
      .put("/hrid-settings-storage/hrid-settings")
      .then()
      .statusCode(204);

    when()
      .get("/hrid-settings-storage/hrid-settings")
      .then()
      .statusCode(200)
      .body("holdings.currentNumber", is(6));
  }
}
