package org.folio.it.api.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_BAD_REQUEST;
import static org.folio.HttpStatus.SC_CREATED;
import static org.folio.HttpStatus.SC_REQUEST_TOO_LONG;
import static org.folio.HttpStatus.SC_UNPROCESSABLE_CONTENT;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.support.ResourcePaths.INSTANCES;
import static org.folio.support.ResourcePaths.INSTANCES_SYNC;
import static org.folio.support.ResourcePaths.INSTANCES_SYNC_UNSAFE;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.Set;
import java.util.UUID;
import org.folio.rest.impl.StorageHelper;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Subject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InstanceStorageBatchIT extends InstanceStorageTestBase {

  @Test
  @DisplayName("should return 413 when a sync batch exceeds the maximum entities property")
  void shouldReturn413_whenSyncBatchExceedsMaxEntities() {
    var instancesArray = new JsonArray();
    for (int i = 0; i < StorageHelper.MAX_ENTITIES + 1; i++) {
      instancesArray.add(smallAngryPlanet(UUID.randomUUID()).put("hrid", "hrid" + i));
    }

    var response = syncBatch(instancesArray);

    assertThat(response.status()).isEqualTo(SC_REQUEST_TOO_LONG);
  }

  @Test
  @DisplayName("should create instances via a synchronous batch")
  void shouldCreateInstances_viaSynchronousBatch() {
    var instancesArray = new JsonArray()
      .add(uprooted(UUID.randomUUID())).add(smallAngryPlanet(UUID.randomUUID())).add(temeraire(UUID.randomUUID()));

    var response = syncBatch(instancesArray);
    assertThat(response.status()).isEqualTo(SC_CREATED);

    var createdInstances = instancesArray.stream()
      .map(JsonObject.class::cast)
      .map(json -> json.getString("id"))
      .map(id -> getById(id).jsonBody())
      .toList();

    createdInstances.forEach(instance -> assertThat(instance.getBoolean(DISCOVERY_SUPPRESS)).isFalse());
    instanceMessageChecks.createdMessagesPublished(createdInstances);
  }

  @Test
  @DisplayName("should give instances created in a synchronous batch metadata")
  void shouldGiveInstancesCreatedInSynchronousBatch_metadata() {
    var instanceCollection = requestForMultipleInstances(2);

    var response = await(doPost(client, INSTANCES_SYNC, instanceCollection));
    assertThat(response.status()).isEqualTo(SC_CREATED);

    instanceCollection.getJsonArray(INSTANCES_KEY).stream().map(JsonObject.class::cast).forEach(instance -> {
      var metadata = getById(instance.getString("id")).jsonBody().getJsonObject(METADATA_KEY);
      assertThat(metadata).isNotNull();
      assertThat(metadata.getString("createdDate")).isNotNull();
      assertThat(metadata.getString("updatedDate")).isNotNull();
    });
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch has an instance with a non-existing subject id")
  void shouldReturn422_whenSynchronousBatchHasNonExistingSubjectId() {
    var instanceCollection = requestForMultipleInstances(3);
    var invalidSubjectId = UUID.randomUUID().toString();
    var subject = new Subject().withSourceId(invalidSubjectId).withValue("subject");
    var instanceToCreate = smallAngryPlanet(UUID.randomUUID())
      .put(SUBJECTS_KEY, new JsonArray().add(pojo2JsonObject(subject)));
    instanceCollection.getJsonArray(INSTANCES_KEY).add(instanceToCreate);

    var response = await(doPost(client, INSTANCES_SYNC, instanceCollection));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(response.jsonBody().mapTo(Errors.class).getErrors().getFirst().getMessage())
      .contains(invalidSubjectId);
  }

  @Test
  @DisplayName("should return 400 when a synchronous batch has an instance with an invalid statistical code id")
  void shouldReturn400_whenSynchronousBatchHasInvalidStatisticalCodeId() {
    var instanceCollection = requestForMultipleInstances(3);
    var instanceToCreate = smallAngryPlanet(UUID.randomUUID()).put(STATISTICAL_CODE_IDS_KEY, Set.of(INVALID_VALUE));
    instanceCollection.getJsonArray(INSTANCES_KEY).add(instanceToCreate);

    var response = await(doPost(client, INSTANCES_SYNC, instanceCollection));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains(INVALID_TYPE_ERROR_MESSAGE);
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch has an invalid instance")
  void shouldReturn422_whenSynchronousBatchHasInvalidInstance() {
    var instancesArray = new JsonArray().add(uprooted(UUID.randomUUID()))
      .add(smallAngryPlanet(UUID.randomUUID()).put("invalidPropertyName", "bar"))
      .add(temeraire(UUID.randomUUID()));

    var response = syncBatch(instancesArray);

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(response.jsonBody().mapTo(Errors.class).getErrors().getFirst().getMessage())
      .contains("Unrecognized field \"invalidPropertyName\"");
    instancesArray.forEach(instance ->
      assertGetNotFound(INSTANCES + "/" + ((JsonObject) instance).getString("id")));
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch reuses an existing id without upsert")
  void shouldReturn422_whenSynchronousBatchReusesExistingIdWithoutUpsert() {
    assertReturns422ForExistingId(INSTANCES_SYNC);
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch reuses an existing id with upsert=false")
  void shouldReturn422_whenSynchronousBatchReusesExistingIdWithUpsertFalse() {
    assertReturns422ForExistingId(INSTANCES_SYNC + "?upsert=false");
  }

  @Test
  @DisplayName("should upsert an instance via a synchronous batch when the id already exists and upsert=true")
  void shouldUpsertInstance_viaSynchronousBatchWithUpsertTrue() {
    var duplicateId = UUID.randomUUID();
    final var existingInstance = createInstance(nod(duplicateId));
    var firstInstanceToCreate = uprooted(UUID.randomUUID());
    var instanceToUpdate = smallAngryPlanet(duplicateId);
    var secondInstanceToCreate = temeraire(UUID.randomUUID());
    var instancesArray = new JsonArray().add(firstInstanceToCreate).add(instanceToUpdate).add(secondInstanceToCreate);

    var response = await(doPost(client, INSTANCES_SYNC + "?upsert=true",
      new JsonObject().put(INSTANCES_KEY, instancesArray)));

    assertThat(response.status()).isEqualTo(SC_CREATED);
    assertExists(instancesArray.getJsonObject(0));
    assertExists(instancesArray.getJsonObject(1));
    assertExists(instancesArray.getJsonObject(2));

    var updatedInstance = getById(duplicateId).jsonBody();
    assertThat(updatedInstance.getString("title")).isEqualTo("Long Way to a Small Angry Planet");

    instanceMessageChecks.updatedMessagePublished(existingInstance, updatedInstance);
    instanceMessageChecks.createdMessagePublished(getById(firstInstanceToCreate.getString("id")).jsonBody());
    instanceMessageChecks.createdMessagePublished(getById(secondInstanceToCreate.getString("id")).jsonBody());
  }

  @Test
  @DisplayName("should create an instance without an id via a synchronous batch with upsert=true")
  void shouldCreateInstanceWithoutId_viaSynchronousBatchWithUpsertTrue() {
    assertCreatesInstanceWithoutId(INSTANCES_SYNC + "?upsert=true");
  }

  @Test
  @DisplayName("should create an instance without an id via a synchronous batch without upsert")
  void shouldCreateInstanceWithoutId_viaSynchronousBatchWithoutUpsert() {
    assertCreatesInstanceWithoutId(INSTANCES_SYNC);
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch has a duplicate id")
  void shouldReturn422_whenSynchronousBatchHasDuplicateId() {
    var duplicateId = UUID.randomUUID();
    var instancesArray = new JsonArray()
      .add(uprooted(duplicateId)).add(smallAngryPlanet(UUID.randomUUID())).add(temeraire(duplicateId));

    var response = syncBatch(instancesArray);

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    instancesArray.forEach(instance ->
      assertGetNotFound(INSTANCES + "/" + ((JsonObject) instance).getString("id")));
  }

  @Test
  @DisplayName("should return 413 when an unsafe synchronous batch is not allowed")
  void shouldReturn413_whenUnsafeSynchronousBatchNotAllowed() {
    // not allowed because env var DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING is not set
    var instances = new JsonArray().add(uprooted(UUID.randomUUID())).add(temeraire(UUID.randomUUID()));

    var response = syncBatchUnsafe(instances);

    assertThat(response.status()).isEqualTo(SC_REQUEST_TOO_LONG);
  }

  @Test
  @DisplayName("should create discovery-suppressed instances via a synchronous batch")
  void shouldCreateDiscoverySuppressedInstances_viaSynchronousBatch() {
    var suppressedId = UUID.randomUUID();
    var notSuppressedId = UUID.randomUUID();
    var instancesArray = new JsonArray()
      .add(uprooted(notSuppressedId))
      .add(smallAngryPlanet(suppressedId).put(DISCOVERY_SUPPRESS, true));

    var response = syncBatch(instancesArray);

    assertThat(response.status()).isEqualTo(SC_CREATED);
    assertThat(getById(suppressedId).jsonBody().getBoolean(DISCOVERY_SUPPRESS)).isTrue();
    assertThat(getById(notSuppressedId).jsonBody().getBoolean(DISCOVERY_SUPPRESS)).isFalse();
  }

  private static void assertReturns422ForExistingId(String path) {
    var duplicateId = UUID.randomUUID();
    var instancesArray = new JsonArray()
      .add(uprooted(UUID.randomUUID())).add(smallAngryPlanet(duplicateId)).add(temeraire(UUID.randomUUID()));
    createInstance(instancesArray.getJsonObject(1));

    var response = await(doPost(client, path, new JsonObject().put(INSTANCES_KEY, instancesArray)));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertGetNotFound(INSTANCES + "/" + instancesArray.getJsonObject(0).getString("id"));
    assertExists(instancesArray.getJsonObject(1));
    assertGetNotFound(INSTANCES + "/" + instancesArray.getJsonObject(2).getString("id"));
  }

  private void assertCreatesInstanceWithoutId(String path) {
    var instanceRecord = new JsonObject()
      .put("source", "MARC").put("title", "Test-Instance").put("instanceTypeId", instanceTypeId).put("_version", 1);

    var response = await(
      doPost(client, path, new JsonObject().put(INSTANCES_KEY, new JsonArray().add(instanceRecord))));
    assertThat(response.status()).isEqualTo(SC_CREATED);

    var found = searchForInstances("title=Test-Instance").getJsonArray(INSTANCES_KEY).getJsonObject(0);
    assertThat(found.getString("id")).isNotNull();
    assertThat(found.getString("title")).isEqualTo("Test-Instance");
    instanceMessageChecks.createdMessagePublished(getById(found.getString("id")).jsonBody());
  }

  private static JsonObject requestForMultipleInstances(int numberOfInstances) {
    var instancesArray = new JsonArray();
    for (int i = 0; i < numberOfInstances; i++) {
      instancesArray.add(smallAngryPlanet(UUID.randomUUID()));
    }
    return new JsonObject().put(INSTANCES_KEY, instancesArray);
  }

  private static TestResponse syncBatchUnsafe(JsonArray instancesArray) {
    return await(doPost(client, INSTANCES_SYNC_UNSAFE,
      new JsonObject().put(INSTANCES_KEY, instancesArray)));
  }
}
