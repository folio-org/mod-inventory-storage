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
import static org.folio.utility.RestUtility.CONSORTIUM_CENTRAL_TENANT;
import static org.folio.utility.RestUtility.CONSORTIUM_ID;
import static org.folio.utility.RestUtility.CONSORTIUM_MEMBER_TENANT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpResponseHead;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import lombok.SneakyThrows;
import org.folio.rest.jaxrs.model.ServicePoint;
import org.folio.utility.ModuleUtility;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith({VertxExtension.class, SystemStubsExtension.class})
class ServicePointSynchronizationVerticleTest extends TestBaseWithInventoryUtil {

  private static final String SERVICE_POINT_TOPIC = format(
    "%s.%s.inventory.service-point", environment(), CONSORTIUM_CENTRAL_TENANT);
  private static final String KAFKA_SERVER_URL = format("%s:%s", host(), port());
  private static final String ECS_TLR_FEATURE_ENABLED = "ECS_TLR_FEATURE_ENABLED";

  private static KafkaProducer<String, JsonObject> producer;
  private static KafkaAdminClient adminClient;

  @SystemStub
  private EnvironmentVariables env = new EnvironmentVariables(ECS_TLR_FEATURE_ENABLED, "true");
  // Generated fresh per test in setUp() rather than once for the class: reusing a fixed id let a
  // still-draining async propagation from a previous test race the next test's own create with a
  // duplicate-key error.
  private String servicePointId;

  @BeforeAll
  static void setUpClass() throws Exception {
    ModuleUtility.prepareTenant(CONSORTIUM_CENTRAL_TENANT, false);
    ModuleUtility.prepareTenant(CONSORTIUM_MEMBER_TENANT, false);

    producer = createProducer();
    adminClient = createAdminClient();
  }

  @BeforeEach
  void setUp() {
    servicePointId = UUID.randomUUID().toString();
    clearData(CONSORTIUM_CENTRAL_TENANT);
    clearData(CONSORTIUM_MEMBER_TENANT);
    mockUserTenantsForConsortiumMember();
    mockConsortiumTenants();
    mockUserTenantsForNonConsortiumMember();
    assertTrue(Boolean.parseBoolean(System.getenv().getOrDefault(ECS_TLR_FEATURE_ENABLED, "false")));
  }

  @SneakyThrows
  @AfterAll
  static void tearDownClass() {
    ModuleUtility.removeTenant(CONSORTIUM_CENTRAL_TENANT);
    ModuleUtility.removeTenant(CONSORTIUM_MEMBER_TENANT);
    waitFor(producer.close().compose(v -> adminClient.close()));
  }

  @Test
  void shouldPropagateCreationOfServicePointOnLendingTenant(VertxTestContext context) {
    var servicePointFromCentralTenant = createServicePointAgainstTenant(CONSORTIUM_CENTRAL_TENANT, false);

    int initialOffset = getOffsetForServicePointCreateEvents();
    publishServicePointCreateEvent(servicePointFromCentralTenant);
    waitUntilValueIsIncreased(initialOffset,
      ServicePointSynchronizationVerticleTest::getOffsetForServicePointCreateEvents);

    var collegeServicePoint = waitForServicePointOnTenant(CONSORTIUM_MEMBER_TENANT);
    assertEquals(servicePointFromCentralTenant.getId(), collegeServicePoint.getId());
    context.completeNow();
  }

  @Test
  void shouldPropagateUpdateOfServicePointOnLendingTenant(VertxTestContext context) {
    var servicePointFromCentralTenant = createServicePointAgainstTenant(CONSORTIUM_CENTRAL_TENANT, false);

    // Creating the central service point already triggers the verticle's own create-propagation
    // to the member tenant (ECS_TLR_FEATURE_ENABLED=true). Wait for that shadow copy instead of
    // also creating one directly against the member tenant: the two would race on the same id and
    // whichever loses gets a duplicate-key error from postServicePoints.
    int createOffset = getOffsetForServicePointCreateEvents();
    publishServicePointCreateEvent(servicePointFromCentralTenant);
    waitUntilValueIsIncreased(createOffset,
      ServicePointSynchronizationVerticleTest::getOffsetForServicePointCreateEvents);
    var servicePointFromDataTenant = waitForServicePointOnTenant(CONSORTIUM_MEMBER_TENANT);

    var updatedServicePointFromCentralTenant = servicePointFromCentralTenant
      .withDiscoveryDisplayName("Circulation Desk -- Basement(updated)");

    int updateOffset = getOffsetForServicePointUpdateEvents();
    publishServicePointUpdateEvent(servicePointFromDataTenant, updatedServicePointFromCentralTenant);
    waitUntilValueIsIncreased(updateOffset,
      ServicePointSynchronizationVerticleTest::getOffsetForServicePointUpdateEvents);

    var updatedCollegeServicePoint = waitForServicePointOnTenant(CONSORTIUM_MEMBER_TENANT,
      servicePoint -> updatedServicePointFromCentralTenant.getDiscoveryDisplayName()
        .equals(servicePoint.getDiscoveryDisplayName()));
    assertEquals(updatedServicePointFromCentralTenant.getDiscoveryDisplayName(),
      updatedCollegeServicePoint.getDiscoveryDisplayName());
    context.completeNow();
  }

  @Test
  void shouldPropagateDeleteOfServicePointOnLendingTenant(VertxTestContext context) {
    var servicePointFromCentralTenant = createServicePointAgainstTenant(CONSORTIUM_CENTRAL_TENANT, false);

    // See shouldPropagateUpdateOfServicePointOnLendingTenant: wait for the verticle's own
    // create-propagation to the member tenant instead of creating a second copy directly, which
    // would race the propagation on the same id.
    int createOffset = getOffsetForServicePointCreateEvents();
    publishServicePointCreateEvent(servicePointFromCentralTenant);
    waitUntilValueIsIncreased(createOffset,
      ServicePointSynchronizationVerticleTest::getOffsetForServicePointCreateEvents);
    // waitFor()/waitForServicePointOnTenant() block the calling thread, so they must run on the
    // test thread, not inside a Vert.x callback (e.g. compose/onComplete) - doing so would block
    // the event loop they need to complete on, deadlocking until the blocking call's own timeout.
    var servicePointFromDataTenant = waitForServicePointOnTenant(CONSORTIUM_MEMBER_TENANT);

    int initialOffset = getOffsetForServicePointDeleteEvents();
    publishServicePointDeleteEvent(servicePointFromDataTenant);
    waitUntilValueIsIncreased(initialOffset,
      ServicePointSynchronizationVerticleTest::getOffsetForServicePointDeleteEvents);

    waitForServicePointAbsentOnTenant(CONSORTIUM_MEMBER_TENANT);
    context.completeNow();
  }

  @Test
  void shouldHandleUpdateEventForNonExistingServicePoint(VertxTestContext context) {
    ServicePoint nonExistingServicePoint = new ServicePoint().withId(UUID.randomUUID().toString());
    publishServicePointUpdateEvent(nonExistingServicePoint, nonExistingServicePoint);

    getStatusCodeOfServicePointById(CONSORTIUM_MEMBER_TENANT)
      .onComplete(context.succeeding(statusCode -> {
        context.verify(() -> assertEquals(HTTP_NOT_FOUND, statusCode));
        context.completeNow();
      }));
  }

  @Test
  void shouldHandleDeleteEventForNonExistingServicePoint(VertxTestContext context) {
    ServicePoint nonExistingServicePoint = new ServicePoint().withId(UUID.randomUUID().toString());
    publishServicePointDeleteEvent(nonExistingServicePoint);

    getStatusCodeOfServicePointById(CONSORTIUM_MEMBER_TENANT)
      .onComplete(context.succeeding(statusCode -> {
        context.verify(() -> assertEquals(HTTP_NOT_FOUND, statusCode));
        context.completeNow();
      }));
  }

  @SneakyThrows
  private static <T> T waitFor(Future<T> future, int timeoutSeconds) {
    return future.toCompletionStage()
      .toCompletableFuture()
      .get(timeoutSeconds, TimeUnit.SECONDS);
  }

  private static <T> T waitFor(Future<T> future) {
    return waitFor(future, 10);
  }

  private Future<ServicePoint> getServicePointById(String tenantId) {
    Promise<HttpResponse<Buffer>> promise = Promise.promise();
    getClient().get(servicePointsUrl("/" + servicePointId), tenantId, promise::complete);
    return promise.future().map(resp -> {
      assertThat(resp.statusCode(), CoreMatchers.is(HTTP_OK));
      return resp.bodyAsJson(ServicePoint.class);
    });
  }

  private Future<Integer> getStatusCodeOfServicePointById(String tenantId) {
    Promise<HttpResponse<Buffer>> promise = Promise.promise();
    getClient().get(servicePointsUrl("/" + servicePointId), tenantId, promise::complete);
    return promise.future().map(HttpResponseHead::statusCode);
  }

  @SneakyThrows(Exception.class)
  private ServicePoint createServicePointAgainstTenant(String tenantId, boolean updated) {
    String discoveryDisplayName = "Circulation Desk -- Basement" + (updated ? "(updated)" : "");
    return createServicePoint(UUID.fromString(servicePointId), "Circ Desk 2522", "cd2522",
      discoveryDisplayName, null, 20,
      true, createHoldShelfExpiryPeriod(), tenantId)
      .getJson().mapTo(ServicePoint.class);
  }

  private static int waitUntilValueIsIncreased(int previousValue, Callable<Integer> valueSupplier) {
    return waitAtMost(60, SECONDS)
      .until(valueSupplier, newValue -> newValue > previousValue);
  }

  /**
   * Poll for the propagated service point on {@code tenantId} instead of trusting a single GET
   * right after {@link #waitUntilValueIsIncreased}. The Kafka consumer group backing that offset
   * check is shared for the whole test JVM run and also advances for other tenants' unrelated
   * service-point activity, so "offset increased" alone doesn't guarantee this event's downstream
   * HTTP-level propagation has actually finished - only that some message got committed.
   */
  private ServicePoint waitForServicePointOnTenant(String tenantId) {
    return waitForServicePointOnTenant(tenantId, servicePoint -> true);
  }

  @SneakyThrows
  private ServicePoint waitForServicePointOnTenant(String tenantId, Predicate<ServicePoint> ready) {
    return waitAtMost(60, SECONDS).until(() -> {
      int statusCode = waitFor(getStatusCodeOfServicePointById(tenantId));
      return statusCode == HTTP_OK ? waitFor(getServicePointById(tenantId)) : null;
    }, servicePoint -> servicePoint != null && ready.test(servicePoint));
  }

  private void waitForServicePointAbsentOnTenant(String tenantId) {
    waitAtMost(60, SECONDS).until(() -> waitFor(getStatusCodeOfServicePointById(tenantId)),
      statusCode -> statusCode == HTTP_NOT_FOUND);
  }

  private static JsonObject buildCreateEvent(ServicePoint newVersion) {
    return new JsonObject()
      .put("tenant", CONSORTIUM_CENTRAL_TENANT)
      .put("type", "CREATE")
      .put("new", newVersion);
  }

  private static JsonObject buildUpdateEvent(ServicePoint oldVersion, ServicePoint newVersion) {
    return new JsonObject()
      .put("tenant", CONSORTIUM_CENTRAL_TENANT)
      .put("type", "UPDATE")
      .put("old", oldVersion)
      .put("new", newVersion);
  }

  private static JsonObject buildDeleteEvent(ServicePoint object) {
    return new JsonObject()
      .put("tenant", CONSORTIUM_CENTRAL_TENANT)
      .put("type", "DELETE")
      .put("old", object);
  }

  private void publishServicePointCreateEvent(
    ServicePoint newServicePoint) {

    publishEvent(buildCreateEvent(newServicePoint));
  }

  private void publishServicePointUpdateEvent(ServicePoint oldServicePoint, ServicePoint newServicePoint) {

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
    var kafkaRecord = KafkaProducerRecord.create(SERVICE_POINT_TOPIC, servicePointId,
      eventPayload);
    kafkaRecord.addHeader("X-Okapi-Tenant".toLowerCase(Locale.ROOT), CONSORTIUM_CENTRAL_TENANT);
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
          .put("centralTenantId", CONSORTIUM_CENTRAL_TENANT)
          .put("consortiumId", CONSORTIUM_ID)));
    WireMock.stubFor(WireMock.get(USER_TENANTS_PATH)
      .withHeader("X-Okapi-Tenant", equalToIgnoreCase(CONSORTIUM_CENTRAL_TENANT))
      .willReturn(WireMock.ok().withBody(userTenantsCollection.encodePrettily())));
  }
}
