package org.folio.rest.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToIgnoreCase;
import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.awaitility.Awaitility.waitAtMost;
import static org.folio.kafka.services.KafkaEnvironmentProperties.environment;
import static org.folio.kafka.services.KafkaEnvironmentProperties.host;
import static org.folio.kafka.services.KafkaEnvironmentProperties.port;
import static org.folio.rest.impl.ServicePointsIT.createHoldShelfExpiryPeriod;
import static org.folio.rest.support.http.InterfaceUrls.servicePointsUrl;
import static org.folio.rest.tools.utils.ModuleName.getModuleName;
import static org.folio.rest.tools.utils.ModuleName.getModuleVersion;
import static org.folio.services.domainevent.ServicePointEventType.SERVICE_POINT_CREATED;
import static org.folio.services.domainevent.ServicePointEventType.SERVICE_POINT_DELETED;
import static org.folio.services.domainevent.ServicePointEventType.SERVICE_POINT_UPDATED;
import static org.folio.utility.LocationUtility.createServicePoint;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpResponseHead;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.kafka.admin.KafkaAdminClient;
import io.vertx.kafka.client.common.TopicPartition;
import io.vertx.kafka.client.consumer.OffsetAndMetadata;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.ServicePoint;
import org.folio.utility.ModuleUtility;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.org.webcompere.systemstubs.rules.EnvironmentVariablesRule;

@RunWith(VertxUnitRunner.class)
public class ServicePointSynchronizationVerticleTest extends TestBaseWithInventoryUtil {

  private static final String CENTRAL_TENANT_ID = "consortium";
  private static final String COLLEGE_TENANT_ID = "college";
  private static final String SERVICE_POINT_TOPIC = format(
    "%s.%s.inventory.service-point", environment(), CENTRAL_TENANT_ID);
  private static final String KAFKA_SERVER_URL = format("%s:%s", host(), port());
  private static final String SERVICE_POINT_ID = UUID.randomUUID().toString();
  private static final String CONSORTIUM_ID = UUID.randomUUID().toString();
  private static final String CONSORTIUM_TENANTS_PATH = "/consortia/%s/tenants".formatted(
    CONSORTIUM_ID);
  private static final String ECS_TLR_FEATURE_ENABLED = "ECS_TLR_FEATURE_ENABLED";
  private static KafkaProducer<String, JsonObject> producer;
  private static KafkaAdminClient adminClient;
  @Rule
  public EnvironmentVariablesRule environmentVariablesRule =
    new EnvironmentVariablesRule(ECS_TLR_FEATURE_ENABLED, "true");

  @BeforeClass
  public static void setUpClass() throws Exception {
    ModuleUtility.prepareTenant(CENTRAL_TENANT_ID, false);
    ModuleUtility.prepareTenant(COLLEGE_TENANT_ID, false);

    producer = createProducer();
    adminClient = createAdminClient();
  }

  @Before
  public void setUp() {
    clearData(CENTRAL_TENANT_ID);
    clearData(COLLEGE_TENANT_ID);
    mockUserTenantsForConsortiumMember();
    mockConsortiumTenants();
    mockUserTenantsForNonConsortiumMember();
    assertTrue(Boolean.parseBoolean(System.getenv().getOrDefault(ECS_TLR_FEATURE_ENABLED,
      "false")));
  }

  @AfterClass
  public static void tearDownClass() throws ExecutionException, InterruptedException,
    TimeoutException {

    ModuleUtility.removeTenant(CENTRAL_TENANT_ID);
    ModuleUtility.removeTenant(COLLEGE_TENANT_ID);
    waitFor(producer.close().compose(v -> adminClient.close())
    );
  }

  @Test
  public void shouldPropagateCreationOfServicePointOnLendingTenant(TestContext context) {
    var servicePointFromCentralTenant = createServicePointAgainstTenant(CENTRAL_TENANT_ID, false);

    int initialOffset = getOffsetForServicePointCreateEvents();
    publishServicePointCreateEvent(servicePointFromCentralTenant);
    waitUntilValueIsIncreased(initialOffset,
      ServicePointSynchronizationVerticleTest::getOffsetForServicePointCreateEvents);
    getServicePointById(COLLEGE_TENANT_ID)
      .onComplete(context.asyncAssertSuccess(collegeServicePoint ->
        context.assertEquals(servicePointFromCentralTenant.getId(), collegeServicePoint.getId())));
  }

  @Test
  public void shouldPropagateUpdateOfServicePointOnLendingTenant(TestContext context) {
    var servicePointFromCentralTenant = createServicePointAgainstTenant(CENTRAL_TENANT_ID,
      true);
    var servicePointFromDataTenant = createServicePointAgainstTenant(COLLEGE_TENANT_ID,
      false);

    int initialOffset = getOffsetForServicePointUpdateEvents();
    publishServicePointUpdateEvent(servicePointFromDataTenant, servicePointFromCentralTenant);
    waitUntilValueIsIncreased(initialOffset,
      ServicePointSynchronizationVerticleTest::getOffsetForServicePointUpdateEvents);
    getServicePointById(COLLEGE_TENANT_ID)
      .onComplete(context.asyncAssertSuccess(collegeServicePoint ->
        context.assertEquals(servicePointFromCentralTenant.getDiscoveryDisplayName(),
          collegeServicePoint.getDiscoveryDisplayName())));
  }

  @Test
  public void shouldPropagateDeleteOfServicePointOnLendingTenant(TestContext context) {
    var servicePointFromCentralTenant = createServicePointAgainstTenant(CENTRAL_TENANT_ID, false);
    var servicePointFromDataTenant = createServicePointAgainstTenant(COLLEGE_TENANT_ID, false);

    getServicePointById(COLLEGE_TENANT_ID)
      .onComplete(context.asyncAssertSuccess(servicePoint ->
        context.assertEquals(servicePointFromCentralTenant.getId(),
          servicePointFromDataTenant.getId())));

    int initialOffset = getOffsetForServicePointDeleteEvents();
    publishServicePointDeleteEvent(servicePointFromDataTenant);
    waitUntilValueIsIncreased(initialOffset,
      ServicePointSynchronizationVerticleTest::getOffsetForServicePointDeleteEvents);
    getStatusCodeOfServicePointById(COLLEGE_TENANT_ID)
      .onComplete(context.asyncAssertSuccess(statusCode ->
        context.assertEquals(HTTP_NOT_FOUND, statusCode)));
  }

  @Test
  public void shouldHandleUpdateEventForNonExistingServicePoint(TestContext context) {
    ServicePoint nonExistingServicePoint = new ServicePoint().withId(UUID.randomUUID().toString());
    publishServicePointUpdateEvent(nonExistingServicePoint, nonExistingServicePoint);

    getStatusCodeOfServicePointById(COLLEGE_TENANT_ID)
      .onComplete(context.asyncAssertSuccess(statusCode ->
        context.assertEquals(HTTP_NOT_FOUND, statusCode)));
  }

  @Test
  public void shouldHandleDeleteEventForNonExistingServicePoint(TestContext context) {
    ServicePoint nonExistingServicePoint = new ServicePoint().withId(UUID.randomUUID().toString());
    publishServicePointDeleteEvent(nonExistingServicePoint);

    getStatusCodeOfServicePointById(COLLEGE_TENANT_ID)
      .onComplete(context.asyncAssertSuccess(statusCode ->
        context.assertEquals(HTTP_NOT_FOUND, statusCode)));
  }

  @SneakyThrows
  public static <T> T waitFor(Future<T> future, int timeoutSeconds) {
    return future.toCompletionStage()
      .toCompletableFuture()
      .get(timeoutSeconds, TimeUnit.SECONDS);
  }

  public static <T> T waitFor(Future<T> future) {
    return waitFor(future, 10);
  }

  private Future<ServicePoint> getServicePointById(String tenantId) {
    Promise<HttpResponse<Buffer>> promise = Promise.promise();
    getClient().get(servicePointsUrl("/" + SERVICE_POINT_ID), tenantId, promise::complete);
    return promise.future().map(resp -> {
      MatcherAssert.assertThat(resp.statusCode(), CoreMatchers.is(HTTP_OK));
      return resp.bodyAsJson(ServicePoint.class);
    });
  }

  private Future<Integer> getStatusCodeOfServicePointById(String tenantId) {
    Promise<HttpResponse<Buffer>> promise = Promise.promise();
    getClient().get(servicePointsUrl("/" + SERVICE_POINT_ID), tenantId, promise::complete);
    return promise.future().map(HttpResponseHead::statusCode);
  }

  @SneakyThrows(Exception.class)
  private static ServicePoint createServicePointAgainstTenant(String tenantId, boolean updated) {
    String discoveryDisplayName = "Circulation Desk -- Basement" + (updated ? "(updated)" : "");
    return createServicePoint(UUID.fromString(SERVICE_POINT_ID), "Circ Desk 2522", "cd2522",
      discoveryDisplayName, null, 20,
      true, createHoldShelfExpiryPeriod(), tenantId)
      .getJson().mapTo(ServicePoint.class);
  }

  private static int waitUntilValueIsIncreased(int previousValue, Callable<Integer> valueSupplier) {
    return waitAtMost(60, SECONDS)
      .until(valueSupplier, newValue -> newValue > previousValue);
  }

  private static JsonObject buildCreateEvent(ServicePoint newVersion) {
    return new JsonObject()
      .put("tenant", CENTRAL_TENANT_ID)
      .put("type", "CREATE")
      .put("new", newVersion);
  }

  private static JsonObject buildUpdateEvent(ServicePoint oldVersion, ServicePoint newVersion) {
    return new JsonObject()
      .put("tenant", CENTRAL_TENANT_ID)
      .put("type", "UPDATE")
      .put("old", oldVersion)
      .put("new", newVersion);
  }

  private static JsonObject buildDeleteEvent(ServicePoint object) {
    return new JsonObject()
      .put("tenant", CENTRAL_TENANT_ID)
      .put("type", "DELETE")
      .put("old", object);
  }

  private void publishServicePointCreateEvent(
    ServicePoint newServicePoint) {

    publishEvent(buildCreateEvent(newServicePoint));
  }

  private void publishServicePointUpdateEvent(ServicePoint oldServicePoint,    ServicePoint newServicePoint) {

    publishEvent(buildUpdateEvent(oldServicePoint, newServicePoint));
  }

  private void publishServicePointDeleteEvent(ServicePoint servicePoint) {
    publishEvent(buildDeleteEvent(servicePoint));
  }

  private static Integer getOffsetForServicePointCreateEvents() {
    return getOffset(buildConsumerGroupId(SERVICE_POINT_CREATED.name()));
  }

  private static Integer getOffsetForServicePointUpdateEvents() {
    return getOffset(buildConsumerGroupId(SERVICE_POINT_UPDATED.name()));
  }

  private static Integer getOffsetForServicePointDeleteEvents() {
    return getOffset(buildConsumerGroupId(SERVICE_POINT_DELETED.name()));
  }

  private void publishEvent(JsonObject eventPayload) {
    var kafkaRecord = KafkaProducerRecord.create(SERVICE_POINT_TOPIC, SERVICE_POINT_ID,
      eventPayload);
    kafkaRecord.addHeader("X-Okapi-Tenant".toLowerCase(Locale.ROOT), CENTRAL_TENANT_ID);
    kafkaRecord.addHeader("X-Okapi-Token".toLowerCase(Locale.ROOT),
      "test-token".toLowerCase(Locale.ROOT));
    kafkaRecord.addHeader("X-Okapi-Url", mockServer.baseUrl().toLowerCase(Locale.ROOT));
    waitFor(producer.write(kafkaRecord));
  }

  private static KafkaProducer<String, JsonObject> createProducer() {
    Properties config = new Properties();
    config.put(BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER_URL);
    config.put(ACKS_CONFIG, "1");

    return KafkaProducer.create(getVertx(), config, String.class, JsonObject.class);
  }

  private static KafkaAdminClient createAdminClient() {
    Map<String, String> config = Map.of(BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER_URL);
    return KafkaAdminClient.create(getVertx(), config);
  }

  private static String buildConsumerGroupId(String eventType) {
    return format("%s.%s-%s", eventType, getModuleName().replace("_", "-"), getModuleVersion());
  }

  private static int getOffset(String consumerGroupId) {
    return waitFor(
      adminClient.listConsumerGroupOffsets(consumerGroupId)
        .map(partitions -> Optional.ofNullable(partitions.get(new TopicPartition(SERVICE_POINT_TOPIC, 0)))
          .map(OffsetAndMetadata::getOffset)
          .map(Long::intValue)
          .orElse(0)) // if topic does not exist yet
    );
  }

  private void mockUserTenantsForConsortiumMember() {
    JsonObject userTenantsCollection = new JsonObject()
      .put("userTenants", new JsonArray()
        .add(new JsonObject()
          .put("centralTenantId", CENTRAL_TENANT_ID)
          .put("consortiumId", CONSORTIUM_ID)));
    WireMock.stubFor(WireMock.get(USER_TENANTS_PATH)
      .withHeader("X-Okapi-Tenant", equalToIgnoreCase(CENTRAL_TENANT_ID))
      .willReturn(WireMock.ok().withBody(userTenantsCollection.encodePrettily())));
  }

  public static void mockConsortiumTenants() {
    JsonObject tenantsCollection = new JsonObject()
      .put("tenants", new JsonArray()
        .add(new JsonObject()
          .put("id", CENTRAL_TENANT_ID)
          .put("isCentral", true))
        .add(new JsonObject()
          .put("id", COLLEGE_TENANT_ID)
          .put("isCentral", false)));
    WireMock.stubFor(WireMock.get(CONSORTIUM_TENANTS_PATH)
      .willReturn(WireMock.ok().withBody(tenantsCollection.encodePrettily())));
  }

  public static void mockUserTenantsForNonConsortiumMember() {
    JsonObject emptyUserTenantsCollection = new JsonObject()
      .put("userTenants", JsonArray.of());
    WireMock.stubFor(WireMock.get(USER_TENANTS_PATH)
      .withHeader(XOkapiHeaders.TENANT, equalToIgnoreCase(COLLEGE_TENANT_ID))
      .willReturn(WireMock.ok().withBody(emptyUserTenantsCollection.encodePrettily())));
  }
}
