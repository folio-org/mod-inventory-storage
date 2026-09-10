package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_REQUEST_TOO_LONG;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstanceType;
import static org.folio.validator.NotesValidators.MAX_NOTE_LENGTH;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceDates;
import org.folio.rest.jaxrs.model.InstanceNote;
import org.folio.rest.jaxrs.model.MarcJson;
import org.folio.rest.jaxrs.model.Subject;
import org.folio.rest.support.messages.InstanceEventMessageChecks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InstanceStorageIT extends BaseIntegrationTest {

  private static final String INSTANCES_KEY = "instances";
  private static final String TOTAL_RECORDS_KEY = "totalRecords";
  private static final String METADATA_KEY = "metadata";
  private static final String DATES_KEY = "dates";
  private static final String SUBJECTS_KEY = "subjects";
  private static final String TAG_VALUE = "test-tag";
  private static final String STATUS_UPDATED_DATE_PROPERTY = "statusUpdatedDate";
  private static final String DISCOVERY_SUPPRESS = "discoverySuppress";
  private static final String STAFF_SUPPRESS = "staffSuppress";
  private static final String STATISTICAL_CODE_IDS_KEY = "statisticalCodeIds";
  private static final String INVALID_VALUE = "invalid value";
  private static final String INVALID_TYPE_ERROR_MESSAGE =
    "invalid input syntax for type uuid: \"" + INVALID_VALUE + "\"";
  private static final String OPTIMIZE_UPDATES_SETTING_KEY = "inventory.optimize-updates.enabled";
  // The "isbn"/"invalidIsbn" CQL indexes are backed by normalize_isbns()/normalize_invalid_isbns()
  // Postgres functions (see create_isbn_functions.sql) that hardcode these exact reference-data
  // ids - unlike every other type seeded below, these two cannot be fresh/random.
  private static final String ISBN_TYPE_ID = "8261054f-be78-422d-bd51-4ed9f33c3422";
  private static final String INVALID_ISBN_TYPE_ID = "fcca2643-406a-482a-b760-7a7f8aec640e";
  // Migration-seeded instance_date_type row (see MIGRATION_SEEDED_TABLES), always present.
  private static final String INSTANCE_DATE_TYPE_ID = "42dac21e-3c81-4cb1-9f16-9e50c81bacc4";

  // Reference data the legacy rest.api stack loads from reference-data/ at tenant install
  // (loadReference=true); the shared verticle's tenant doesn't, so this class seeds its own
  // copies once, with fresh ids - nothing in this class depends on the literal reference-data
  // UUIDs, only on referential consistency within its own fixtures.
  private static String instanceTypeId;
  private static String isbnTypeId;
  private static String asinTypeId;
  private static String invalidIsbnTypeId;
  private static String personalNameTypeId;
  private static String catalogedStatusId;
  private static String otherStatusId;
  private static String subjectSourceId;
  private static String subjectTypeId;

  private final InstanceEventMessageChecks instanceMessageChecks = eventMessageChecks();
  private final MarcJson marcJson = new MarcJson()
    .withLeader("xxxxxnam a22yyyyy c 4500")
    .withFields(List.of(
      new JsonObject().put("001", "029857716"),
      new JsonObject().put("245", new JsonObject().put("ind1", "0").put("ind2", "4")
        .put("subfields", new JsonArray().add(new JsonObject().put("a", "The Yearbook of Okapiology"))))));

  @BeforeAll
  static void seedReferenceData() {
    instanceTypeId = createInstanceType(client);
    isbnTypeId = createIdentifierType(ISBN_TYPE_ID, "ISBN");
    asinTypeId = createIdentifierType(null, "ASIN");
    invalidIsbnTypeId = createIdentifierType(INVALID_ISBN_TYPE_ID, "Invalid ISBN");
    personalNameTypeId = createContributorNameType("Personal name");
    catalogedStatusId = createInstanceStatus("cat", "Cataloged");
    otherStatusId = createInstanceStatus("other", "Other");
    subjectSourceId = get(doPost(client, ResourcePaths.SUBJECT_SOURCES,
      new JsonObject().put("name", "a subject source").put("source", "local"))).jsonBody().getString("id");
    subjectTypeId = get(doPost(client, ResourcePaths.SUBJECT_TYPES,
      new JsonObject().put("name", "a subject type").put("source", "local"))).jsonBody().getString("id");
  }

  @BeforeEach
  void clearInstances() {
    runQuery("TRUNCATE TABLE instance, holdings_record, item CASCADE");
  }

  @AfterEach
  void resetHridSequence() {
    setInstanceSequence(1);
  }

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

    var response = get(doPost(client, ResourcePaths.INSTANCES, instanceToCreate));
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

  private static void assertDatesAndSubjectStored(JsonObject instance, String subjectSourceId, String subjectTypeId) {
    var storedDates = instance.getJsonObject(DATES_KEY).mapTo(InstanceDates.class);
    assertThat(storedDates.getDate1()).isEqualTo("2023");
    assertThat(storedDates.getDate2()).isEqualTo("2024");
    var storedSubject = instance.getJsonArray(SUBJECTS_KEY).getJsonObject(0).mapTo(Subject.class);
    assertThat(storedSubject.getSourceId()).isEqualTo(subjectSourceId);
    assertThat(storedSubject.getTypeId()).isEqualTo(subjectTypeId);
  }

  @Test
  @DisplayName("should create an instance without providing an id")
  void shouldCreateInstance_withoutProvidingId() {
    var response = get(doPost(client, ResourcePaths.INSTANCES, smallAngryPlanet(null)));
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
  @DisplayName("should return 422 when the instance id is not a UUID")
  void shouldReturn422_whenInstanceIdIsNotUuid() {
    var instanceToCreate = new JsonObject()
      .put("id", "6556456")
      .put("source", "TEST")
      .put("title", "Long Way to a Small Angry Planet")
      .put("identifiers", new JsonArray().add(identifier(isbnTypeId, "9781473619777")))
      .put("contributors", new JsonArray().add(contributor(personalNameTypeId, "Chambers, Becky")))
      .put("instanceTypeId", instanceTypeId);

    var response = get(doPost(client, ResourcePaths.INSTANCES, instanceToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    assertThat(response.body().toString()).contains("must match");
  }

  @Test
  @DisplayName("should return 400 when a statistical code id is invalid")
  void shouldReturn400_whenStatisticalCodeIdIsInvalid() {
    var instanceToCreate = smallAngryPlanet(null).put(STATISTICAL_CODE_IDS_KEY, Set.of(INVALID_VALUE));

    var response = get(doPost(client, ResourcePaths.INSTANCES, instanceToCreate));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains(INVALID_TYPE_ERROR_MESSAGE);
  }

  @Test
  @DisplayName("should return 400 when updating an instance with a subject id that does not exist")
  void shouldReturn400_whenUpdatingInstanceWithNotExistingSubjectId() {
    var instanceId = createInstance(smallAngryPlanet(null)).getString("id");
    var instanceFromGet = getById(instanceId).jsonBody();
    var subject = new Subject().withSourceId(UUID.randomUUID().toString()).withValue("subject");
    instanceFromGet.put(SUBJECTS_KEY, new JsonArray().add(pojo2JsonObject(subject)));

    var response = update(instanceFromGet);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should return 400 when creating an instance with a subject id that does not exist")
  void shouldReturn400_whenCreatingInstanceWithNotExistingSubjectId() {
    var subject = new Subject()
      .withSourceId(UUID.randomUUID().toString())
      .withTypeId(UUID.randomUUID().toString())
      .withValue("subject");
    var instanceToCreate = smallAngryPlanet(null).put(SUBJECTS_KEY, new JsonArray().add(pojo2JsonObject(subject)));

    var response = get(doPost(client, ResourcePaths.INSTANCES, instanceToCreate));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should update an instance unlinking the subject source and type")
  void shouldUpdateInstance_unlinkingSubjectSourceAndType() {
    var subject = new Subject().withSourceId(subjectSourceId).withTypeId(subjectTypeId).withValue("subject");
    var instanceToCreate = smallAngryPlanet(UUID.randomUUID())
      .put(SUBJECTS_KEY, new JsonArray().add(pojo2JsonObject(subject)));
    var newId = createInstance(instanceToCreate).getString("id");

    var instanceFromGet = getById(newId).jsonBody();
    instanceFromGet.putNull(SUBJECTS_KEY);

    var response = update(instanceFromGet);

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should update an instance linking and unlinking the subject source and type")
  void shouldUpdateInstance_linkingAndUnlinkingSubjectSourceAndType() {
    var subject = new Subject().withSourceId(subjectSourceId).withTypeId(subjectTypeId).withValue("subject");
    var instanceToCreate = smallAngryPlanet(UUID.randomUUID())
      .put(SUBJECTS_KEY, new JsonArray().add(pojo2JsonObject(subject)));
    var newId = createInstance(instanceToCreate).getString("id");

    var instanceFromGet = getById(newId).jsonBody();
    var updatedSubject = new Subject()
      .withSourceId(subjectSourceId)
      .withTypeId(subjectTypeId)
      .withValue("subject upd");
    instanceFromGet.put(SUBJECTS_KEY, new JsonArray().add(pojo2JsonObject(updatedSubject)));

    var response = update(instanceFromGet);

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should return 404 when putting an instance at a location that does not exist")
  void shouldReturn404_whenPuttingInstanceAtNonExistingLocation() {
    var id = UUID.randomUUID();
    var path = ResourcePaths.INSTANCES + "/" + id;

    var response = get(doPut(client, path, nod(id)));

    assertThat(response.status()).isEqualTo(SC_NOT_FOUND);
    assertGetNotFound(path);
  }

  @Test
  @DisplayName("should return 422 when creating an instance whose note exceeds the maximum length")
  void shouldReturn422_whenCreatingInstanceNoteExceedsMaximumLength() {
    var id = UUID.randomUUID();
    var instanceToCreate = smallAngryPlanet(id)
      .put("notes", new JsonArray().add(new InstanceNote().withNote("x".repeat(MAX_NOTE_LENGTH + 1))));

    var response = get(doPost(client, ResourcePaths.INSTANCES, instanceToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should return 422 when creating an instance whose administrative note exceeds the maximum length")
  void shouldReturn422_whenCreatingInstanceAdministrativeNoteExceedsMaximumLength() {
    var id = UUID.randomUUID();
    var instanceToCreate = smallAngryPlanet(id)
      .put("administrativeNotes", new JsonArray().add("x".repeat(MAX_NOTE_LENGTH + 1)));

    var response = get(doPost(client, ResourcePaths.INSTANCES, instanceToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should return 422 when updating an instance's administrative note to exceed the maximum length")
  void shouldReturn422_whenUpdatingInstanceAdministrativeNoteExceedsMaximumLength() {
    var id = UUID.randomUUID();
    createInstance(smallAngryPlanet(id));
    var instance = getById(id).jsonBody();
    instance.put("administrativeNotes", new JsonArray().add("x".repeat(MAX_NOTE_LENGTH + 1)));

    assertThat(update(instance).status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should return 422 when updating an instance's note to exceed the maximum length")
  void shouldReturn422_whenUpdatingInstanceNoteExceedsMaximumLength() {
    var id = UUID.randomUUID();
    createInstance(smallAngryPlanet(id));
    var instance = getById(id).jsonBody();
    var longNote = new InstanceNote().withNote("x".repeat(MAX_NOTE_LENGTH + 1));
    instance.put("notes", new JsonArray().add(pojo2JsonObject(longNote)));

    assertThat(update(instance).status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should enforce optimistic locking on instance version")
  void shouldEnforceOptimisticLocking_onInstanceVersion() {
    var id = UUID.randomUUID();
    createInstance(nod(id));
    var instance = getById(id).jsonBody();
    instance.put("title", "foo");
    // updating with current _version 1 succeeds and increments _version to 2
    assertThat(update(instance).status()).isEqualTo(SC_NO_CONTENT);
    instance.put("title", "bar");
    // updating with outdated _version 1 fails, current _version is 2
    assertThat(update(instance).status()).isEqualTo(SC_CONFLICT);
    // updating with _version -1 should fail, single instance PUT never allows suppressing optimistic locking
    instance.put("_version", -1);
    assertThat(update(instance).status()).isEqualTo(SC_CONFLICT);
  }

  @Test
  @DisplayName("should not update an instance when there are no changes and optimize-updates is enabled")
  void shouldNotUpdateInstance_whenNoChangesAndOptimizeUpdatesEnabled() {
    assertThat(updateOptimizeUpdatesSetting(true).status()).isEqualTo(SC_NO_CONTENT);
    var id = UUID.randomUUID();
    createInstance(nod(id));
    var instance = getById(id).jsonBody();
    instanceMessageChecks.createdMessagePublished(id.toString());

    assertThat(update(instance).status()).isEqualTo(SC_NO_CONTENT);

    var updatedInstance = getById(id).jsonBody();
    assertThat(updatedInstance.getString("_version")).isEqualTo("1");
    assertThat(KAFKA_CONSUMER.getMessagesForInstance(id.toString())).hasSize(1);
  }

  @Test
  @DisplayName("should update an instance when there are no changes and optimize-updates is disabled")
  void shouldUpdateInstance_whenNoChangesAndOptimizeUpdatesDisabled() {
    assertThat(updateOptimizeUpdatesSetting(false).status()).isEqualTo(SC_NO_CONTENT);
    var id = UUID.randomUUID();
    createInstance(nod(id));
    var instance = getById(id).jsonBody();
    instanceMessageChecks.createdMessagePublished(id.toString());

    assertThat(update(instance).status()).isEqualTo(SC_NO_CONTENT);

    var updatedInstance = getById(id).jsonBody();
    assertThat(updatedInstance.getString("_version")).isEqualTo("2");
    instanceMessageChecks.updatedMessagePublished(instance, updatedInstance);
  }

  @Test
  @DisplayName("should return 422 when an instance has an additional unrecognized property")
  void shouldReturn422_whenInstanceHasAdditionalProperty() {
    var request = nod(UUID.randomUUID()).put("somethingAdditional", "foo");

    var response = get(doPost(client, ResourcePaths.INSTANCES, request));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    assertThat(response.jsonBody().mapTo(Errors.class).getErrors().getFirst().getMessage())
      .contains("Unrecognized field");
  }

  @Test
  @DisplayName("should return 422 when an instance identifier has an additional unrecognized property")
  void shouldReturn422_whenInstanceIdentifierHasAdditionalProperty() {
    var request = nod(UUID.randomUUID());
    request.getJsonArray("identifiers").add(identifier(isbnTypeId, "5645678432576").put("somethingAdditional", "foo"));

    var response = get(doPost(client, ResourcePaths.INSTANCES, request));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    assertThat(response.jsonBody().mapTo(Errors.class).getErrors().getFirst().getMessage())
      .contains("Unrecognized field");
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

    var putResponse = get(doPut(client, ResourcePaths.INSTANCES + "/" + id, replacement));
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
    var path = ResourcePaths.INSTANCES + "/" + id;

    var deleteResponse = get(doDelete(client, path));

    assertThat(deleteResponse.status()).isEqualTo(SC_NO_CONTENT);
    assertGetNotFound(path);
    instanceMessageChecks.deletedMessagePublished(createdInstance);
  }

  @Test
  @DisplayName("should return 404 when deleting an instance that does not exist")
  void shouldReturn404_whenDeletingInstanceThatDoesNotExist() {
    var response = get(doDelete(client, ResourcePaths.INSTANCES + "/" + UUID.randomUUID()));

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

    var response = get(doDelete(client, ResourcePaths.INSTANCES + "?query=hrid==12*"));

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
    var response = get(doDelete(client, ResourcePaths.INSTANCES + "?query="));

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

    var responseBody = get(doGet(client, ResourcePaths.INSTANCES)).jsonBody();
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

    var allInstancesResponse = get(doPost(client, ResourcePaths.INSTANCES_RETRIEVE, new JsonObject())).jsonBody();
    var sortedResponse = get(doPost(client, ResourcePaths.INSTANCES_RETRIEVE,
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
  @DisplayName("should search by classification number without an array modifier")
  void shouldSearchByClassificationNumber_withoutArrayModifier() {
    var classificationTypeId = UUID.randomUUID().toString();
    createInstance(smallAngryPlanet(UUID.randomUUID()).put("classifications", new JsonArray().add(
      new JsonObject().put("classificationTypeId", classificationTypeId).put("classificationNumber", "K1 .M385"))));
    createInstance(nod(UUID.randomUUID()).put("classifications", new JsonArray().add(
      new JsonObject().put("classificationTypeId", classificationTypeId).put("classificationNumber", "KB1 .A437"))));

    var allInstances = searchForInstances("classifications =\"K1 .M385\"");

    assertThat(allInstances.getInteger(TOTAL_RECORDS_KEY)).isEqualTo(1);
    assertThat(allInstances.getJsonArray(INSTANCES_KEY).getJsonObject(0).getString("title"))
      .isEqualTo("Long Way to a Small Angry Planet");
  }

  @Test
  @DisplayName("should search using the metadata date-updated index")
  void shouldSearchUsingMetadataDateUpdatedIndex() throws InterruptedException {
    createInstance(smallAngryPlanet(UUID.randomUUID()));
    Thread.sleep(2000); // ensure a different "updatedDate" than the first instance
    var second = createInstance(nod(UUID.randomUUID()));

    var metadata = second.getJsonObject(METADATA_KEY);
    var query = "metadata.updatedDate>=" + metadata.getString("updatedDate");

    var responseBody = searchForInstances(query);
    var allInstances = responseBody.getJsonArray(INSTANCES_KEY);

    assertThat(allInstances).hasSize(1);
    assertThat(allInstances.getJsonObject(0).getString("title")).isEqualTo(second.getString("title"));
  }

  @Test
  @DisplayName("should page through all instances")
  void shouldPageThroughAllInstances() {
    create5instances();

    var firstPage = get(doGet(client, ResourcePaths.INSTANCES + "?limit=3")).jsonBody();
    var secondPage = get(doGet(client, ResourcePaths.INSTANCES + "?limit=3&offset=3")).jsonBody();

    assertThat(firstPage.getJsonArray(INSTANCES_KEY)).hasSize(3);
    assertThat(firstPage.getInteger(TOTAL_RECORDS_KEY)).isEqualTo(5);
    assertThat(secondPage.getJsonArray(INSTANCES_KEY)).hasSize(2);
    assertThat(secondPage.getInteger(TOTAL_RECORDS_KEY)).isEqualTo(5);
  }

  @Test
  @DisplayName("should return no results for a large page offset and limit")
  void shouldReturnNoResults_forLargePageOffsetAndLimit() {
    create5instances();

    var page = get(doGet(client, ResourcePaths.INSTANCES + "?limit=5000&offset=5000")).jsonBody();

    assertThat(page.getJsonArray(INSTANCES_KEY)).isEmpty();
  }

  @Test
  @DisplayName("should create an instance MARC source record")
  void shouldCreateInstanceMarcSourceRecord() {
    var id = UUID.randomUUID();
    createInstance(smallAngryPlanet(id));

    putMarcJson(id, marcJson);

    var getResponse = getMarcJson(id);
    assertThat(getResponse.status()).isEqualTo(SC_OK);
    var body = getResponse.jsonBody();
    assertThat(body.getString("id")).isEqualTo(id.toString());
    assertThat(body.getString("leader")).isEqualTo("xxxxxnam a22yyyyy c 4500");
    var fields = body.getJsonArray("fields");
    assertThat(fields.getJsonObject(0).getString("001")).isEqualTo("029857716");
    assertThat(fields.getJsonObject(1).getJsonObject("245").getJsonArray("subfields").getJsonObject(0)
      .getString("a")).isEqualTo("The Yearbook of Okapiology");
    assertThat(body.getMap().keySet()).containsExactlyInAnyOrder("id", "leader", "fields");
    assertThat(body.getString(STATUS_UPDATED_DATE_PROPERTY)).isNull();
  }

  @Test
  @DisplayName("should update an instance MARC source record")
  void shouldUpdateInstanceMarcSourceRecord() throws IOException {
    var id = UUID.randomUUID();
    createInstance(smallAngryPlanet(id));

    putMarcJson(id, marcJson);
    putMarcJson(id, toMarcJson("/101073931X.mrcjson"));

    var getResponse = getMarcJson(id);
    assertThat(getResponse.status()).isEqualTo(SC_OK);
    assertThat(getResponse.jsonBody().getJsonArray("fields").getJsonObject(0).getString("001"))
      .isEqualTo("101073931X");
  }

  @Test
  @DisplayName("should return 404 when getting a MARC source record that does not exist")
  void shouldReturn404_whenGettingNonExistingSourceRecord() {
    assertMarcJsonNotFound(UUID.randomUUID());
  }

  @Test
  @DisplayName("should delete an instance's MARC source record")
  void shouldDeleteInstanceMarcSourceRecord() {
    var id = UUID.randomUUID();
    createInstance(smallAngryPlanet(id));
    assertThat(getSourceRecordFormat(id)).isNull();

    putMarcJson(id, marcJson);
    assertThat(getSourceRecordFormat(id)).isEqualTo("MARC-JSON");

    var deleteResponse = get(doDelete(client, ResourcePaths.INSTANCES + "/" + id + "/source-record/marc-json"));
    assertThat(deleteResponse.status()).isEqualTo(SC_NO_CONTENT);
    assertThat(getSourceRecordFormat(id)).isNull();
    assertMarcJsonNotFound(id);
  }

  @Test
  @DisplayName("should delete an instance's source record")
  void shouldDeleteInstanceSourceRecord() {
    var id = UUID.randomUUID();
    createInstance(smallAngryPlanet(id));
    assertThat(getSourceRecordFormat(id)).isNull();

    putMarcJson(id, marcJson);
    assertThat(getSourceRecordFormat(id)).isEqualTo("MARC-JSON");

    var deleteResponse = get(doDelete(client, ResourcePaths.INSTANCES + "/" + id + "/source-record"));
    assertThat(deleteResponse.status()).isEqualTo(SC_NO_CONTENT);
    assertThat(getSourceRecordFormat(id)).isNull();
    assertMarcJsonNotFound(id);
  }

  @Test
  @DisplayName("should delete the source record when deleting the instance")
  void shouldDeleteSourceRecord_whenDeletingInstance() {
    var id = UUID.randomUUID();
    createInstance(smallAngryPlanet(id));
    putMarcJson(id, marcJson);

    var deleteResponse = get(doDelete(client, ResourcePaths.INSTANCES + "/" + id));
    assertThat(deleteResponse.status()).isEqualTo(SC_NO_CONTENT);

    assertMarcJsonNotFound(id);
  }

  @Test
  @DisplayName("should return 404 when creating a source record without a matching instance")
  void shouldReturn404_whenCreatingSourceRecordWithoutInstance() {
    var id = UUID.randomUUID();

    var response = get(doPut(client, ResourcePaths.INSTANCES + "/" + id + "/source-record/marc-json",
      JsonObject.mapFrom(marcJson)));

    assertThat(response.status()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  @DisplayName("should delete all instances")
  void shouldDeleteAllInstances() {
    createInstance(smallAngryPlanet(UUID.randomUUID()));
    createInstance(nod(UUID.randomUUID()));
    createInstance(uprooted(UUID.randomUUID()));

    var deleteResponse = get(doDelete(client, ResourcePaths.INSTANCES + "?query=cql.allRecords=1"));
    assertThat(deleteResponse.status()).isEqualTo(SC_NO_CONTENT);

    var responseBody = get(doGet(client, ResourcePaths.INSTANCES)).jsonBody();
    assertThat(responseBody.getJsonArray(INSTANCES_KEY)).isEmpty();
    assertThat(responseBody.getInteger(TOTAL_RECORDS_KEY)).isZero();

    instanceMessageChecks.allInstancesDeletedMessagePublished();
  }

  @Test
  @DisplayName("should return 400 when the tenant is missing when creating an instance")
  void shouldReturn400_whenTenantMissingForCreatingInstance() {
    var response = get(doPost(client, ResourcePaths.INSTANCES, null, nod(UUID.randomUUID())));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).isEqualTo("Unable to process request Tenant must be set");
  }

  @Test
  @DisplayName("should return 400 when the tenant is missing when getting an instance")
  void shouldReturn400_whenTenantMissingForGettingInstance() {
    var response = get(doGet(client, ResourcePaths.INSTANCES + "/" + UUID.randomUUID(), null));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).isEqualTo("Unable to process request Tenant must be set");
  }

  @Test
  @DisplayName("should return 400 when the tenant is missing when getting all instances")
  void shouldReturn400_whenTenantMissingForGettingAllInstances() {
    var response = get(doGet(client, ResourcePaths.INSTANCES, null));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).isEqualTo("Unable to process request Tenant must be set");
  }

  @Test
  @DisplayName("should return an instance when filtering by tags")
  void shouldReturnInstance_whenFilteringByTags() {
    var tagValue = "important";
    var instanceWithTag = smallAngryPlanet(UUID.randomUUID())
      .put("tags", new JsonObject().put("tagList", new JsonArray().add(tagValue)));
    createInstance(instanceWithTag);
    createInstance(nod(UUID.randomUUID()));

    var responseBody = searchForInstances("tags.tagList=" + tagValue);
    var instances = responseBody.getJsonArray(INSTANCES_KEY);

    assertThat(instances).hasSize(1);
    assertThat(responseBody.getInteger(TOTAL_RECORDS_KEY)).isEqualTo(1);
    var tagList = instances.getJsonObject(0).getJsonObject("tags").getJsonArray("tagList");
    assertThat(tagList).contains(tagValue);
  }

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

    var response = get(doPost(client, ResourcePaths.INSTANCES_SYNC, instanceCollection));
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

    var response = get(doPost(client, ResourcePaths.INSTANCES_SYNC, instanceCollection));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    assertThat(response.jsonBody().mapTo(Errors.class).getErrors().getFirst().getMessage())
      .contains(invalidSubjectId);
  }

  @Test
  @DisplayName("should return 400 when a synchronous batch has an instance with an invalid statistical code id")
  void shouldReturn400_whenSynchronousBatchHasInvalidStatisticalCodeId() {
    var instanceCollection = requestForMultipleInstances(3);
    var instanceToCreate = smallAngryPlanet(UUID.randomUUID()).put(STATISTICAL_CODE_IDS_KEY, Set.of(INVALID_VALUE));
    instanceCollection.getJsonArray(INSTANCES_KEY).add(instanceToCreate);

    var response = get(doPost(client, ResourcePaths.INSTANCES_SYNC, instanceCollection));

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

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    assertThat(response.jsonBody().mapTo(Errors.class).getErrors().getFirst().getMessage())
      .contains("Unrecognized field \"invalidPropertyName\"");
    instancesArray.forEach(instance ->
      assertGetNotFound(ResourcePaths.INSTANCES + "/" + ((JsonObject) instance).getString("id")));
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch reuses an existing id without upsert")
  void shouldReturn422_whenSynchronousBatchReusesExistingIdWithoutUpsert() {
    assertReturns422ForExistingId(ResourcePaths.INSTANCES_SYNC);
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch reuses an existing id with upsert=false")
  void shouldReturn422_whenSynchronousBatchReusesExistingIdWithUpsertFalse() {
    assertReturns422ForExistingId(ResourcePaths.INSTANCES_SYNC + "?upsert=false");
  }

  private void assertReturns422ForExistingId(String path) {
    var duplicateId = UUID.randomUUID();
    var instancesArray = new JsonArray()
      .add(uprooted(UUID.randomUUID())).add(smallAngryPlanet(duplicateId)).add(temeraire(UUID.randomUUID()));
    createInstance(instancesArray.getJsonObject(1));

    var response = get(doPost(client, path, new JsonObject().put(INSTANCES_KEY, instancesArray)));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    assertGetNotFound(ResourcePaths.INSTANCES + "/" + instancesArray.getJsonObject(0).getString("id"));
    assertExists(instancesArray.getJsonObject(1));
    assertGetNotFound(ResourcePaths.INSTANCES + "/" + instancesArray.getJsonObject(2).getString("id"));
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

    var response = get(doPost(client, ResourcePaths.INSTANCES_SYNC + "?upsert=true",
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
    assertCreatesInstanceWithoutId(ResourcePaths.INSTANCES_SYNC + "?upsert=true");
  }

  @Test
  @DisplayName("should create an instance without an id via a synchronous batch without upsert")
  void shouldCreateInstanceWithoutId_viaSynchronousBatchWithoutUpsert() {
    assertCreatesInstanceWithoutId(ResourcePaths.INSTANCES_SYNC);
  }

  private void assertCreatesInstanceWithoutId(String path) {
    var instanceRecord = new JsonObject()
      .put("source", "MARC").put("title", "Test-Instance").put("instanceTypeId", instanceTypeId).put("_version", 1);

    var response = get(doPost(client, path, new JsonObject().put(INSTANCES_KEY, new JsonArray().add(instanceRecord))));
    assertThat(response.status()).isEqualTo(SC_CREATED);

    var found = searchForInstances("title=Test-Instance").getJsonArray(INSTANCES_KEY).getJsonObject(0);
    assertThat(found.getString("id")).isNotNull();
    assertThat(found.getString("title")).isEqualTo("Test-Instance");
    instanceMessageChecks.createdMessagePublished(getById(found.getString("id")).jsonBody());
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch has a duplicate id")
  void shouldReturn422_whenSynchronousBatchHasDuplicateId() {
    var duplicateId = UUID.randomUUID();
    var instancesArray = new JsonArray()
      .add(uprooted(duplicateId)).add(smallAngryPlanet(UUID.randomUUID())).add(temeraire(duplicateId));

    var response = syncBatch(instancesArray);

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    instancesArray.forEach(instance ->
      assertGetNotFound(ResourcePaths.INSTANCES + "/" + ((JsonObject) instance).getString("id")));
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
  @DisplayName("should generate an instance hrid when none is supplied")
  void shouldGenerateInstanceHrid_whenNoneSupplied() {
    var id = UUID.randomUUID();
    var instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.remove("hrid");
    setInstanceSequence(1);

    createInstance(instanceToCreate);

    assertThat(getById(id).jsonBody().getString("hrid")).isEqualTo("in00000000001");
  }

  @Test
  @DisplayName("should create an instance when a hrid is supplied")
  void shouldCreateInstance_whenHridSupplied() {
    var id = UUID.randomUUID();
    var instanceToCreate = smallAngryPlanet(id).put("hrid", "testHRID");

    createInstance(instanceToCreate);

    assertThat(getById(id).jsonBody().getString("hrid")).isEqualTo("testHRID");
  }

  @Test
  @DisplayName("should return 400 when creating an instance with a duplicate hrid")
  void shouldReturn400_whenCreatingInstanceWithDuplicateHrid() {
    var id = UUID.randomUUID();
    var instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.remove("hrid");
    setInstanceSequence(1);
    createInstance(instanceToCreate);
    assertThat(getById(id).jsonBody().getString("hrid")).isEqualTo("in00000000001");

    var duplicate = nod(UUID.randomUUID()).put("hrid", "in00000000001");
    var response = get(doPost(client, ResourcePaths.INSTANCES, duplicate));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).isEqualTo("HRID value already exists in table instance: in00000000001");
  }

  @Test
  @DisplayName("should return 500 when hrid generation fails because the sequence is exhausted")
  void shouldReturn500_whenHridGenerationFails() {
    var id = UUID.randomUUID();
    var instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.remove("hrid");
    setInstanceSequence(99_999_999_999L);
    createInstance(instanceToCreate);
    assertThat(getById(id).jsonBody().getString("hrid")).isEqualTo("in99999999999");

    var instanceToFail = nod(UUID.randomUUID());
    instanceToFail.remove("hrid");
    var response = get(doPost(client, ResourcePaths.INSTANCES, instanceToFail));

    assertThat(response.status()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    assertThat(response.body().toString()).contains("hrid_instances_seq");
  }

  @Test
  @DisplayName("should return 400 when changing the hrid after creation")
  void shouldReturn400_whenChangingHridAfterCreation() {
    var id = UUID.randomUUID();
    var instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.remove("hrid");
    setInstanceSequence(1);
    createInstance(instanceToCreate);
    var instance = getById(id).jsonBody();
    assertThat(instance.getString("hrid")).isEqualTo("in00000000001");
    instance.put("hrid", "testHRID");

    var response = get(doPut(client, ResourcePaths.INSTANCES + "/" + id, instance));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString())
      .isEqualTo("The hrid field cannot be changed: new=testHRID, old=in00000000001");
  }

  @Test
  @DisplayName("should allow changing the hrid when the source is consortia")
  void shouldAllowChangingHrid_whenSourceIsConsortia() {
    var id = UUID.randomUUID();
    createInstance(smallAngryPlanet(id).put("hrid", "oldHRID"));
    var newHrid = "newHRID";
    var instance = getById(id).jsonBody().put("source", "CONSORTIUM-MARC").put("hrid", newHrid);

    var updated = updateInstance(instance);

    assertThat(updated.mapTo(Instance.class).getHrid()).isEqualTo(newHrid);
  }

  @Test
  @DisplayName("should return 400 when creating an instance whose hrid is already allocated")
  void shouldReturn400_whenCreatingInstanceWithAlreadyAllocatedHrid() {
    var instanceRequest = smallAngryPlanet(UUID.randomUUID());
    instanceRequest.remove("id");
    instanceRequest.remove("hrid");
    setInstanceSequence(1000L);

    var firstAllocation = get(doPost(client, ResourcePaths.INSTANCES, instanceRequest)).jsonBody();
    assertThat(firstAllocation.getString("hrid")).isEqualTo("in00000001000");

    setInstanceSequence(1000L);
    var response = get(doPost(client, ResourcePaths.INSTANCES, instanceRequest));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).isEqualTo("HRID value already exists in table instance: in00000001000");
  }

  @Test
  @DisplayName("should return 400 when removing the hrid after creation")
  void shouldReturn400_whenRemovingHridAfterCreation() {
    var id = UUID.randomUUID();
    var instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.remove("hrid");
    setInstanceSequence(1);
    createInstance(instanceToCreate);
    var instance = getById(id).jsonBody();
    assertThat(instance.getString("hrid")).isEqualTo("in00000000001");
    instance.remove("hrid");

    var response = get(doPut(client, ResourcePaths.INSTANCES + "/" + id, instance));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).isEqualTo("The hrid field cannot be changed: new=null, old=in00000000001");
  }

  @Test
  @DisplayName("should generate hrids for a synchronous batch")
  void shouldGenerateHrids_forSynchronousBatch() {
    var instancesArray = new JsonArray()
      .add(uprooted(UUID.randomUUID())).add(smallAngryPlanet(UUID.randomUUID())).add(temeraire(UUID.randomUUID()));
    setInstanceSequence(1);

    var response = syncBatch(instancesArray);
    assertThat(response.status()).isEqualTo(SC_CREATED);

    for (int i = 0; i < 3; i++) {
      var hrid = getById(instancesArray.getJsonObject(i).getString("id")).jsonBody().getString("hrid");
      assertThat(hrid).isBetween("in00000000001", "in00000000003");
    }
  }

  @Test
  @DisplayName("should generate hrids for the instances in a batch missing one, alongside existing hrids")
  void shouldGenerateHrids_forInstancesMissingOneInBatch() {
    var ids = new UUID[5];
    var instancesArray = new JsonArray()
      .add(uprooted(ids[0] = UUID.randomUUID()))
      .add(uprooted(ids[1] = UUID.randomUUID()).put("hrid", "foo"))
      .add(uprooted(ids[2] = UUID.randomUUID()))
      .add(uprooted(ids[3] = UUID.randomUUID()).put("hrid", "bar"))
      .add(uprooted(ids[4] = UUID.randomUUID()));
    setInstanceSequence(1);

    var response = syncBatch(instancesArray);
    assertThat(response.status()).isEqualTo(SC_CREATED);

    assertThat(getById(ids[0]).jsonBody().getString("hrid")).isEqualTo("in00000000001");
    assertThat(getById(ids[1]).jsonBody().getString("hrid")).isEqualTo("foo");
    assertThat(getById(ids[2]).jsonBody().getString("hrid")).isEqualTo("in00000000002");
    assertThat(getById(ids[3]).jsonBody().getString("hrid")).isEqualTo("bar");
    assertThat(getById(ids[4]).jsonBody().getString("hrid")).isEqualTo("in00000000003");

    var nextHrid = get(doPost(client, ResourcePaths.INSTANCES, uprooted(UUID.randomUUID())))
      .jsonBody().getString("hrid");
    assertThat(nextHrid).isEqualTo("in00000000004");
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch has duplicate hrids")
  void shouldReturn422_whenSynchronousBatchHasDuplicateHrids() {
    var instancesArray = new JsonArray().add(uprooted(UUID.randomUUID()));
    instancesArray.add(temeraire(UUID.randomUUID()).put("hrid", "in00000000001"));
    setInstanceSequence(1);

    var response = syncBatch(instancesArray);

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var errors = response.jsonBody().mapTo(Errors.class);
    assertThat(errors.getErrors()).hasSize(1);
    var parameter = errors.getErrors().getFirst().getParameters().getFirst();
    assertThat(parameter.getKey()).contains("'hrid'");
    assertThat(parameter.getValue()).isEqualTo("in00000000001");
  }

  @Test
  @DisplayName("should return 500 when a synchronous batch fails to generate a hrid")
  void shouldReturn500_whenSynchronousBatchHridGenerationFails() {
    var instancesArray = new JsonArray().add(uprooted(UUID.randomUUID()));
    instancesArray.add(temeraire(UUID.randomUUID()).put("hrid", ""));
    setInstanceSequence(99_999_999_999L);

    var response = syncBatch(instancesArray);

    assertThat(response.status()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    assertThat(response.body().toString()).contains("hrid_instances_seq");
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
    var response = get(doPost(client, ResourcePaths.INSTANCES, duplicate));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).isEqualTo("lower(f_unaccent(jsonb ->> 'matchKey'::text)) value already "
      + "exists in table instance: match_key");
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
  @DisplayName("should search by discovery-suppress property")
  void shouldSearchByDiscoverySuppressProperty() {
    var suppressed = createInstance(smallAngryPlanet(UUID.randomUUID()).put(DISCOVERY_SUPPRESS, true));
    var notSuppressed = createInstance(smallAngryPlanet(UUID.randomUUID()));

    var suppressedResults = searchForInstances(DISCOVERY_SUPPRESS + "==true").getJsonArray(INSTANCES_KEY);
    var notSuppressedResults = searchForInstances(DISCOVERY_SUPPRESS + "==false").getJsonArray(INSTANCES_KEY);

    assertThat(suppressedResults).hasSize(1);
    assertThat(suppressedResults.getJsonObject(0).getString("id")).isEqualTo(suppressed.getString("id"));
    assertThat(notSuppressedResults).hasSize(1);
    assertThat(notSuppressedResults.getJsonObject(0).getString("id")).isEqualTo(notSuppressed.getString("id"));
  }

  @Test
  @DisplayName("should search by staff-suppress property")
  void shouldSearchByStaffSuppressProperty() {
    var suppressed = createInstance(smallAngryPlanet(UUID.randomUUID()).put(STAFF_SUPPRESS, true));
    var notSuppressed = createInstance(smallAngryPlanet(UUID.randomUUID()).put(STAFF_SUPPRESS, false));
    var notSuppressedDefault = createInstance(smallAngryPlanet(UUID.randomUUID()));

    var suppressedResults = searchForInstances(STAFF_SUPPRESS + "==true").getJsonArray(INSTANCES_KEY);
    var notSuppressedResults = searchForInstances("cql.allRecords=1 not " + STAFF_SUPPRESS + "==true")
      .getJsonArray(INSTANCES_KEY);

    assertThat(suppressedResults).hasSize(1);
    assertThat(suppressedResults.getJsonObject(0).getString("id")).isEqualTo(suppressed.getString("id"));
    assertThat(notSuppressedResults).hasSize(2);
    assertThat(notSuppressedResults.stream().map(o -> ((JsonObject) o).getString("id")).toList())
      .containsExactlyInAnyOrder(notSuppressed.getString("id"), notSuppressedDefault.getString("id"));
  }

  @Test
  @DisplayName("should patch an instance unlinking the subject source and type")
  void shouldPatchInstance_unlinkingSubjectSourceAndType() {
    var subject = new Subject().withSourceId(subjectSourceId).withTypeId(subjectTypeId).withValue("subject");
    var instanceToCreate = smallAngryPlanet(UUID.randomUUID())
      .put(SUBJECTS_KEY, new JsonArray().add(pojo2JsonObject(subject)));
    var newId = createInstance(instanceToCreate).getString("id");

    var patchJson = new JsonObject().put("id", newId).put("_version", 1).put("title", "New Title");

    var response = get(doPatch(client, ResourcePaths.INSTANCES + "/" + newId, patchJson));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should return 400 when patching an instance with a changed hrid")
  void shouldReturn400_whenPatchingInstanceWithChangedHrid() {
    var newId = createInstance(smallAngryPlanet(UUID.randomUUID())).getString("id");
    var patchJson = new JsonObject().put("id", newId).put("_version", 1).put("hrid", "12345");

    var response = get(doPatch(client, ResourcePaths.INSTANCES + "/" + newId, patchJson));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should patch an instance when the hrid is unchanged")
  void shouldPatchInstance_whenHridIsUnchanged() {
    var newId = createInstance(smallAngryPlanet(UUID.randomUUID())).getString("id");
    var existingHrid = getById(newId).jsonBody().getString("hrid");
    var patchJson = new JsonObject().put("id", newId).put("_version", 1).put("hrid", existingHrid);

    var response = get(doPatch(client, ResourcePaths.INSTANCES + "/" + newId, patchJson));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should return 409 when patching an instance whose version is stale")
  void shouldReturn409_whenPatchingInstanceWithStaleVersion() {
    var newId = createInstance(smallAngryPlanet(UUID.randomUUID())).getString("id");
    var firstPatch = new JsonObject().put("id", newId).put("_version", 1).put("title", "new title");
    assertThat(get(doPatch(client, ResourcePaths.INSTANCES + "/" + newId, firstPatch)).status())
      .isEqualTo(SC_NO_CONTENT);

    var stalePatch = new JsonObject().put("id", newId).put("_version", 1).put("title", "new title");
    var response = get(doPatch(client, ResourcePaths.INSTANCES + "/" + newId, stalePatch));

    assertThat(response.status()).isEqualTo(SC_CONFLICT);
  }

  @Test
  @DisplayName("should return 404 when patching an instance that does not exist")
  void shouldReturn404_whenPatchingInstanceThatDoesNotExist() {
    var id = UUID.randomUUID();
    var patchJson = new JsonObject().put("id", id.toString()).put("_version", 1).put("title", "new title");

    var response = get(doPatch(client, ResourcePaths.INSTANCES + "/" + id, patchJson));

    assertThat(response.status()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  @DisplayName("should return 422 when patching an instance with an administrative note that is too long")
  void shouldReturn422_whenPatchingInstanceWithLongAdministrativeNote() {
    var newId = createInstance(smallAngryPlanet(UUID.randomUUID())).getString("id");
    var patchJson = new JsonObject()
      .put("administrativeNotes", new JsonArray().add(StringUtils.repeat("a", MAX_NOTE_LENGTH + 1)));

    var response = get(doPatch(client, ResourcePaths.INSTANCES + "/" + newId, patchJson));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should return 422 when patching an instance with a note that is too long")
  void shouldReturn422_whenPatchingInstanceWithLongNote() {
    var newId = createInstance(smallAngryPlanet(UUID.randomUUID())).getString("id");
    var longNote = new InstanceNote()
      .withInstanceNoteTypeId(UUID.randomUUID().toString())
      .withNote(StringUtils.repeat("a", MAX_NOTE_LENGTH + 1));
    var patchJson = new JsonObject().put("notes", new JsonArray().add(pojo2JsonObject(longNote)));

    var response = get(doPatch(client, ResourcePaths.INSTANCES + "/" + newId, patchJson));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  // -- search / CQL --------------------------------------------------------------------------

  @Test
  @DisplayName("should search for instances by title")
  void shouldSearchForInstances_byTitle() {
    canSort("title=\"Upr*\"", "Uprooted");
  }

  @Test
  @DisplayName("should search for instances by a title word")
  void shouldSearchForInstances_byTitleWord() {
    canSort("title=\"Times\"", "Interesting Times");
  }

  @Test
  @DisplayName("should search for instances by title using the adj relation")
  void shouldSearchForInstances_byTitleAdj() {
    canSort("title adj \"Upro*\"", "Uprooted");
  }

  @Test
  @DisplayName("should search for instances using a query similar to the UI look-ahead search")
  void shouldSearchForInstances_usingUiLookAheadStyleQuery() {
    canSort("title=\"upr*\" or contributors=\"name\": \"upr*\" or identifiers=\"value\": \"upr*\"", "Uprooted");
  }

  @Test
  @DisplayName("should search for instances using the identifiers array modifier on value")
  void shouldSearchForInstances_usingIdentifiersArrayModifierOnValue() {
    canSort("identifiers = /@value 9781447294146", "Uprooted");
  }

  @Test
  @DisplayName("should search for instances using the identifiers array modifier on identifierTypeId and value")
  void shouldSearchForInstances_usingIdentifiersArrayModifierOnTypeAndValue() {
    canSort("identifiers = /@identifierTypeId = " + isbnTypeId + " 9781447294146", "Uprooted");
  }

  @Test
  @DisplayName("should search for instances using the identifiers array modifier on identifierTypeId")
  void shouldSearchForInstances_usingIdentifiersArrayModifierOnType() {
    canSort("identifiers = /@identifierTypeId " + asinTypeId, "Nod");
  }

  @Test
  @DisplayName("should search without being vulnerable to SQL injection via special characters")
  void shouldSearch_withoutSqlInjectionVulnerability() {
    create5instances();
    var specialChars = new String[] {"'", "''", "\\\"", "\\\"\\\"", "(", "((", ")", "))", "{", "{{", "}", "}}"};

    for (var s : specialChars) {
      matchInstanceTitles(searchForInstances("title=\"" + s + "Uprooted\""), "Uprooted");
      matchInstanceTitles(searchForInstances("title==\"" + s + "Uprooted\""));
      matchInstanceTitles(searchForInstances("identifiers=\"" + s + "\""));
      matchInstanceTitles(searchForInstances("identifiers==\"" + s + "\""));
    }
  }

  @Test
  @DisplayName("should search by subjects")
  void shouldSearchBySubjects() {
    createInstanceWithTypeAndSource(instanceWithSubjects("first", "foo", "bar", "baz"));
    createInstanceWithTypeAndSource(instanceWithSubjects("second", "abc def ghi", "uvw xyz"));

    matchInstanceTitles(searchForInstances("subjects=foo"), "first");
    matchInstanceTitles(searchForInstances("subjects=bar"), "first");
    matchInstanceTitles(searchForInstances("subjects=baz"), "first");
    matchInstanceTitles(searchForInstances("subjects=abc"), "second");
    matchInstanceTitles(searchForInstances("subjects=def"), "second");
    matchInstanceTitles(searchForInstances("subjects=ghi"), "second");
    matchInstanceTitles(searchForInstances("subjects=uvw"), "second");
    matchInstanceTitles(searchForInstances("subjects=xyz"), "second");
    matchInstanceTitles(searchForInstances("subjects=\"def ghi\""), "second");
    matchInstanceTitles(searchForInstances("subjects=\"uvw xyz\""), "second");
    matchInstanceTitles(searchForInstances("subjects=\"baz bar\""));
    matchInstanceTitles(searchForInstances("subjects=\"abc xyz\""));
  }

  @Test
  @DisplayName("should search by item barcode")
  void shouldSearchByItemBarcode() {
    var expectedInstanceId = UUID.randomUUID();
    var expectedHoldingId = UUID.randomUUID();
    createInstance(smallAngryPlanet(expectedInstanceId));
    createHoldingsForInstance(expectedHoldingId, expectedInstanceId);
    createItemWithBarcode(expectedHoldingId, "706949453641");

    var otherInstanceId = UUID.randomUUID();
    var otherHoldingId = UUID.randomUUID();
    createInstance(nod(otherInstanceId));
    createHoldingsForInstance(otherHoldingId, otherInstanceId);
    createItemWithBarcode(expectedHoldingId, "766043059304");

    canSort("item.barcode==706949453641", "Long Way to a Small Angry Planet");
  }

  @Test
  @DisplayName("should search by item barcode and holdings permanent location")
  void shouldSearchByItemBarcodeAndPermanentLocation() {
    var smallAngryPlanetInstanceId = UUID.randomUUID();
    var mainLibraryHoldingId = UUID.randomUUID();
    var annexHoldingId = UUID.randomUUID();
    var mainLibraryLocationId = createLocationId();
    var annexLocationId = createLocationId();
    createInstance(smallAngryPlanet(smallAngryPlanetInstanceId));
    createHoldingsWithLocationAndItem(mainLibraryHoldingId, smallAngryPlanetInstanceId, mainLibraryLocationId,
      "706949453641");
    createHoldingsWithLocationAndItem(annexHoldingId, smallAngryPlanetInstanceId, annexLocationId, "70704539201");

    var nodInstanceId = UUID.randomUUID();
    var nodHoldingId = UUID.randomUUID();
    createInstance(nod(nodInstanceId));
    createHoldingsWithLocationAndItem(nodHoldingId, nodInstanceId, mainLibraryLocationId, "766043059304");

    canSort("item.barcode==706949453641 and holdingsRecords.permanentLocationId==" + mainLibraryLocationId,
      "Long Way to a Small Angry Planet");
    canSort("holdingsRecords.permanentLocationId=\"" + mainLibraryLocationId + "\" sortBy title/sort.descending",
      "Nod", "Long Way to a Small Angry Planet");
  }

  @Test
  @DisplayName("should still find instances with no holdings or items when searching by title and barcode")
  void shouldFindInstancesWithNoHoldingsOrItems_whenSearchingByTitleAndBarcode() {
    var smallAngryPlanetInstanceId = UUID.randomUUID();
    var mainLibraryHoldingId = UUID.randomUUID();
    final var nodInstanceId = UUID.randomUUID();
    createInstance(smallAngryPlanet(smallAngryPlanetInstanceId));
    createHoldingsForInstance(mainLibraryHoldingId, smallAngryPlanetInstanceId);
    createItemWithBarcode(mainLibraryHoldingId, "706949453641");
    createInstance(nod(nodInstanceId));

    var responseBody = searchForInstances("item.barcode=706949453641* or title=Nod*");

    assertThat(responseBody.getInteger(TOTAL_RECORDS_KEY)).isEqualTo(2);
    var foundIds = responseBody.getJsonArray(INSTANCES_KEY).stream()
      .map(o -> ((JsonObject) o).getString("id")).toList();
    assertThat(foundIds).containsExactlyInAnyOrder(smallAngryPlanetInstanceId.toString(), nodInstanceId.toString());
  }

  @Test
  @DisplayName("should search for the first ISBN with additional hyphens")
  void shouldSearchForFirstIsbn_withAdditionalHyphens() {
    canSort("isbn = 0-552-16754-1", "Interesting Times");
  }

  @Test
  @DisplayName("should search for the first ISBN with additional hyphens and truncation")
  void shouldSearchForFirstIsbn_withAdditionalHyphensAndTruncation() {
    canSort("isbn = 05-5*", "Interesting Times");
  }

  @Test
  @DisplayName("should search for the second ISBN with missing hyphens")
  void shouldSearchForSecondIsbn_withMissingHyphens() {
    canSort("isbn = 9780552167543", "Interesting Times");
  }

  @Test
  @DisplayName("should search for the second ISBN with missing hyphens and truncation")
  void shouldSearchForSecondIsbn_withMissingHyphensAndTruncation() {
    canSort("isbn = 9780* sortBy title", "Interesting Times", "Temeraire");
  }

  @Test
  @DisplayName("should search for the second ISBN with altered hyphens")
  void shouldSearchForSecondIsbn_withAlteredHyphens() {
    canSort("isbn = 9-7-8-055-2167-543", "Interesting Times");
  }

  @Test
  @DisplayName("should not find an ISBN by its tail string")
  void shouldNotFindIsbn_byTailString() {
    canSort("isbn = 552-16754-3");
  }

  @Test
  @DisplayName("should not find an ISBN by an inner string with truncation")
  void shouldNotFindIsbn_byInnerStringWithTruncation() {
    canSort("isbn = 552*");
  }

  @Test
  @DisplayName("should find the first invalid ISBN")
  void shouldFindFirstInvalidIsbn() {
    canSort("invalidIsbn = 12345", "Interesting Times");
  }

  @Test
  @DisplayName("should not find an ISBN in the invalid-ISBN index")
  void shouldNotFindIsbn_inInvalidIsbnIndex() {
    canSort("invalidIsbn = 0552167541");
  }

  @Test
  @DisplayName("should not find an invalid ISBN in the ISBN index")
  void shouldNotFindInvalidIsbn_inIsbnIndex() {
    canSort("isbn = 12345");
  }

  @Test
  @DisplayName("should sort instances ascending by title")
  void shouldSortInstances_ascendingByTitle() {
    canSort("cql.allRecords=1 sortBy title",
      "Interesting Times", "Long Way to a Small Angry Planet", "Nod", "Temeraire", "Uprooted");
  }

  @Test
  @DisplayName("should sort instances descending by title")
  void shouldSortInstances_descendingByTitle() {
    canSort("cql.allRecords=1 sortBy title/sort.descending",
      "Uprooted", "Temeraire", "Nod", "Long Way to a Small Angry Planet", "Interesting Times");
  }

  @Test
  @DisplayName("should answer cross-table queries joining instances and holdings")
  void shouldAnswerCrossTableQueries_joiningInstancesAndHoldings() {
    final var loc1 = createLocationId();
    final var loc2 = createLocationId();
    var idJ1 = UUID.randomUUID();
    var idJ2 = UUID.randomUUID();
    var idJ3 = UUID.randomUUID();
    createInstance(instanceRequest(idJ1, "TEST1", "Long Way to a Small Angry Planet 1"));
    createInstance(instanceRequest(idJ2, "TEST2", "Long Way to a Small Angry Planet 2"));
    createInstance(instanceRequest(idJ3, "TEST3", "Long Way to a Small Angry Planet 3"));
    createHoldingsRecord(idJ1, loc1);
    createHoldingsRecord(idJ2, loc2);
    createHoldingsRecord(idJ3, loc2);

    assertCrossTableQuery("title=Long Way to a Small Angry Planet* sortby title", 3, "TEST1");
    assertCrossTableQuery("title=cql.allRecords=1 sortBy title", 3, "TEST1");
    assertCrossTableQuery("holdingsRecords.permanentLocationId=" + loc2 + " sortBy title", 2, "TEST2");
    assertCrossTableQuery("title=cql.allRecords=1 and holdingsRecords.permanentLocationId="
      + loc2 + " sortby title", 2, null);
    assertCrossTableQuery("title=cql.allRecords=1 and holdingsRecords.permanentLocationId=abc* sortby"
      + " holdingsRecords.permanentLocationId", 0, null);
  }

  private void assertCrossTableQuery(String cql, int expectedCount, String expectedFirstSource) {
    var responseBody = searchForInstances(cql);
    assertThat(responseBody.getInteger(TOTAL_RECORDS_KEY)).isEqualTo(expectedCount);
    if (expectedFirstSource != null) {
      assertThat(responseBody.getJsonArray(INSTANCES_KEY).getJsonObject(0).getString("source"))
        .isEqualTo(expectedFirstSource);
    }
  }

  // -- fixtures / helpers ---------------------------------------------------------------------

  private static String createIdentifierType(String id, String name) {
    var request = new JsonObject().put("name", name).put("source", "folio");
    if (id != null) {
      request.put("id", id);
    }
    var response = get(doPost(client, ResourcePaths.IDENTIFIER_TYPES, request));
    return response.jsonBody().getString("id");
  }

  private static String createContributorNameType(String name) {
    var response = get(doPost(client, ResourcePaths.CONTRIBUTOR_NAME_TYPES, new JsonObject().put("name", name)));
    return response.jsonBody().getString("id");
  }

  private static String createInstanceStatus(String code, String name) {
    var response = get(doPost(client, ResourcePaths.INSTANCE_STATUSES,
      new JsonObject().put("code", code).put("name", name).put("source", "folio")));
    return response.jsonBody().getString("id");
  }

  private static String createLocationId() {
    return LocationStorageFixtures.createLocation(client);
  }

  private static JsonObject identifier(String identifierTypeId, String value) {
    return new JsonObject().put("identifierTypeId", identifierTypeId).put("value", value);
  }

  private static JsonObject contributor(String contributorNameTypeId, String name) {
    return new JsonObject().put("contributorNameTypeId", contributorNameTypeId).put("name", name);
  }

  private static JsonObject instanceRequest(UUID id, String source, String title, JsonArray identifiers,
                                             JsonArray contributors, JsonArray tags) {
    var instance = new JsonObject();
    if (id != null) {
      instance.put("id", id.toString());
    }
    instance.put("title", title);
    instance.put("source", source);
    instance.put("identifiers", identifiers);
    instance.put("contributors", contributors);
    instance.put("instanceTypeId", instanceTypeId);
    instance.put("tags", new JsonObject().put("tagList", tags));
    instance.put("_version", 1);
    return instance;
  }

  private static JsonObject instanceRequest(UUID id, String source, String title) {
    return instanceRequest(id, source, title, new JsonArray().add(identifier(isbnTypeId, "9781473619777")),
      new JsonArray().add(contributor(personalNameTypeId, "Chambers, Becky")), new JsonArray().add(TAG_VALUE));
  }

  private static JsonObject smallAngryPlanet(UUID id) {
    return instanceRequest(id, "MARC", "Long Way to a Small Angry Planet",
      new JsonArray().add(identifier(isbnTypeId, "9781473619777")),
      new JsonArray().add(contributor(personalNameTypeId, "Chambers, Becky")), new JsonArray().add(TAG_VALUE));
  }

  private static JsonObject nod(UUID id) {
    return instanceRequest(id, "MARC", "Nod",
      new JsonArray().add(identifier(asinTypeId, "B01D1PLMDO")),
      new JsonArray().add(contributor(personalNameTypeId, "Barnes, Adrian")), new JsonArray().add(TAG_VALUE));
  }

  private static JsonObject uprooted(UUID id) {
    return instanceRequest(id, "MARC", "Uprooted",
      new JsonArray().add(identifier(isbnTypeId, "1447294149")).add(identifier(isbnTypeId, "9781447294146")),
      new JsonArray().add(contributor(personalNameTypeId, "Novik, Naomi")), new JsonArray().add(TAG_VALUE));
  }

  private static JsonObject temeraire(UUID id) {
    return instanceRequest(id, "MARC", "Temeraire",
      new JsonArray().add(identifier(isbnTypeId, "0007258712")).add(identifier(isbnTypeId, "9780007258710")),
      new JsonArray().add(contributor(personalNameTypeId, "Novik, Naomi")), new JsonArray().add(TAG_VALUE));
  }

  private static JsonObject interestingTimes(UUID id) {
    return instanceRequest(id, "MARC", "Interesting Times",
      new JsonArray().add(identifier(isbnTypeId, "0552167541")).add(identifier(isbnTypeId, "978-0-552-16754-3"))
        .add(identifier(invalidIsbnTypeId, "1-2-3-4-5")),
      new JsonArray().add(contributor(personalNameTypeId, "Pratchett, Terry")), new JsonArray().add(TAG_VALUE));
  }

  private static JsonObject instanceWithSubjects(String title, String... subjectValues) {
    var subjects = new JsonArray();
    for (var value : subjectValues) {
      subjects.add(new JsonObject().put("value", value));
    }
    return new JsonObject().put("title", title).put(SUBJECTS_KEY, subjects);
  }

  private static void createInstanceWithTypeAndSource(JsonObject instance) {
    instance.put("source", "test").put("instanceTypeId", instanceTypeId);
    createInstance(instance);
  }

  private static void createHoldingsForInstance(UUID holdingId, UUID instanceId) {
    var sourceId = HoldingsStorageFixtures.createHoldingsRecordsSource(client);
    var request = new org.folio.rest.support.builders.HoldingRequestBuilder()
      .withId(holdingId).withSource(UUID.fromString(sourceId))
      .withPermanentLocation(UUID.fromString(createLocationId())).forInstance(instanceId).create();
    var response = get(doPost(client, ResourcePaths.HOLDINGS, request));
    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  private static void createHoldingsWithLocationAndItem(UUID holdingId, UUID instanceId, String locationId,
                                                         String barcode) {
    var sourceId = HoldingsStorageFixtures.createHoldingsRecordsSource(client);
    var request = new org.folio.rest.support.builders.HoldingRequestBuilder()
      .withId(holdingId).withSource(UUID.fromString(sourceId))
      .withPermanentLocation(UUID.fromString(locationId)).forInstance(instanceId).create();
    var response = get(doPost(client, ResourcePaths.HOLDINGS, request));
    assertThat(response.status()).isEqualTo(SC_CREATED);
    createItemWithBarcode(holdingId, barcode);
  }

  private static void createItemWithBarcode(UUID holdingId, String barcode) {
    var materialTypeId = HoldingsStorageFixtures.createMaterialType(client);
    var loanTypeId = HoldingsStorageFixtures.createLoanType(client);
    var request = new org.folio.rest.support.builders.ItemRequestBuilder()
      .forHolding(holdingId).withBarcode(barcode)
      .withPermanentLoanType(UUID.fromString(loanTypeId)).withMaterialType(UUID.fromString(materialTypeId)).create();
    var response = get(doPost(client, ResourcePaths.ITEMS, request));
    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  private static void createHoldingsRecord(UUID instanceId, String locationId) {
    var sourceId = HoldingsStorageFixtures.createHoldingsRecordsSource(client);
    var holding = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("instanceId", instanceId.toString())
      .put("sourceId", sourceId)
      .put("permanentLocationId", locationId);
    var response = get(doPost(client, ResourcePaths.HOLDINGS, holding));
    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  private static JsonObject createInstance(JsonObject instanceToCreate) {
    var response = get(doPost(client, ResourcePaths.INSTANCES, instanceToCreate));
    assertThat(response.status()).isEqualTo(SC_CREATED);
    return response.jsonBody();
  }

  private static TestResponse syncBatch(JsonArray instancesArray) {
    return get(doPost(client, ResourcePaths.INSTANCES_SYNC, new JsonObject().put(INSTANCES_KEY, instancesArray)));
  }

  private static TestResponse syncBatchUnsafe(JsonArray instancesArray) {
    return get(doPost(client, ResourcePaths.INSTANCES_SYNC_UNSAFE,
      new JsonObject().put(INSTANCES_KEY, instancesArray)));
  }

  private static TestResponse update(JsonObject instance) {
    var id = instance.getString("id");
    return get(doPut(client, ResourcePaths.INSTANCES + "/" + id, instance));
  }

  private static JsonObject updateInstance(JsonObject instance) {
    var putResponse = update(instance);
    assertThat(putResponse.status()).isEqualTo(SC_NO_CONTENT);
    return getById(instance.getString("id")).jsonBody();
  }

  private static TestResponse getById(UUID id) {
    return getById(id.toString());
  }

  private static TestResponse getById(String id) {
    return get(doGet(client, ResourcePaths.INSTANCES + "/" + id));
  }

  private static void assertGetNotFound(String path) {
    assertThat(get(doGet(client, path)).status()).isEqualTo(SC_NOT_FOUND);
  }

  private static void assertExists(JsonObject expectedInstance) {
    var response = getById(expectedInstance.getString("id"));
    assertThat(response.status()).isEqualTo(SC_OK);
    assertThat(response.body().toString()).contains(expectedInstance.getString("title"));
  }

  private static void assertNotExists(JsonObject instance) {
    assertGetNotFound(ResourcePaths.INSTANCES + "/" + instance.getString("id"));
  }

  private static JsonObject searchForInstances(String cql) {
    var response = get(doGet(client, ResourcePaths.INSTANCES + "?query=" + urlEncode(cql)));
    assertThat(response.status()).isEqualTo(SC_OK);
    return response.jsonBody();
  }

  private static String urlEncode(String value) {
    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static void create5instances() {
    createInstance(smallAngryPlanet(UUID.randomUUID()));
    createInstance(nod(UUID.randomUUID()));
    createInstance(uprooted(UUID.randomUUID()));
    createInstance(temeraire(UUID.randomUUID()));
    createInstance(interestingTimes(UUID.randomUUID()));
  }

  private static void matchInstanceTitles(JsonObject jsonObject, String... expectedTitles) {
    var foundInstances = jsonObject.getJsonArray(INSTANCES_KEY);
    var titles = new String[foundInstances.size()];
    for (int i = 0; i < titles.length; i++) {
      titles[i] = foundInstances.getJsonObject(i).getString("title");
    }
    assertThat(titles).isEqualTo(expectedTitles);
    assertThat(jsonObject.getInteger(TOTAL_RECORDS_KEY)).isEqualTo(expectedTitles.length);
  }

  private static void canSort(String cql, String... expectedTitles) {
    create5instances();
    matchInstanceTitles(searchForInstances(cql), expectedTitles);
  }

  private static JsonObject[] orderInstancesById(JsonArray allInstances, UUID expectedFirstId) {
    var first = allInstances.getJsonObject(0);
    var second = allInstances.getJsonObject(1);
    if (expectedFirstId.toString().equals(second.getString("id"))) {
      return new JsonObject[] {second, first};
    }
    return new JsonObject[] {first, second};
  }

  private static JsonObject requestForMultipleInstances(int numberOfInstances) {
    var instancesArray = new JsonArray();
    for (int i = 0; i < numberOfInstances; i++) {
      instancesArray.add(smallAngryPlanet(UUID.randomUUID()));
    }
    return new JsonObject().put(INSTANCES_KEY, instancesArray);
  }

  private static void setInstanceSequence(long sequenceNumber) {
    runQuery("select setval('hrid_instances_seq'," + sequenceNumber + ",FALSE)");
  }

  private static TestResponse updateOptimizeUpdatesSetting(boolean value) {
    return get(doPatch(client, "/inventory-settings/" + OPTIMIZE_UPDATES_SETTING_KEY,
      new JsonObject().put("value", value)));
  }

  private static void putMarcJson(UUID id, MarcJson marcJson) {
    // JsonObject.mapFrom (not pojo2JsonObject) - RMB's ObjectMapperTool has no module for Vert.x's
    // JsonObject/JsonArray, so it would serialize the nested JsonObject fields via their "map"
    // getter instead of as plain JSON objects.
    var response = get(doPut(client, ResourcePaths.INSTANCES + "/" + id + "/source-record/marc-json",
      JsonObject.mapFrom(marcJson)));
    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
  }

  private static TestResponse getMarcJson(UUID id) {
    return get(doGet(client, ResourcePaths.INSTANCES + "/" + id + "/source-record/marc-json"));
  }

  private static void assertMarcJsonNotFound(UUID id) {
    assertGetNotFound(ResourcePaths.INSTANCES + "/" + id + "/source-record/marc-json");
  }

  private static String getSourceRecordFormat(UUID id) {
    return getById(id).jsonBody().getString("sourceRecordFormat");
  }

  private static MarcJson toMarcJson(String resourcePath) throws IOException {
    var mrcjson = IOUtils.toString(
      Objects.requireNonNull(InstanceStorageIT.class.getResourceAsStream(resourcePath)), StandardCharsets.UTF_8);
    var json = new JsonObject(mrcjson);
    List<Object> fields = new ArrayList<>(json.getJsonArray("fields").getList());
    return new MarcJson().withLeader(json.getString("leader")).withFields(fields);
  }

  private static InstanceEventMessageChecks eventMessageChecks() {
    try {
      return new InstanceEventMessageChecks(KAFKA_CONSUMER, new URL(wm.baseUrl()));
    } catch (MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }
}
