package org.folio.rest.impl;

import static java.lang.String.format;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.folio.kafka.services.KafkaEnvironmentProperties.environment;
import static org.folio.kafka.services.KafkaEnvironmentProperties.host;
import static org.folio.kafka.services.KafkaEnvironmentProperties.port;
import static org.folio.rest.impl.ServicePointsIT.createHoldShelfExpiryPeriod;
import static org.folio.rest.tools.utils.ModuleName.getModuleName;
import static org.folio.rest.tools.utils.ModuleName.getModuleVersion;
import static org.folio.services.domainevent.ServicePointEventType.SERVICE_POINT_CREATED;
import static org.folio.services.domainevent.ServicePointEventType.SERVICE_POINT_DELETED;
import static org.folio.services.domainevent.ServicePointEventType.SERVICE_POINT_UPDATED;
import static org.folio.utility.RestUtility.CONSORTIUM_CENTRAL_TENANT;
import static org.folio.utility.RestUtility.CONSORTIUM_MEMBER_TENANT;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.admin.KafkaAdminClient;
import io.vertx.kafka.client.common.TopicPartition;
import io.vertx.kafka.client.consumer.OffsetAndMetadata;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.ServicePoint;
import org.folio.rest.support.extension.EnableTenant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

/**
 * Covers {@code ServicePointSynchronizationVerticle}, which consumes service-point domain events
 * published on the consortium central tenant's own Kafka topic and propagates create/update/delete
 * to every member tenant. Publishes synthetic events directly onto that topic (rather than relying
 * on end-to-end domain-event timing from a plain POST/PUT/DELETE) to exercise the verticle's own
 * consumption logic in isolation, exactly like the legacy test this migrates.
 */
@EnableTenant(tenants = {CONSORTIUM_CENTRAL_TENANT, CONSORTIUM_MEMBER_TENANT})
@ExtendWith(SystemStubsExtension.class)
class ServicePointSynchronizationVerticleIT extends BaseIntegrationTest {

  private static final String SERVICE_POINT_TOPIC =
    format("%s.%s.inventory.service-point", environment(), CONSORTIUM_CENTRAL_TENANT);
  private static final String ECS_TLR_FEATURE_ENABLED = "ECS_TLR_FEATURE_ENABLED";
  private static final Duration PROPAGATION_TIMEOUT = Duration.ofSeconds(60);

  private static final String ID_FIELD = "id";
  private static final String NAME_FIELD = "name";
  private static final String CODE_FIELD = "code";
  private static final String DISCOVERY_DISPLAY_NAME_FIELD = "discoveryDisplayName";
  private static final String SHELVING_LAG_TIME_FIELD = "shelvingLagTime";
  private static final String PICKUP_LOCATION_FIELD = "pickupLocation";
  private static final String HOLD_SHELF_EXPIRY_PERIOD_FIELD = "holdShelfExpiryPeriod";
  private static final String TENANT_FIELD = "tenant";
  private static final String TYPE_FIELD = "type";
  private static final String OLD_FIELD = "old";
  private static final String NEW_FIELD = "new";

  private static KafkaProducer<String, JsonObject> producer;
  private static KafkaAdminClient adminClient;

  @SystemStub
  private final EnvironmentVariables env = new EnvironmentVariables(ECS_TLR_FEATURE_ENABLED, "true");

  // Generated fresh per test rather than once for the class: reusing a fixed id let a still-
  // draining async propagation from a previous test race the next test's own create with a
  // duplicate-key error.
  private String servicePointId;

  @BeforeAll
  static void setUpKafka(Vertx vertx) {
    var kafkaServerUrl = host() + ":" + port();
    var producerConfig = new Properties();
    producerConfig.put(BOOTSTRAP_SERVERS_CONFIG, kafkaServerUrl);
    producerConfig.put(ACKS_CONFIG, "1");
    producer = KafkaProducer.create(vertx, producerConfig, String.class, JsonObject.class);
    adminClient = KafkaAdminClient.create(vertx, Map.of(BOOTSTRAP_SERVERS_CONFIG, kafkaServerUrl));
  }

  @AfterAll
  static void tearDownKafka() {
    get(producer.close().compose(v -> adminClient.close()));
  }

  @BeforeEach
  void setUp() {
    servicePointId = UUID.randomUUID().toString();
    mockUserTenantsForConsortiumMember(CONSORTIUM_CENTRAL_TENANT);
    mockUserTenantsForConsortiumMember(CONSORTIUM_MEMBER_TENANT);
    mockConsortiumTenants();
  }

  @AfterEach
  void cleanUpServicePoint() {
    get(doDelete(client, ResourcePaths.SERVICE_POINTS + "/" + servicePointId, CONSORTIUM_CENTRAL_TENANT));
    get(doDelete(client, ResourcePaths.SERVICE_POINTS + "/" + servicePointId, CONSORTIUM_MEMBER_TENANT));
  }

  @Test
  @DisplayName("should propagate creation of a service point to the member tenant")
  void shouldPropagateServicePointCreation_toMemberTenant() {
    var servicePointFromCentralTenant = createServicePointOnCentralTenant();

    var createOffset = getOffsetForServicePointCreateEvents();
    publishServicePointCreateEvent(servicePointFromCentralTenant);
    waitUntilOffsetIncreases(createOffset, ServicePointSynchronizationVerticleIT::getOffsetForServicePointCreateEvents);

    var memberServicePoint = waitForServicePointOnTenant(CONSORTIUM_MEMBER_TENANT);
    assertThat(memberServicePoint.getId()).isEqualTo(servicePointFromCentralTenant.getId());
  }

  @Test
  @DisplayName("should propagate an update of a service point to the member tenant")
  void shouldPropagateServicePointUpdate_toMemberTenant() {
    var servicePointFromCentralTenant = createServicePointOnCentralTenant();

    // Creating the central service point already triggers the verticle's own create-propagation
    // to the member tenant (ECS_TLR_FEATURE_ENABLED=true). Wait for that shadow copy instead of
    // also creating one directly against the member tenant: the two would race on the same id and
    // whichever loses gets a duplicate-key error from postServicePoints.
    var createOffset = getOffsetForServicePointCreateEvents();
    publishServicePointCreateEvent(servicePointFromCentralTenant);
    waitUntilOffsetIncreases(createOffset, ServicePointSynchronizationVerticleIT::getOffsetForServicePointCreateEvents);
    var servicePointFromMemberTenant = waitForServicePointOnTenant(CONSORTIUM_MEMBER_TENANT);

    var updatedServicePointFromCentralTenant =
      servicePointFromCentralTenant.withDiscoveryDisplayName("Circulation Desk -- Basement(updated)");

    var updateOffset = getOffsetForServicePointUpdateEvents();
    publishServicePointUpdateEvent(servicePointFromMemberTenant, updatedServicePointFromCentralTenant);
    waitUntilOffsetIncreases(updateOffset, ServicePointSynchronizationVerticleIT::getOffsetForServicePointUpdateEvents);

    var updatedMemberServicePoint = waitForServicePointOnTenant(CONSORTIUM_MEMBER_TENANT, servicePoint ->
      updatedServicePointFromCentralTenant.getDiscoveryDisplayName().equals(servicePoint.getDiscoveryDisplayName()));
    assertThat(updatedMemberServicePoint.getDiscoveryDisplayName())
      .isEqualTo(updatedServicePointFromCentralTenant.getDiscoveryDisplayName());
  }

  @Test
  @DisplayName("should propagate deletion of a service point to the member tenant")
  void shouldPropagateServicePointDeletion_toMemberTenant() {
    var servicePointFromCentralTenant = createServicePointOnCentralTenant();

    // See shouldPropagateServicePointUpdate_toMemberTenant: wait for the verticle's own
    // create-propagation to the member tenant instead of creating a second copy directly, which
    // would race the propagation on the same id.
    var createOffset = getOffsetForServicePointCreateEvents();
    publishServicePointCreateEvent(servicePointFromCentralTenant);
    waitUntilOffsetIncreases(createOffset, ServicePointSynchronizationVerticleIT::getOffsetForServicePointCreateEvents);
    var servicePointFromMemberTenant = waitForServicePointOnTenant(CONSORTIUM_MEMBER_TENANT);

    var deleteOffset = getOffsetForServicePointDeleteEvents();
    publishServicePointDeleteEvent(servicePointFromMemberTenant);
    waitUntilOffsetIncreases(deleteOffset, ServicePointSynchronizationVerticleIT::getOffsetForServicePointDeleteEvents);

    waitForServicePointAbsentOnTenant(CONSORTIUM_MEMBER_TENANT);
  }

  @Test
  @DisplayName("should not create a service point when an update event is received for one that does not exist")
  void shouldNotCreateServicePoint_whenUpdateEventReceivedForNonExistingServicePoint() {
    var nonExistingServicePoint = new ServicePoint().withId(servicePointId);

    publishServicePointUpdateEvent(nonExistingServicePoint, nonExistingServicePoint);

    waitForServicePointAbsentOnTenant(CONSORTIUM_MEMBER_TENANT);
  }

  @Test
  @DisplayName("should have no effect when a delete event is received for a service point that does not exist")
  void shouldHaveNoEffect_whenDeleteEventReceivedForNonExistingServicePoint() {
    var nonExistingServicePoint = new ServicePoint().withId(servicePointId);

    publishServicePointDeleteEvent(nonExistingServicePoint);

    waitForServicePointAbsentOnTenant(CONSORTIUM_MEMBER_TENANT);
  }

  private ServicePoint createServicePointOnCentralTenant() {
    var request = new JsonObject()
      .put(ID_FIELD, servicePointId)
      .put(NAME_FIELD, "Circ Desk 2522")
      .put(CODE_FIELD, "cd2522")
      .put(DISCOVERY_DISPLAY_NAME_FIELD, "Circulation Desk -- Basement")
      .put(SHELVING_LAG_TIME_FIELD, 20)
      .put(PICKUP_LOCATION_FIELD, true)
      .put(HOLD_SHELF_EXPIRY_PERIOD_FIELD, JsonObject.mapFrom(createHoldShelfExpiryPeriod()));

    var response = get(doPost(client, ResourcePaths.SERVICE_POINTS, CONSORTIUM_CENTRAL_TENANT, request));
    assertThat(response.status()).isEqualTo(SC_CREATED);
    return response.bodyAsClass(ServicePoint.class);
  }

  /**
   * Polls for the propagated service point on {@code tenantId} instead of trusting a single GET
   * right after {@link #waitUntilOffsetIncreases}. The Kafka consumer group backing that offset
   * check is shared for the whole test JVM run and also advances for other tenants' unrelated
   * service-point activity, so "offset increased" alone doesn't guarantee this event's downstream
   * HTTP-level propagation has actually finished - only that some message got committed.
   */
  private ServicePoint waitForServicePointOnTenant(String tenantId) {
    return waitForServicePointOnTenant(tenantId, servicePoint -> true);
  }

  private ServicePoint waitForServicePointOnTenant(String tenantId, Predicate<ServicePoint> ready) {
    return await().atMost(PROPAGATION_TIMEOUT).until(() -> {
      var response = get(doGet(client, ResourcePaths.SERVICE_POINTS + "/" + servicePointId, tenantId));
      return response.status() == SC_OK ? response.bodyAsClass(ServicePoint.class) : null;
    }, servicePoint -> servicePoint != null && ready.test(servicePoint));
  }

  private void waitForServicePointAbsentOnTenant(String tenantId) {
    await().atMost(PROPAGATION_TIMEOUT).until(
      () -> get(doGet(client, ResourcePaths.SERVICE_POINTS + "/" + servicePointId, tenantId)).status(),
      status -> status == SC_NOT_FOUND);
  }

  private static int waitUntilOffsetIncreases(int previousValue, Callable<Integer> valueSupplier) {
    return await().atMost(PROPAGATION_TIMEOUT).until(valueSupplier, newValue -> newValue > previousValue);
  }

  private static JsonObject buildCreateEvent(ServicePoint newVersion) {
    return new JsonObject()
      .put(TENANT_FIELD, CONSORTIUM_CENTRAL_TENANT)
      .put(TYPE_FIELD, "CREATE")
      .put(NEW_FIELD, newVersion);
  }

  private static JsonObject buildUpdateEvent(ServicePoint oldVersion, ServicePoint newVersion) {
    return new JsonObject()
      .put(TENANT_FIELD, CONSORTIUM_CENTRAL_TENANT)
      .put(TYPE_FIELD, "UPDATE")
      .put(OLD_FIELD, oldVersion)
      .put(NEW_FIELD, newVersion);
  }

  private static JsonObject buildDeleteEvent(ServicePoint object) {
    return new JsonObject()
      .put(TENANT_FIELD, CONSORTIUM_CENTRAL_TENANT)
      .put(TYPE_FIELD, "DELETE")
      .put(OLD_FIELD, object);
  }

  private void publishServicePointCreateEvent(ServicePoint newServicePoint) {
    publishEvent(buildCreateEvent(newServicePoint));
  }

  private void publishServicePointUpdateEvent(ServicePoint oldServicePoint, ServicePoint newServicePoint) {
    publishEvent(buildUpdateEvent(oldServicePoint, newServicePoint));
  }

  private void publishServicePointDeleteEvent(ServicePoint servicePoint) {
    publishEvent(buildDeleteEvent(servicePoint));
  }

  private void publishEvent(JsonObject eventPayload) {
    var kafkaRecord = KafkaProducerRecord.create(SERVICE_POINT_TOPIC, servicePointId, eventPayload);
    kafkaRecord.addHeader(XOkapiHeaders.TENANT.toLowerCase(Locale.ROOT), CONSORTIUM_CENTRAL_TENANT);
    kafkaRecord.addHeader(XOkapiHeaders.TOKEN.toLowerCase(Locale.ROOT), "test-token");
    kafkaRecord.addHeader(XOkapiHeaders.URL, wm.baseUrl());
    get(producer.write(kafkaRecord));
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

  private static String buildConsumerGroupId(String eventType) {
    return format("%s.%s-%s", eventType, getModuleName().replace("_", "-"), getModuleVersion());
  }

  private static int getOffset(String consumerGroupId) {
    return get(adminClient.listConsumerGroupOffsets(consumerGroupId)
      .map(partitions -> Optional.ofNullable(partitions.get(new TopicPartition(SERVICE_POINT_TOPIC, 0)))
        .map(OffsetAndMetadata::getOffset)
        .map(Long::intValue)
        .orElse(0))); // if the topic does not exist yet
  }
}
