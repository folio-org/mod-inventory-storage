package org.folio.rest;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.nio.file.Path;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.utility.KafkaUtility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Check the shaded fat uber jar and Dockerfile.
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
@Testcontainers
class InstallUpgradeIT {
  /**
   * set true for debugging.
   */
  private static final boolean IS_LOG_ENABLED = true;

  private static final Network NETWORK = Network.newNetwork();

  @Container
  private static final KafkaContainer KAFKA =
    new KafkaContainer(KafkaUtility.getImageName())
      .withNetwork(NETWORK)
      .withNetworkAliases("mykafka")
      .withListener("mykafka:19092")
      .withStartupAttempts(3);

  @Container
  private static final PostgreSQLContainer POSTGRES =
    new PostgreSQLContainer("postgres:16-alpine")
      .withClasspathResourceMapping("lotus-23.0.0.sql", "/lotus-23.0.0.sql", BindMode.READ_ONLY)
      .withNetwork(NETWORK)
      .withNetworkAliases("mypostgres")
      .withExposedPorts(5432)
      .withUsername("username")
      .withPassword("password")
      .withDatabaseName("postgres");

  @Container
  private static LocalStackContainer LOCAL_STACK =
    new LocalStackContainer(DockerImageName.parse("localstack/localstack:s3-latest"))
      .withServices("s3")
      .withNetwork(NETWORK)
      .withNetworkAliases("s3")
      .withExposedPorts(4566);

  @RegisterExtension
  protected static WireMockExtension okapiMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort()
      .notifier(new ConsoleNotifier(IS_LOG_ENABLED)))
    .build();

  @Container
  private static final GenericContainer<?> MOD_MIS =
    new GenericContainer<>(new ImageFromDockerfile("mod-inventory-storage")
      .withFileFromPath(".", Path.of("..").toAbsolutePath()))
      .withNetwork(NETWORK)
      .withExposedPorts(8081)
      .withAccessToHost(true)
      .dependsOn(KAFKA, POSTGRES, LOCAL_STACK)
      .withEnv("DB_HOST", "mypostgres")
      .withEnv("DB_PORT", "5432")
      .withEnv("DB_USERNAME", "username")
      .withEnv("DB_PASSWORD", "password")
      .withEnv("DB_DATABASE", "postgres")
      .withEnv("KAFKA_HOST", "mykafka")
      .withEnv("KAFKA_PORT", "19092")
      .withEnv("S3_MARC_MIGRATION_URL", "http://s3:4566")
      .withEnv("S3_MARC_MIGRATION_REGION", "us-east-1")
      .withEnv("S3_MARC_MIGRATION_BUCKET", "mod-inventory-storage-marc-migration-it")
      .withEnv("S3_MARC_MIGRATION_ACCESS_KEY_ID", "test")
      .withEnv("S3_MARC_MIGRATION_SECRET_ACCESS_KEY", "test")
      .withEnv("S3_REINDEX_URL", "http://s3:4566")
      .withEnv("S3_REINDEX_REGION", "us-east-1")
      .withEnv("S3_REINDEX_BUCKET", "mod-inventory-storage-reindex-it")
      .withEnv("S3_REINDEX_ACCESS_KEY_ID", "test")
      .withEnv("S3_REINDEX_SECRET_ACCESS_KEY", "test");

  private static final Logger LOG = LoggerFactory.getLogger(InstallUpgradeIT.class);
  private static final String USER_TENANTS_PATH = "/user-tenants?limit=1";
  private static final String USER_TENANTS_FIELD = "userTenants";

  @BeforeAll
  static void beforeClass() {
    org.testcontainers.Testcontainers.exposeHostPorts(okapiMock.getPort());
    RestAssured.reset();
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    RestAssured.baseURI = "http://" + MOD_MIS.getHost() + ":" + MOD_MIS.getFirstMappedPort();
    if (IS_LOG_ENABLED) {
      KAFKA.followOutput(new Slf4jLogConsumer(LOG).withSeparateOutputStreams().withPrefix("Kafka"));
      POSTGRES.followOutput(new Slf4jLogConsumer(LOG).withSeparateOutputStreams().withPrefix("Postgres"));
      LOCAL_STACK.followOutput(new Slf4jLogConsumer(LOG).withSeparateOutputStreams().withPrefix("S3"));
      MOD_MIS.followOutput(new Slf4jLogConsumer(LOG).withSeparateOutputStreams().withPrefix("mis"));
    }

    okapiMock.stubFor(WireMock.get(USER_TENANTS_PATH)
      .willReturn(WireMock.ok().withBody(new JsonObject().put(USER_TENANTS_FIELD, JsonArray.of()).encode())));
  }

  @BeforeEach
  void beforeEach() {
    RestAssured.requestSpecification = null;
  }

  @Test
  void health() {
    // request without X-Okapi-Tenant
    when()
      .get("/admin/health")
      .then()
      .statusCode(200)
      .body(is("\"OK\""));
  }

  @Test
  void installAndUpgrade() {
    when()
      .get("/admin/health")
      .then()
      .statusCode(200)
      .body(is("\"OK\""));

    setTenant("latest");

    JsonObject body = new JsonObject()
      .put("module_to", "mod-inventory-storage-999999.0.0")
      .put("parameters", new JsonArray()
        .add(new JsonObject().put("key", "loadReference").put("value", "true"))
        .add(new JsonObject().put("key", "loadSample").put("value", "false")));

    postTenant(body);

    // migrate from 0.0.0 to current version, installation and migration should be idempotent
    body.put("module_from", "mod-inventory-storage-0.0.0");
    postTenant(body);

    smokeTest();
  }

  /**
   * Test logging. It broke several times caused by dependency order in pom.xml or by configuration:
   * <a href="https://issues.folio.org/browse/EDGPATRON-90">https://issues.folio.org/browse/EDGPATRON-90</a>
   * <a href="https://issues.folio.org/browse/CIRCSTORE-263">https://issues.folio.org/browse/CIRCSTORE-263</a>
   */
  @Test
  void canLog() {
    setTenant("logtenant");

    given()
      .header(XOkapiHeaders.REQUEST_ID, "987654321")
      .header(XOkapiHeaders.USER_ID, "itsme")
      .when()
      .get("/inventory-view/instances")
      .then()
      .statusCode(400);  // tenant hasn't been created

    assertThat(MOD_MIS.getLogs(), containsString("[987654321] [logtenant] [itsme] [mod_inventory_storage]"));
  }

  private void setTenant(String tenant) {
    RestAssured.requestSpecification = new RequestSpecBuilder()
      .addHeader(XOkapiHeaders.URL_TO, "http://localhost:8081")
      .addHeader(XOkapiHeaders.URL, okapiMock.baseUrl())
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
      .get(location + "?wait=120000")
      .then()
      .statusCode(200)  // getting job record succeeds
      .body("complete", is(true))  // job is complete
      .body("error", is(nullValue()));  // job has succeeded without error
  }

  private void smokeTest() {
    when()
      .get("/classification-types?limit=1000")
      .then()
      .statusCode(200)
      .body("classificationTypes.size()", is(10));

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
