package org.folio.it.verticle;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.emptyList;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.TOKEN;
import static org.folio.okapi.common.XOkapiHeaders.URL;
import static org.folio.utility.RestUtility.CONSORTIUM_CENTRAL_TENANT;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.impl.KafkaConsumerRecordImpl;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.folio.InventoryKafkaTopic;
import org.folio.it.BaseIntegrationTest;
import org.folio.it.InstanceStorageFixtures;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.services.caches.ConsortiumData;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.consortium.ShadowInstanceSynchronizationHandler;
import org.folio.services.domainevent.DomainEvent;
import org.folio.services.domainevent.DomainEventType;
import org.folio.support.ResourcePaths;
import org.folio.support.builders.InstanceRequestBuilder;
import org.folio.support.extension.EnableTenant;
import org.folio.utility.RestUtility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers {@code ShadowInstanceSynchronizationHandler}, which - when an instance is updated on a
 * consortium's central tenant - looks up which member tenants hold a "shadow" copy of it (via a
 * mocked {@code GET /consortia/{id}/sharing/instances} call) and propagates the update to each.
 * Constructs the handler directly and calls {@link ShadowInstanceSynchronizationHandler#handle}
 * with a synthetic Kafka record, exactly like the legacy test this migrates, rather than
 * publishing a real Kafka message and waiting for a consumer to pick it up.
 */
@EnableTenant(tenants = {TENANT_ID, CONSORTIUM_CENTRAL_TENANT})
@ExtendWith(MockitoExtension.class)
class ShadowInstanceSynchronizationHandlerIT extends BaseIntegrationTest {

  private static final String SHARING_JOBS_PATH = "/consortia/.{36}/sharing/instances";
  private static final String CONSORTIUM_ID = UUID.randomUUID().toString();
  private static final String SHARING_INSTANCES_FIELD = "sharingInstances";
  private static final String SOURCE_TENANT_ID_FIELD = "sourceTenantId";
  private static final String TARGET_TENANT_ID_FIELD = "targetTenantId";
  private static final String INSTANCE_IDENTIFIER_FIELD = "instanceIdentifier";

  // A shadow-instance update copies its source instance's instanceTypeId verbatim onto the
  // target tenant, so both tenants need the same instance type id seeded, not independent ones.
  private static String instanceTypeId;

  @Mock
  private ConsortiumDataCache consortiaDataCache;
  private ShadowInstanceSynchronizationHandler synchronizationHandler;

  @BeforeAll
  static void seedReferenceData() {
    mockUserTenantsForNonConsortiumMember(TENANT_ID);
    mockUserTenantsForNonConsortiumMember(CONSORTIUM_CENTRAL_TENANT);
    instanceTypeId = InstanceStorageFixtures.createInstanceType(client, TENANT_ID);
    InstanceStorageFixtures.createInstanceType(client, CONSORTIUM_CENTRAL_TENANT, instanceTypeId);
  }

  @BeforeEach
  void setUp(Vertx vertx) {
    mockUserTenantsForNonConsortiumMember(CONSORTIUM_CENTRAL_TENANT);
    synchronizationHandler =
      new ShadowInstanceSynchronizationHandler(consortiaDataCache, vertx.createHttpClient(), vertx);

    var sharingCollection = new JsonObject()
      .put(SHARING_INSTANCES_FIELD, JsonArray.of(new JsonObject()
        .put(SOURCE_TENANT_ID_FIELD, CONSORTIUM_CENTRAL_TENANT)
        .put(TARGET_TENANT_ID_FIELD, TENANT_ID)
        .put(INSTANCE_IDENTIFIER_FIELD, UUID.randomUUID().toString())));

    wm.stubFor(WireMock.get(WireMock.urlPathMatching(SHARING_JOBS_PATH))
      .willReturn(WireMock.ok().withBody(sharingCollection.encodePrettily())));
  }

  private void mockConsortiumDataCache() {
    var consortiumData = new ConsortiumData(CONSORTIUM_CENTRAL_TENANT, CONSORTIUM_ID, emptyList());
    when(consortiaDataCache.getConsortiumData(anyString(), anyMap()))
      .thenReturn(succeededFuture(Optional.of(consortiumData)));
  }

  @Test
  @DisplayName("should update the shadow instance when the shared instance is updated on the central tenant")
  void shouldUpdateShadowInstance_whenSharedInstanceUpdatedOnCentralTenant() {
    mockConsortiumDataCache();
    var sharedId = UUID.randomUUID().toString();

    var shadowInstance = new Instance()
      .withId(sharedId)
      .withInstanceTypeId(instanceTypeId)
      .withTitle("test-title")
      .withSource("CONSORTIUM-MARC");

    var sharedInstance = new Instance()
      .withId(sharedId)
      .withInstanceTypeId(instanceTypeId)
      .withTitle("test-title-updated")
      .withSource("MARC");

    createInstance(sharedInstance, CONSORTIUM_CENTRAL_TENANT);
    createInstance(shadowInstance, TENANT_ID);

    var event = DomainEvent.updateEvent(sharedInstance, sharedInstance, CONSORTIUM_CENTRAL_TENANT);
    var kafkaRecord = buildKafkaRecord(sharedId, event);

    await(synchronizationHandler.handle(kafkaRecord));

    var updatedShadowInstance = getInstanceById(sharedId);
    assertThat(updatedShadowInstance.getTitle()).isEqualTo(sharedInstance.getTitle());
  }

  @Test
  @DisplayName("should not update the shadow instance when the event type is not an update")
  void shouldNotUpdateShadowInstance_whenEventTypeIsNotUpdate() {
    var instance = new Instance()
      .withId(UUID.randomUUID().toString())
      .withInstanceTypeId(instanceTypeId)
      .withTitle("title")
      .withSource("MARC");

    var event = DomainEvent.createEvent(instance, CONSORTIUM_CENTRAL_TENANT);
    assertThat(event.getType()).isNotEqualTo(DomainEventType.UPDATE);
    var kafkaRecord = buildKafkaRecord(instance.getId(), event);

    await(synchronizationHandler.handle(kafkaRecord));

    wm.verify(0, WireMock.getRequestedFor(WireMock.urlPathMatching(SHARING_JOBS_PATH)));
  }

  @Test
  @DisplayName("should not initiate a shadow instance update when the event's tenant is not the central tenant")
  void shouldNotInitiateShadowInstanceUpdate_whenEventTenantIsNotCentralTenant() {
    mockConsortiumDataCache();
    var instance = new Instance().withId(UUID.randomUUID().toString());
    var event = DomainEvent.updateEvent(instance, instance, TENANT_ID);
    assertThat(event.getTenant()).isNotEqualTo(CONSORTIUM_CENTRAL_TENANT);
    var kafkaRecord = buildKafkaRecord(instance.getId(), event);

    await(synchronizationHandler.handle(kafkaRecord));

    wm.verify(0, WireMock.getRequestedFor(WireMock.urlPathMatching(SHARING_JOBS_PATH)));
  }

  @Test
  @DisplayName("should return a failed future when fetching the instance sharing actions fails")
  void shouldReturnFailedFuture_whenFetchingInstanceSharingActionsFails() {
    mockConsortiumDataCache();
    wm.stubFor(WireMock.get(WireMock.urlPathMatching(SHARING_JOBS_PATH)).willReturn(WireMock.serverError()));

    var instance = new Instance().withId(UUID.randomUUID().toString());
    var event = DomainEvent.updateEvent(instance, instance, CONSORTIUM_CENTRAL_TENANT);
    var kafkaRecord = buildKafkaRecord(instance.getId(), event);

    assertThatThrownBy(() -> await(synchronizationHandler.handle(kafkaRecord)))
      .isInstanceOf(IllegalStateException.class);

    wm.verify(1, WireMock.getRequestedFor(WireMock.urlPathMatching(SHARING_JOBS_PATH)));
  }

  private void createInstance(Instance instanceToCreate, String tenantId) {
    var request = new InstanceRequestBuilder()
      .withId(UUID.fromString(instanceToCreate.getId()))
      .withTitle(instanceToCreate.getTitle())
      .withSource(instanceToCreate.getSource())
      .withInstanceTypeId(UUID.fromString(instanceToCreate.getInstanceTypeId()))
      .create();

    var response = await(doPost(client, ResourcePaths.INSTANCES, tenantId, request));
    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  private Instance getInstanceById(String instanceId) {
    var response =
      await(doGet(client, ResourcePaths.INSTANCES + "/" + instanceId, RestUtility.TENANT_ID));
    return response.bodyAsClass(Instance.class);
  }

  private KafkaConsumerRecordImpl<String, String> buildKafkaRecord(String recordKey, DomainEvent<Instance> event) {
    var topic = InventoryKafkaTopic.INSTANCE.fullTopicName(CONSORTIUM_CENTRAL_TENANT);
    ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>(topic, 0, 0, recordKey, Json.encode(event));
    consumerRecord.headers().add(new RecordHeader(TENANT.toLowerCase(), CONSORTIUM_CENTRAL_TENANT.getBytes()));
    consumerRecord.headers().add(new RecordHeader(URL.toLowerCase(), wm.baseUrl().getBytes()));
    consumerRecord.headers().add(new RecordHeader(TOKEN.toLowerCase(), "test-token".getBytes()));
    return new KafkaConsumerRecordImpl<>(consumerRecord);
  }
}
