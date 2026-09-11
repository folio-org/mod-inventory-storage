package org.folio.it.api;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.it.InstanceStorageFixtures.createInstanceType;
import static org.folio.utility.RestUtility.CONSORTIUM_CENTRAL_TENANT;
import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.UUID;
import org.folio.it.BaseIntegrationTest;
import org.folio.rest.jaxrs.model.Subject;
import org.folio.support.ResourcePaths;
import org.folio.support.builders.InstanceRequestBuilder;
import org.folio.support.extension.EnableTenant;
import org.folio.utility.RestUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The migration-seeded "folio" subject type row (id {@link #FOLIO_SUBJECT_TYPE_ID}) is excluded
 * from {@link BaseIntegrationTest}'s per-class table truncation (see
 * {@code MIGRATION_SEEDED_TABLES}), so it's always present to exercise the "cannot touch the
 * folio type" rules against.
 */
@EnableTenant(tenants = {TENANT_ID, CONSORTIUM_CENTRAL_TENANT})
class SubjectTypeIT extends BaseIntegrationTest {

  private static final String FOLIO_SUBJECT_TYPE_ID = "d6488f88-1e74-40ce-81b5-b19a928ff5b1";

  private static final String NAME_FIELD = "name";
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
  @DisplayName("should fail to create a subject type when the name is a duplicate")
  void shouldFailToCreateSubjectType_whenNameIsDuplicate() {
    var subjectType = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);
    createSubjectType(subjectType);

    var response = await(doPost(client, ResourcePaths.SUBJECT_TYPES, subjectType));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    assertThat(response.jsonBody().getJsonArray(ERRORS_FIELD)).hasSize(1);
  }

  @Test
  @DisplayName("should fail to create a subject type when the source is folio")
  void shouldFailToCreateSubjectType_whenSourceIsFolio() {
    var subjectType = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_FOLIO);

    var response = await(doPost(client, ResourcePaths.SUBJECT_TYPES, subjectType));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertThat(errors).hasSize(1);
    assertThat(errors.getJsonObject(0).getString(MESSAGE_FIELD))
      .isEqualTo("Illegal operation: Source field cannot be set to folio");
  }

  @Test
  @DisplayName("should fail to create a subject type when the source is consortium at a non-consortium tenant")
  void shouldFailToCreateSubjectType_whenSourceIsConsortiumAtNonConsortiumTenant() {
    var subjectType = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_CONSORTIUM);

    var response = await(doPost(client, ResourcePaths.SUBJECT_TYPES, subjectType));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertThat(errors).hasSize(1);
    assertThat(errors.getJsonObject(0).getString(MESSAGE_FIELD))
      .isEqualTo("Illegal operation: Source consortium cannot be applied at non-consortium tenant");
  }

  @Test
  @DisplayName("should create a subject type when the source is consortium at the consortium central tenant")
  void shouldCreateSubjectType_whenSourceIsConsortiumAtConsortiumCentralTenant() {
    var subjectType = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_CONSORTIUM);

    var response = await(
      doPost(client, ResourcePaths.SUBJECT_TYPES, RestUtility.CONSORTIUM_CENTRAL_TENANT, subjectType));

    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  @Test
  @DisplayName("should return 404 when updating a subject type that does not exist")
  void shouldReturn404_whenUpdatingNonExistingSubjectType() {
    var subjectType = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);
    var id = UUID.randomUUID().toString();

    var response = await(doPut(client, ResourcePaths.SUBJECT_TYPES + "/" + id, subjectType));

    assertThat(response.status()).isEqualTo(SC_NOT_FOUND);
    assertThat(response.body().toString()).isEqualTo("SubjectType was not found");
  }

  @Test
  @DisplayName("should fail to update the folio subject type")
  void shouldFailToUpdateSubjectType_whenSourceIsFolio() {
    var subjectType = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);

    var response = await(doPut(client, ResourcePaths.SUBJECT_TYPES + "/" + FOLIO_SUBJECT_TYPE_ID, subjectType));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertThat(errors).hasSize(1);
    assertThat(errors.getJsonObject(0).getString(MESSAGE_FIELD))
      .isEqualTo("Illegal operation: Source folio cannot be updated");
  }

  @Test
  @DisplayName("should fail to update a subject type when changing its source to folio")
  void shouldFailToUpdateSubjectType_whenChangingSourceToFolio() {
    var id = UUID.randomUUID().toString();
    var subjectType = new JsonObject().put(ID_FIELD, id).put(NAME_FIELD, randomName())
      .put(SOURCE_FIELD, SOURCE_LOCAL);
    createSubjectType(subjectType);

    var response = await(doPut(client, ResourcePaths.SUBJECT_TYPES + "/" + id,
      subjectType.put(SOURCE_FIELD, SOURCE_FOLIO)));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertThat(errors).hasSize(1);
    assertThat(errors.getJsonObject(0).getString(MESSAGE_FIELD))
      .isEqualTo("Illegal operation: Source field cannot be set to folio");
  }

  @Test
  @DisplayName("should fail to update a subject type when changing its source to consortium at a "
               + "non-consortium tenant")
  void shouldFailToUpdateSubjectType_whenChangingSourceToConsortiumAtNonConsortiumTenant() {
    var id = UUID.randomUUID().toString();
    var subjectType = new JsonObject().put(ID_FIELD, id).put(NAME_FIELD, randomName())
      .put(SOURCE_FIELD, SOURCE_LOCAL);
    createSubjectType(subjectType);

    var response = await(doPut(client, ResourcePaths.SUBJECT_TYPES + "/" + id,
      subjectType.put(SOURCE_FIELD, SOURCE_CONSORTIUM)));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertThat(errors).hasSize(1);
    assertThat(errors.getJsonObject(0).getString(MESSAGE_FIELD))
      .isEqualTo("Illegal operation: Source field cannot be updated at non-consortium tenant");
  }

  @Test
  @DisplayName("should update a subject type when changing its source to consortium at the consortium "
               + "central tenant")
  void shouldUpdateSubjectType_whenChangingSourceToConsortiumAtConsortiumCentralTenant() {
    var id = UUID.randomUUID().toString();
    var subjectType = new JsonObject().put(ID_FIELD, id).put(NAME_FIELD, randomName())
      .put(SOURCE_FIELD, SOURCE_LOCAL);
    await(doPost(client, ResourcePaths.SUBJECT_TYPES, CONSORTIUM_CENTRAL_TENANT, subjectType));

    var response =
      await(doPut(client, ResourcePaths.SUBJECT_TYPES + "/" + id, RestUtility.CONSORTIUM_CENTRAL_TENANT,
        subjectType.put(SOURCE_FIELD, SOURCE_CONSORTIUM)));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should update a subject type when changing its source to local at the consortium central tenant")
  void shouldUpdateSubjectType_whenChangingSourceToLocalAtConsortiumCentralTenant() {
    var id = UUID.randomUUID().toString();
    var subjectType = new JsonObject().put(ID_FIELD, id).put(NAME_FIELD, randomName())
      .put(SOURCE_FIELD, SOURCE_CONSORTIUM);
    await(doPost(client, ResourcePaths.SUBJECT_TYPES, CONSORTIUM_CENTRAL_TENANT, subjectType));

    var response =
      await(doPut(client, ResourcePaths.SUBJECT_TYPES + "/" + id, RestUtility.CONSORTIUM_CENTRAL_TENANT,
        subjectType.put(SOURCE_FIELD, SOURCE_LOCAL)));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should fail to delete a subject type when it is linked to an instance")
  void shouldFailToDeleteSubjectType_whenLinkedToInstance() {
    var subjectType = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);
    var subjectTypeId = createSubjectType(subjectType).jsonBody().getString(ID_FIELD);
    var instanceId = createInstanceWithSubject(randomSubjectSourceId(), subjectTypeId);

    var response = await(doDelete(client, ResourcePaths.SUBJECT_TYPES + "/" + subjectTypeId));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains("id is still referenced from table instance_subject_type");

    await(doDelete(client, ResourcePaths.INSTANCES + "/" + instanceId));
  }

  @Test
  @DisplayName("should clear the link between a subject type and an instance when the instance is deleted")
  void shouldClearLinkBetweenSubjectTypeAndInstance_whenInstanceIsDeleted() {
    var subjectType = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);
    var subjectTypeId = createSubjectType(subjectType).jsonBody().getString(ID_FIELD);
    var instanceId = createInstanceWithSubject(randomSubjectSourceId(), subjectTypeId);

    var response = await(doDelete(client, ResourcePaths.INSTANCES + "/" + instanceId));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
  }

  private TestResponse createSubjectType(JsonObject subjectType) {
    return await(doPost(client, ResourcePaths.SUBJECT_TYPES, subjectType));
  }

  private static String randomName() {
    return "a subject type " + UUID.randomUUID();
  }

  private static String randomSubjectSourceId() {
    var subjectSource = new JsonObject().put(NAME_FIELD, "a subject source " + UUID.randomUUID())
      .put(SOURCE_FIELD, SOURCE_LOCAL);
    return await(doPost(client, ResourcePaths.SUBJECT_SOURCES, subjectSource)).jsonBody().getString(ID_FIELD);
  }

  private static String createInstanceWithSubject(String subjectSourceId, String subjectTypeId) {
    var instanceTypeId = createInstanceType(client);
    var subject = new Subject().withSourceId(subjectSourceId).withTypeId(subjectTypeId).withValue("a subject");
    var instance = new InstanceRequestBuilder()
      .withTitle("an instance").withSource("TEST").withInstanceTypeId(UUID.fromString(instanceTypeId))
      .create()
      .put("subjects", JsonArray.of(pojo2JsonObject(subject)));

    return await(doPost(client, ResourcePaths.INSTANCES, instance)).jsonBody().getString(ID_FIELD);
  }
}
