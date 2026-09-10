package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstanceType;
import static org.folio.utility.RestUtility.CONSORTIUM_CENTRAL_TENANT;
import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.UUID;
import org.folio.rest.jaxrs.model.Subject;
import org.folio.rest.support.builders.InstanceRequestBuilder;
import org.folio.rest.support.extension.EnableTenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The migration-seeded "folio" subject source row (id {@link #FOLIO_SUBJECT_SOURCE_ID}) is
 * excluded from {@link BaseIntegrationTest}'s per-class table truncation (see
 * {@code MIGRATION_SEEDED_TABLES}), so it's always present to exercise the "cannot touch the
 * folio source" rules against.
 */
@EnableTenant(tenants = {TENANT_ID, CONSORTIUM_CENTRAL_TENANT})
class SubjectSourceIT extends BaseIntegrationTest {

  private static final String FOLIO_SUBJECT_SOURCE_ID = "e894d0dc-621d-4b1d-98f6-6f7120eb0d40";

  private static final String NAME_FIELD = "name";
  private static final String CODE_FIELD = "code";
  private static final String SOURCE_FIELD = "source";
  private static final String ID_FIELD = "id";
  private static final String ERRORS_FIELD = "errors";
  private static final String MESSAGE_FIELD = "message";

  private static final String SOURCE_LOCAL = "local";
  private static final String SOURCE_FOLIO = "folio";
  private static final String SOURCE_CONSORTIUM = "consortium";

  @BeforeEach
  void mockConsortiumMembership() {
    mockUserTenantsForConsortiumMember(CONSORTIUM_CENTRAL_TENANT);
    mockConsortiumTenants();
  }

  @Test
  @DisplayName("should fail to create a subject source when the name is a duplicate")
  void shouldFailToCreateSubjectSource_whenNameIsDuplicate() {
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);
    createSubjectSource(subjectSource);

    var response = get(doPost(client, ResourcePaths.SUBJECT_SOURCES, subjectSource));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertThat(errors).hasSize(1);
    assertThat(errors.getJsonObject(0).getString(MESSAGE_FIELD))
      .contains("(jsonb ->> 'name'::text)) value already exists");
  }

  @Test
  @DisplayName("should fail to create a subject source when the code is a duplicate")
  void shouldFailToCreateSubjectSource_whenCodeIsDuplicate() {
    var code = UUID.randomUUID().toString().substring(0, 8);
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(CODE_FIELD, code)
      .put(SOURCE_FIELD, SOURCE_LOCAL);
    createSubjectSource(subjectSource);

    var response = get(doPost(client, ResourcePaths.SUBJECT_SOURCES, subjectSource.put(NAME_FIELD, randomName())));

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

    var response = get(doPost(client, ResourcePaths.SUBJECT_SOURCES, subjectSource));

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

    var response = get(doPost(client, ResourcePaths.SUBJECT_SOURCES, subjectSource));

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

    var response = get(doPost(client, ResourcePaths.SUBJECT_SOURCES, CONSORTIUM_CENTRAL_TENANT, subjectSource));

    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  @Test
  @DisplayName("should return 404 when updating a subject source that does not exist")
  void shouldReturn404_whenUpdatingNonExistingSubjectSource() {
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);
    var id = UUID.randomUUID().toString();

    var response = get(doPut(client, ResourcePaths.SUBJECT_SOURCES + "/" + id, subjectSource));

    assertThat(response.status()).isEqualTo(SC_NOT_FOUND);
    assertThat(response.body().toString()).isEqualTo("SubjectSource was not found");
  }

  @Test
  @DisplayName("should fail to update the folio subject source")
  void shouldFailToUpdateSubjectSource_whenSourceIsFolio() {
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);

    var response = get(doPut(client, ResourcePaths.SUBJECT_SOURCES + "/" + FOLIO_SUBJECT_SOURCE_ID, subjectSource));

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

    var response = get(doPut(client, ResourcePaths.SUBJECT_SOURCES + "/" + id,
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

    var response = get(doPut(client, ResourcePaths.SUBJECT_SOURCES + "/" + id,
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
    get(doPost(client, ResourcePaths.SUBJECT_SOURCES, CONSORTIUM_CENTRAL_TENANT, subjectSource));

    var response = get(doPut(client, ResourcePaths.SUBJECT_SOURCES + "/" + id, CONSORTIUM_CENTRAL_TENANT,
      subjectSource.put(SOURCE_FIELD, SOURCE_CONSORTIUM)));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should update a subject source when changing its source to local at the consortium central tenant")
  void shouldUpdateSubjectSource_whenChangingSourceToLocalAtConsortiumCentralTenant() {
    var id = UUID.randomUUID().toString();
    var subjectSource = new JsonObject().put(ID_FIELD, id).put(NAME_FIELD, randomName())
      .put(SOURCE_FIELD, SOURCE_CONSORTIUM);
    get(doPost(client, ResourcePaths.SUBJECT_SOURCES, CONSORTIUM_CENTRAL_TENANT, subjectSource));

    var response = get(doPut(client, ResourcePaths.SUBJECT_SOURCES + "/" + id, CONSORTIUM_CENTRAL_TENANT,
      subjectSource.put(SOURCE_FIELD, SOURCE_LOCAL)));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should fail to delete a subject source when it is linked to an instance")
  void shouldFailToDeleteSubjectSource_whenLinkedToInstance() {
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);
    var subjectSourceId = createSubjectSource(subjectSource).jsonBody().getString(ID_FIELD);
    createInstanceWithSubject(subjectSourceId, randomSubjectTypeId());

    var response = get(doDelete(client, ResourcePaths.SUBJECT_SOURCES + "/" + subjectSourceId));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains("id is still referenced from table instance_subject_source");
  }

  private TestResponse createSubjectSource(JsonObject subjectSource) {
    return get(doPost(client, ResourcePaths.SUBJECT_SOURCES, subjectSource));
  }

  private static String randomName() {
    return "a subject source " + UUID.randomUUID();
  }

  private static String randomSubjectTypeId() {
    var subjectType = new JsonObject().put(NAME_FIELD, "a subject type " + UUID.randomUUID())
      .put(SOURCE_FIELD, SOURCE_LOCAL);
    return get(doPost(client, ResourcePaths.SUBJECT_TYPES, subjectType)).jsonBody().getString(ID_FIELD);
  }

  private static void createInstanceWithSubject(String subjectSourceId, String subjectTypeId) {
    var instanceTypeId = createInstanceType(client);
    var subject = new Subject().withSourceId(subjectSourceId).withTypeId(subjectTypeId).withValue("a subject");
    var instance = new InstanceRequestBuilder()
      .withTitle("an instance").withSource("TEST").withInstanceTypeId(UUID.fromString(instanceTypeId))
      .create()
      .put("subjects", JsonArray.of(pojo2JsonObject(subject)));

    get(doPost(client, ResourcePaths.INSTANCES, instance));
  }
}
