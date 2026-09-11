package org.folio.it.api;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.it.InstanceStorageFixtures.createInstanceType;
import static org.folio.utility.RestUtility.CONSORTIUM_CENTRAL_TENANT;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.UUID;
import org.folio.it.BaseIntegrationTest;
import org.folio.rest.jaxrs.model.Subject;
import org.folio.support.ResourcePaths;
import org.folio.support.builders.InstanceRequestBuilder;
import org.folio.utility.RestUtility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code subject_source} is one of {@link BaseIntegrationTest}'s {@code MIGRATION_SEEDED_TABLES}:
 * a Liquibase migration seeds the "folio" row (id {@link #FOLIO_SUBJECT_SOURCE_ID}) once, and it's
 * excluded from per-class table truncation, so it's always present - both to exercise the "cannot
 * touch the folio source" rules against, and as the reason this class doesn't extend the generic
 * {@code BaseReferenceDataIntegrationTest} (whose {@code getCollection_shouldReturn200AndEmptyCollection}
 * test and whole-table {@code @AfterEach} wipe both assume an empty, freely-truncatable table,
 * which would delete that seeded row for every other {@code *IT} class sharing the JVM). Every
 * test here instead creates its own uniquely-named/coded record and queries or acts on it
 * specifically, so nothing needs to assume - or reset - the table's total contents.
 */
class SubjectSourceIT extends BaseIntegrationTest {

  private static final String FOLIO_SUBJECT_SOURCE_ID = "e894d0dc-621d-4b1d-98f6-6f7120eb0d40";

  private static final String NAME_FIELD = "name";
  private static final String CODE_FIELD = "code";
  private static final String SOURCE_FIELD = "source";
  private static final String ID_FIELD = "id";
  private static final String ERRORS_FIELD = "errors";
  private static final String MESSAGE_FIELD = "message";
  private static final String TOTAL_RECORDS_FIELD = "totalRecords";
  private static final String SUBJECT_SOURCES_FIELD = "subjectSources";

  private static final String SOURCE_LOCAL = "local";
  private static final String SOURCE_FOLIO = "folio";
  private static final String SOURCE_CONSORTIUM = "consortium";

  @Test
  @DisplayName("should create a subject source when posting valid data")
  void shouldCreateSubjectSource_whenPostingValidData() {
    var name = randomName();
    var code = randomCode();
    var subjectSource = new JsonObject().put(NAME_FIELD, name).put(CODE_FIELD, code).put(SOURCE_FIELD, SOURCE_LOCAL);

    var response = await(doPost(client, ResourcePaths.SUBJECT_SOURCES, subjectSource));

    assertThat(response.status()).isEqualTo(SC_CREATED);
    var created = response.jsonBody();
    assertThat(created.getString(NAME_FIELD)).isEqualTo(name);
    assertThat(created.getString(CODE_FIELD)).isEqualTo(code);
    assertThat(created.getString(SOURCE_FIELD)).isEqualTo(SOURCE_LOCAL);
    assertThat(created.getString(ID_FIELD)).isNotNull();
  }

  @Test
  @DisplayName("should return a subject source collection matching a name or code query")
  void shouldReturnRecordCollection_whenQueryingByNameOrCode() {
    var name = randomName();
    var code = randomCode();
    var subjectSource = new JsonObject().put(NAME_FIELD, name).put(CODE_FIELD, code).put(SOURCE_FIELD, SOURCE_LOCAL);
    createSubjectSource(subjectSource);

    for (var query : List.of("name==" + name, "code==" + code)) {
      var response = await(doGet(client, ResourcePaths.SUBJECT_SOURCES + "?query=" + query));

      assertThat(response.status()).as("query: %s", query).isEqualTo(SC_OK);
      var collection = response.jsonBody();
      assertThat(collection.getInteger(TOTAL_RECORDS_FIELD)).as("query: %s", query).isEqualTo(1);
      var found = collection.getJsonArray(SUBJECT_SOURCES_FIELD).getJsonObject(0);
      assertThat(found.getString(NAME_FIELD)).isEqualTo(name);
      assertThat(found.getString(CODE_FIELD)).isEqualTo(code);
    }
  }

  @Test
  @DisplayName("should return a subject source by id")
  void shouldReturnRecord_whenGettingById() {
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);
    var id = createSubjectSource(subjectSource).jsonBody().getString(ID_FIELD);

    var response = await(doGet(client, ResourcePaths.SUBJECT_SOURCES + "/" + id));

    assertThat(response.status()).isEqualTo(SC_OK);
    assertThat(response.jsonBody().getString(ID_FIELD)).isEqualTo(id);
  }

  @Test
  @DisplayName("should update a subject source when putting valid data")
  void shouldUpdateSubjectSource_whenPuttingValidData() {
    var id = UUID.randomUUID().toString();
    var subjectSource = new JsonObject().put(ID_FIELD, id).put(NAME_FIELD, randomName())
      .put(SOURCE_FIELD, SOURCE_LOCAL);
    createSubjectSource(subjectSource);
    var updatedName = randomName();

    var response = await(
      doPut(client, ResourcePaths.SUBJECT_SOURCES + "/" + id, subjectSource.put(NAME_FIELD, updatedName)));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    var updated = await(doGet(client, ResourcePaths.SUBJECT_SOURCES + "/" + id)).jsonBody();
    assertThat(updated.getString(NAME_FIELD)).isEqualTo(updatedName);
  }

  @Test
  @DisplayName("should delete a subject source")
  void shouldDeleteSubjectSource_whenDeleting() {
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);
    var id = createSubjectSource(subjectSource).jsonBody().getString(ID_FIELD);

    var response = await(doDelete(client, ResourcePaths.SUBJECT_SOURCES + "/" + id));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    assertThat(await(doGet(client, ResourcePaths.SUBJECT_SOURCES + "/" + id)).status()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  @DisplayName("should fail to create a subject source when the name is a duplicate")
  void shouldFailToCreateSubjectSource_whenNameIsDuplicate() {
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);
    createSubjectSource(subjectSource);

    var response = await(doPost(client, ResourcePaths.SUBJECT_SOURCES, subjectSource));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertThat(errors).hasSize(1);
    assertThat(errors.getJsonObject(0).getString(MESSAGE_FIELD))
      .contains("(jsonb ->> 'name'::text)) value already exists");
  }

  @Test
  @DisplayName("should fail to create a subject source when the code is a duplicate")
  void shouldFailToCreateSubjectSource_whenCodeIsDuplicate() {
    var code = randomCode();
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(CODE_FIELD, code)
      .put(SOURCE_FIELD, SOURCE_LOCAL);
    createSubjectSource(subjectSource);

    var response =
      await(doPost(client, ResourcePaths.SUBJECT_SOURCES, subjectSource.put(NAME_FIELD, randomName())));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertThat(errors).hasSize(1);
    assertThat(errors.getJsonObject(0).getString(MESSAGE_FIELD))
      .contains("(jsonb ->> 'code'::text)) value already exists");
  }

  @Test
  @DisplayName("should fail to create a subject source when the source is folio")
  void shouldFailToCreateSubjectSource_whenSourceIsFolio() {
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_FOLIO);

    var response = await(doPost(client, ResourcePaths.SUBJECT_SOURCES, subjectSource));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertThat(errors).hasSize(1);
    assertThat(errors.getJsonObject(0).getString(MESSAGE_FIELD))
      .isEqualTo("Illegal operation: Source field cannot be set to folio");
  }

  @Test
  @DisplayName("should fail to create a subject source when the source is consortium at a non-consortium tenant")
  void shouldFailToCreateSubjectSource_whenSourceIsConsortiumAtNonConsortiumTenant() {
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_CONSORTIUM);

    var response = await(doPost(client, ResourcePaths.SUBJECT_SOURCES, subjectSource));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertThat(errors).hasSize(1);
    assertThat(errors.getJsonObject(0).getString(MESSAGE_FIELD))
      .isEqualTo("Illegal operation: Source consortium cannot be applied at non-consortium tenant");
  }

  @Test
  @DisplayName("should create a subject source when the source is consortium at the consortium central tenant")
  void shouldCreateSubjectSource_whenSourceIsConsortiumAtConsortiumCentralTenant() {
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_CONSORTIUM);

    var response = await(
      doPost(client, ResourcePaths.SUBJECT_SOURCES, RestUtility.CONSORTIUM_CENTRAL_TENANT, subjectSource));

    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  @Test
  @DisplayName("should return 404 when updating a subject source that does not exist")
  void shouldReturn404_whenUpdatingNonExistingSubjectSource() {
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);
    var id = UUID.randomUUID().toString();

    var response = await(doPut(client, ResourcePaths.SUBJECT_SOURCES + "/" + id, subjectSource));

    assertThat(response.status()).isEqualTo(SC_NOT_FOUND);
    assertThat(response.body()).hasToString("SubjectSource was not found");
  }

  @Test
  @DisplayName("should fail to update the folio subject source")
  void shouldFailToUpdateSubjectSource_whenSourceIsFolio() {
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);

    var response =
      await(doPut(client, ResourcePaths.SUBJECT_SOURCES + "/" + FOLIO_SUBJECT_SOURCE_ID, subjectSource));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertThat(errors).hasSize(1);
    assertThat(errors.getJsonObject(0).getString(MESSAGE_FIELD))
      .isEqualTo("Illegal operation: Source folio cannot be updated");
  }

  @Test
  @DisplayName("should fail to update a subject source when changing its source to folio")
  void shouldFailToUpdateSubjectSource_whenChangingSourceToFolio() {
    var id = UUID.randomUUID().toString();
    var subjectSource = new JsonObject().put(ID_FIELD, id).put(NAME_FIELD, randomName())
      .put(SOURCE_FIELD, SOURCE_LOCAL);
    createSubjectSource(subjectSource);

    var response = await(doPut(client, ResourcePaths.SUBJECT_SOURCES + "/" + id,
      subjectSource.put(SOURCE_FIELD, SOURCE_FOLIO)));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertThat(errors).hasSize(1);
    assertThat(errors.getJsonObject(0).getString(MESSAGE_FIELD))
      .isEqualTo("Illegal operation: Source field cannot be set to folio");
  }

  @Test
  @DisplayName("should fail to update a subject source when changing its source to consortium at a "
    + "non-consortium tenant")
  void shouldFailToUpdateSubjectSource_whenChangingSourceToConsortiumAtNonConsortiumTenant() {
    var id = UUID.randomUUID().toString();
    var subjectSource = new JsonObject().put(ID_FIELD, id).put(NAME_FIELD, randomName())
      .put(SOURCE_FIELD, SOURCE_LOCAL);
    createSubjectSource(subjectSource);

    var response = await(doPut(client, ResourcePaths.SUBJECT_SOURCES + "/" + id,
      subjectSource.put(SOURCE_FIELD, SOURCE_CONSORTIUM)));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertThat(errors).hasSize(1);
    assertThat(errors.getJsonObject(0).getString(MESSAGE_FIELD))
      .isEqualTo("Illegal operation: Source field cannot be updated at non-consortium tenant");
  }

  @Test
  @DisplayName("should update a subject source when changing its source to consortium at the consortium "
    + "central tenant")
  void shouldUpdateSubjectSource_whenChangingSourceToConsortiumAtConsortiumCentralTenant() {
    var id = UUID.randomUUID().toString();
    var subjectSource = new JsonObject().put(ID_FIELD, id).put(NAME_FIELD, randomName())
      .put(SOURCE_FIELD, SOURCE_LOCAL);
    await(doPost(client, ResourcePaths.SUBJECT_SOURCES, CONSORTIUM_CENTRAL_TENANT, subjectSource));

    var response =
      await(doPut(client, ResourcePaths.SUBJECT_SOURCES + "/" + id, RestUtility.CONSORTIUM_CENTRAL_TENANT,
        subjectSource.put(SOURCE_FIELD, SOURCE_CONSORTIUM)));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should update a subject source when changing its source to local at the consortium central tenant")
  void shouldUpdateSubjectSource_whenChangingSourceToLocalAtConsortiumCentralTenant() {
    var id = UUID.randomUUID().toString();
    var subjectSource = new JsonObject().put(ID_FIELD, id).put(NAME_FIELD, randomName())
      .put(SOURCE_FIELD, SOURCE_CONSORTIUM);
    await(doPost(client, ResourcePaths.SUBJECT_SOURCES, CONSORTIUM_CENTRAL_TENANT, subjectSource));

    var response =
      await(doPut(client, ResourcePaths.SUBJECT_SOURCES + "/" + id, RestUtility.CONSORTIUM_CENTRAL_TENANT,
        subjectSource.put(SOURCE_FIELD, SOURCE_LOCAL)));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should fail to delete a subject source when it is linked to an instance")
  void shouldFailToDeleteSubjectSource_whenLinkedToInstance() {
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);
    var subjectSourceId = createSubjectSource(subjectSource).jsonBody().getString(ID_FIELD);
    createInstanceWithSubject(subjectSourceId, randomSubjectTypeId());

    var response = await(doDelete(client, ResourcePaths.SUBJECT_SOURCES + "/" + subjectSourceId));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains("id is still referenced from table instance_subject_source");
  }

  private TestResponse createSubjectSource(JsonObject subjectSource) {
    return await(doPost(client, ResourcePaths.SUBJECT_SOURCES, subjectSource));
  }

  private static String randomName() {
    return "a subject source " + UUID.randomUUID();
  }

  private static String randomCode() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private static String randomSubjectTypeId() {
    var subjectType = new JsonObject().put(NAME_FIELD, "a subject type " + UUID.randomUUID())
      .put(SOURCE_FIELD, SOURCE_LOCAL);
    return await(doPost(client, ResourcePaths.SUBJECT_TYPES, subjectType)).jsonBody().getString(ID_FIELD);
  }

  private static void createInstanceWithSubject(String subjectSourceId, String subjectTypeId) {
    var instanceTypeId = createInstanceType(client);
    var subject = new Subject().withSourceId(subjectSourceId).withTypeId(subjectTypeId).withValue("a subject");
    var instance = new InstanceRequestBuilder()
      .withTitle("an instance").withSource("TEST").withInstanceTypeId(UUID.fromString(instanceTypeId))
      .create()
      .put("subjects", JsonArray.of(pojo2JsonObject(subject)));

    await(doPost(client, ResourcePaths.INSTANCES, instance));
  }
}
