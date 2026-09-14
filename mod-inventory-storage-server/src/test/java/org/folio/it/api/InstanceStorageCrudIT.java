package org.folio.it.api;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.support.ResourcePaths.INSTANCES;
import static org.folio.support.ResourcePaths.INSTANCES_RETRIEVE;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.UUID;
import org.folio.rest.jaxrs.model.InstanceDates;
import org.folio.rest.jaxrs.model.Subject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InstanceStorageCrudIT extends InstanceStorageTestBase {

  private static final String DATES_KEY = "dates";
  // Migration-seeded instance_date_type row (see MIGRATION_SEEDED_TABLES), always present.
  private static final String INSTANCE_DATE_TYPE_ID = "42dac21e-3c81-4cb1-9f16-9e50c81bacc4";

  @Test
  @DisplayName("should create an instance with all fields populated")
  void shouldCreateInstance_withAllFieldsPopulated() {
    var id = UUID.randomUUID();
    var adminNote = "Administrative note";
    var dates = new InstanceDates().withDateTypeId(INSTANCE_DATE_TYPE_ID).withDate1("2023").withDate2("2024");
    var subject = new Subject().withSourceId(subjectSourceId).withTypeId(subjectTypeId).withValue("subject");
    var instanceToCreate = smallAngryPlanet(id)
      .put("administrativeNotes", new JsonArray().add(adminNote))
      .put(DATES_KEY, pojo2JsonObject(dates))
      .put(SUBJECTS_KEY, new JsonArray().add(pojo2JsonObject(subject)));

    var response = await(doPost(client, INSTANCES, instanceToCreate));
    assertThat(response.status()).isEqualTo(SC_CREATED);

    var instance = response.jsonBody();
    assertThat(instance.getString("id")).isEqualTo(id.toString());
    assertThat(instance.getString("title")).isEqualTo("Long Way to a Small Angry Planet");
    assertThat(instance.getBoolean("previouslyHeld")).isFalse();
    assertThat(instance.getJsonArray("administrativeNotes").contains(adminNote)).isTrue();
    assertThat(instance.getBoolean(DISCOVERY_SUPPRESS)).isFalse();

    var instanceFromGet = getById(id).jsonBody();
    assertThat(instanceFromGet.getString("title")).isEqualTo("Long Way to a Small Angry Planet");
    assertThat(instanceFromGet.getString(STATUS_UPDATED_DATE_PROPERTY)).isNotBlank();
    instanceMessageChecks.createdMessagePublished(instanceFromGet);

    assertDatesAndSubjectStored(instance, subjectSourceId, subjectTypeId);
  }

  @Test
  @DisplayName("should create an instance without providing an id")
  void shouldCreateInstance_withoutProvidingId() {
    var response = await(doPost(client, INSTANCES, smallAngryPlanet(null)));
    assertThat(response.status()).isEqualTo(SC_CREATED);

    var instance = response.jsonBody();
    var newId = instance.getString("id");
    assertThat(newId).isNotNull();

    var instanceFromGet = getById(newId).jsonBody();
    assertThat(instanceFromGet.getString("title")).isEqualTo("Long Way to a Small Angry Planet");
    var identifiers = instanceFromGet.getJsonArray("identifiers");
    assertThat(identifiers).hasSize(1);
    assertThat(identifiers.getJsonObject(0).getString("identifierTypeId")).isEqualTo(isbnTypeId);
    assertThat(instanceFromGet.getString(STATUS_UPDATED_DATE_PROPERTY)).isNotBlank();
  }

  @Test
  @DisplayName("should return 404 when putting an instance at a location that does not exist")
  void shouldReturn404_whenPuttingInstanceAtNonExistingLocation() {
    var id = UUID.randomUUID();
    var path = INSTANCES + "/" + id;

    var response = await(doPut(client, path, nod(id)));

    assertThat(response.status()).isEqualTo(SC_NOT_FOUND);
    assertGetNotFound(path);
  }

  @Test
  @DisplayName("should replace an instance at a specific location")
  void shouldReplaceInstance_atSpecificLocation() {
    var id = UUID.randomUUID();
    var adminNote = "An Admin note";
    var createdInstance = createInstance(smallAngryPlanet(id));
    removeAllEvents();

    var replacement = createdInstance.copy()
      .put("title", "A Long Way to a Small Angry Planet")
      .put("administrativeNotes", new JsonArray().add(adminNote));

    var putResponse = await(doPut(client, INSTANCES + "/" + id, replacement));
    assertThat(putResponse.status()).isEqualTo(SC_NO_CONTENT);

    var updatedInstance = getById(id);
    var itemFromGet = updatedInstance.jsonBody();
    assertThat(itemFromGet.getString("id")).isEqualTo(id.toString());
    assertThat(itemFromGet.getString("title")).isEqualTo("A Long Way to a Small Angry Planet");
    assertThat(itemFromGet.getString(STATUS_UPDATED_DATE_PROPERTY))
      .isEqualTo(replacement.getString(STATUS_UPDATED_DATE_PROPERTY));
    assertThat(itemFromGet.getBoolean(DISCOVERY_SUPPRESS)).isFalse();
    assertThat(itemFromGet.getJsonArray("administrativeNotes").contains(adminNote)).isTrue();

    instanceMessageChecks.updatedMessagePublished(createdInstance, updatedInstance.jsonBody());
  }

  @Test
  @DisplayName("should delete an instance")
  void shouldDeleteInstance() {
    var id = UUID.randomUUID();
    var createdInstance = createInstance(smallAngryPlanet(id));
    var path = INSTANCES + "/" + id;

    var deleteResponse = await(doDelete(client, path));

    assertThat(deleteResponse.status()).isEqualTo(SC_NO_CONTENT);
    assertGetNotFound(path);
    instanceMessageChecks.deletedMessagePublished(createdInstance);
  }

  @Test
  @DisplayName("should return 404 when deleting an instance that does not exist")
  void shouldReturn404_whenDeletingInstanceThatDoesNotExist() {
    var response = await(doDelete(client, INSTANCES + "/" + UUID.randomUUID()));

    assertThat(response.status()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  @DisplayName("should delete instances matching a CQL query")
  void shouldDeleteInstances_matchingCqlQuery() {
    var id5 = UUID.randomUUID();
    final var instance1 = createInstance(nod(UUID.randomUUID()).put("hrid", "1234"));
    final var instance2 = createInstance(nod(UUID.randomUUID()).put("hrid", "2123"));
    final var instance3 = createInstance(nod(UUID.randomUUID()).put("hrid", "12"));
    final var instance4 = createInstance(nod(UUID.randomUUID()).put("hrid", "345 12"));
    createInstance(nod(id5).put("hrid", "123"));
    putMarcJson(id5, marcJson);
    final var instance5 = getById(id5).jsonBody();

    var response = await(doDelete(client, INSTANCES + "?query=hrid==12*"));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    assertExists(instance2);
    assertExists(instance4);
    assertNotExists(instance1);
    assertNotExists(instance3);
    assertNotExists(instance5);
    assertMarcJsonNotFound(id5);

    instanceMessageChecks.deletedMessagePublished(instance1);
    instanceMessageChecks.deletedMessagePublished(instance3);
    instanceMessageChecks.deletedMessagePublished(instance5);
  }

  @Test
  @DisplayName("should return 400 when deleting instances with an empty CQL query")
  void shouldReturn400_whenDeletingInstancesWithEmptyCql() {
    var response = await(doDelete(client, INSTANCES + "?query="));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains("empty");
  }

  @Test
  @DisplayName("should get an instance by id")
  void shouldGetInstanceById() {
    var id = UUID.randomUUID();
    createInstance(smallAngryPlanet(id));

    var response = getById(id);

    assertThat(response.status()).isEqualTo(SC_OK);
    var instance = response.jsonBody();
    assertThat(instance.getString("id")).isEqualTo(id.toString());
    assertThat(instance.getString("title")).isEqualTo("Long Way to a Small Angry Planet");
    assertThat(instance.getJsonArray("identifiers")).hasSize(1);
  }

  @Test
  @DisplayName("should get all instances")
  void shouldGetAllInstances() {
    var firstInstanceId = UUID.randomUUID();
    var secondInstanceId = UUID.randomUUID();
    createInstance(smallAngryPlanet(firstInstanceId));
    createInstance(nod(secondInstanceId));

    var responseBody = await(doGet(client, INSTANCES)).jsonBody();
    var allInstances = responseBody.getJsonArray(INSTANCES_KEY);

    assertThat(allInstances).hasSize(2);
    assertThat(responseBody.getInteger(TOTAL_RECORDS_KEY)).isEqualTo(2);

    var ordered = orderInstancesById(allInstances, firstInstanceId);
    assertThat(ordered[0].getString("title")).isEqualTo("Long Way to a Small Angry Planet");
    assertThat(ordered[1].getString("title")).isEqualTo("Nod");
  }

  @Test
  @DisplayName("should retrieve all instances via the retrieve endpoint, sorted or unsorted")
  void shouldRetrieveAllInstances_sortedOrUnsorted() {
    var firstInstanceId = UUID.randomUUID();
    var secondInstanceId = UUID.randomUUID();
    createInstance(smallAngryPlanet(firstInstanceId));
    createInstance(nod(secondInstanceId));

    var allInstancesResponse = await(doPost(client, INSTANCES_RETRIEVE, new JsonObject())).jsonBody();
    var sortedResponse = await(doPost(client, INSTANCES_RETRIEVE,
      new JsonObject().put("query", "(cql.allRecords=1) sortBy title"))).jsonBody();

    var allInstances = allInstancesResponse.getJsonArray(INSTANCES_KEY);
    var sortedInstances = sortedResponse.getJsonArray(INSTANCES_KEY);
    assertThat(allInstances).hasSize(2);
    assertThat(sortedInstances).hasSize(2);
    assertThat(allInstancesResponse.getInteger(TOTAL_RECORDS_KEY)).isEqualTo(2);

    var ordered = orderInstancesById(allInstances, firstInstanceId);
    assertThat(ordered[0].getString("title")).isEqualTo("Long Way to a Small Angry Planet");
    assertThat(ordered[1].getString("title")).isEqualTo("Nod");
    assertThat(sortedInstances.getJsonObject(0).getString("title")).isEqualTo("Long Way to a Small Angry Planet");
  }

  @Test
  @DisplayName("should page through all instances")
  void shouldPageThroughAllInstances() {
    create5instances();

    var firstPage = await(doGet(client, INSTANCES + "?limit=3")).jsonBody();
    var secondPage = await(doGet(client, INSTANCES + "?limit=3&offset=3")).jsonBody();

    assertThat(firstPage.getJsonArray(INSTANCES_KEY)).hasSize(3);
    assertThat(firstPage.getInteger(TOTAL_RECORDS_KEY)).isEqualTo(5);
    assertThat(secondPage.getJsonArray(INSTANCES_KEY)).hasSize(2);
    assertThat(secondPage.getInteger(TOTAL_RECORDS_KEY)).isEqualTo(5);
  }

  @Test
  @DisplayName("should return no results for a large page offset and limit")
  void shouldReturnNoResults_forLargePageOffsetAndLimit() {
    create5instances();

    var page = await(doGet(client, INSTANCES + "?limit=5000&offset=5000")).jsonBody();

    assertThat(page.getJsonArray(INSTANCES_KEY)).isEmpty();
  }

  @Test
  @DisplayName("should delete all instances")
  void shouldDeleteAllInstances() {
    createInstance(smallAngryPlanet(UUID.randomUUID()));
    createInstance(nod(UUID.randomUUID()));
    createInstance(uprooted(UUID.randomUUID()));

    var deleteResponse = await(doDelete(client, INSTANCES + "?query=cql.allRecords=1"));
    assertThat(deleteResponse.status()).isEqualTo(SC_NO_CONTENT);

    var responseBody = await(doGet(client, INSTANCES)).jsonBody();
    assertThat(responseBody.getJsonArray(INSTANCES_KEY)).isEmpty();
    assertThat(responseBody.getInteger(TOTAL_RECORDS_KEY)).isZero();

    instanceMessageChecks.allInstancesDeletedMessagePublished();
  }

  @Test
  @DisplayName("should return 400 when the tenant is missing when creating an instance")
  void shouldReturn400_whenTenantMissingForCreatingInstance() {
    var response = await(doPost(client, INSTANCES, null, nod(UUID.randomUUID())));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body()).hasToString("Unable to process request Tenant must be set");
  }

  @Test
  @DisplayName("should return 400 when the tenant is missing when getting an instance")
  void shouldReturn400_whenTenantMissingForGettingInstance() {
    var response = await(doGet(client, INSTANCES + "/" + UUID.randomUUID(), null));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body()).hasToString("Unable to process request Tenant must be set");
  }

  @Test
  @DisplayName("should return 400 when the tenant is missing when getting all instances")
  void shouldReturn400_whenTenantMissingForGettingAllInstances() {
    var response = await(doGet(client, INSTANCES, null));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body()).hasToString("Unable to process request Tenant must be set");
  }

  @Test
  @DisplayName("should change the initial status-updated date when the status changes")
  void shouldChangeInitialStatusUpdatedDate_whenStatusChanges() {
    var id = UUID.randomUUID();
    createInstance(smallAngryPlanet(id).put("statusId", otherStatusId));

    var createdInstance = getById(id).jsonBody();
    var initialDate = createdInstance.getString(STATUS_UPDATED_DATE_PROPERTY);
    assertThat(initialDate).isNotBlank();

    var replacement = createdInstance.copy().put("statusId", catalogedStatusId);
    var updatedInstance = updateInstance(replacement);

    assertThat(updatedInstance.getString(STATUS_UPDATED_DATE_PROPERTY)).isNotBlank();
    assertThat(updatedInstance.getString(STATUS_UPDATED_DATE_PROPERTY)).isNotEqualTo(initialDate);
  }

  @Test
  @DisplayName("should not change the status-updated date when the status has not changed")
  void shouldNotChangeStatusUpdatedDate_whenStatusHasNotChanged() {
    var id = UUID.randomUUID();
    createInstance(smallAngryPlanet(id).put("statusId", otherStatusId));
    var createdInstance = getById(id).jsonBody();

    var withCatStatus = createdInstance.copy().put("statusId", catalogedStatusId);
    var updated1 = updateInstance(withCatStatus);

    var withCatStatusAgain = updated1.copy().put("statusId", catalogedStatusId);
    var updated2 = updateInstance(withCatStatusAgain);

    assertThat(updated1.getString(STATUS_UPDATED_DATE_PROPERTY))
      .isEqualTo(updated2.getString(STATUS_UPDATED_DATE_PROPERTY));
  }

  @Test
  @DisplayName("should return 400 when creating an instance with a duplicate match key")
  void shouldReturn400_whenCreatingInstanceWithDuplicateMatchKey() {
    var id = UUID.randomUUID();
    var instanceToCreate = smallAngryPlanet(id).put("matchKey", "match_key");
    setInstanceSequence(1);
    createInstance(instanceToCreate);
    assertThat(getById(id).jsonBody().getString("matchKey")).isEqualTo("match_key");

    var duplicate = nod(UUID.randomUUID()).put("matchKey", "match_key");
    var response = await(doPost(client, INSTANCES, duplicate));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body()).hasToString("lower(f_unaccent(jsonb ->> 'matchKey'::text)) value already "
                                            + "exists in table instance: match_key");
  }

  @Test
  @DisplayName("should create a discovery-suppressed instance")
  void shouldCreateDiscoverySuppressedInstance() {
    var instance = createInstance(smallAngryPlanet(UUID.randomUUID()).put(DISCOVERY_SUPPRESS, true));

    assertThat(instance.getBoolean(DISCOVERY_SUPPRESS)).isTrue();
    assertThat(getById(instance.getString("id")).jsonBody().getBoolean(DISCOVERY_SUPPRESS)).isTrue();
  }

  @Test
  @DisplayName("should update an instance's discovery-suppress property")
  void shouldUpdateInstance_discoverySuppressProperty() {
    var instance = createInstance(smallAngryPlanet(UUID.randomUUID()));
    assertThat(instance.getBoolean(DISCOVERY_SUPPRESS)).isFalse();
    removeAllEvents();

    var updated = updateInstance(getById(instance.getString("id")).jsonBody().copy().put(DISCOVERY_SUPPRESS, true));

    assertThat(getById(instance.getString("id")).jsonBody().getBoolean(DISCOVERY_SUPPRESS)).isTrue();
    instanceMessageChecks.updatedMessagePublished(instance, updated);
  }

  @Test
  @DisplayName("should patch an instance unlinking the subject source and type")
  void shouldPatchInstance_unlinkingSubjectSourceAndType() {
    var subject = new Subject().withSourceId(subjectSourceId).withTypeId(subjectTypeId).withValue("subject");
    var instanceToCreate = smallAngryPlanet(UUID.randomUUID())
      .put(SUBJECTS_KEY, new JsonArray().add(pojo2JsonObject(subject)));
    var newId = createInstance(instanceToCreate).getString("id");

    var patchJson = new JsonObject().put("id", newId).put("_version", 1).put("title", "New Title");

    var response = await(doPatch(client, INSTANCES + "/" + newId, patchJson));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should return 404 when patching an instance that does not exist")
  void shouldReturn404_whenPatchingInstanceThatDoesNotExist() {
    var id = UUID.randomUUID();
    var patchJson = new JsonObject().put("id", id.toString()).put("_version", 1).put("title", "new title");

    var response = await(doPatch(client, INSTANCES + "/" + id, patchJson));

    assertThat(response.status()).isEqualTo(SC_NOT_FOUND);
  }

  private static void assertDatesAndSubjectStored(JsonObject instance, String subjectSourceId, String subjectTypeId) {
    var storedDates = instance.getJsonObject(DATES_KEY).mapTo(InstanceDates.class);
    assertThat(storedDates.getDate1()).isEqualTo("2023");
    assertThat(storedDates.getDate2()).isEqualTo("2024");
    var storedSubject = instance.getJsonArray(SUBJECTS_KEY).getJsonObject(0).mapTo(Subject.class);
    assertThat(storedSubject.getSourceId()).isEqualTo(subjectSourceId);
    assertThat(storedSubject.getTypeId()).isEqualTo(subjectTypeId);
  }

  private static void assertNotExists(JsonObject instance) {
    assertGetNotFound(INSTANCES + "/" + instance.getString("id"));
  }

  private static JsonObject[] orderInstancesById(JsonArray allInstances, UUID expectedFirstId) {
    var first = allInstances.getJsonObject(0);
    var second = allInstances.getJsonObject(1);
    if (expectedFirstId.toString().equals(second.getString("id"))) {
      return new JsonObject[] {second, first};
    }
    return new JsonObject[] {first, second};
  }
}
