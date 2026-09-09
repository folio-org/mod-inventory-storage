package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstanceType;
import static org.folio.utility.RestUtility.CONSORTIUM_CENTRAL_TENANT;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.UUID;
import org.folio.rest.jaxrs.model.Subject;
import org.folio.rest.support.builders.InstanceRequestBuilder;
import org.folio.rest.support.extension.EnableTenant;
import org.junit.jupiter.api.BeforeEach;
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
  void cannotCreateSubjectTypeWithDuplicateName() {
    var subjectType = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);
    createSubjectType(subjectType);

    var response = get(doPost(client, ResourcePaths.SUBJECT_TYPES, subjectType));

    assertEquals(SC_UNPROCESSABLE_ENTITY, response.status());
    assertEquals(1, response.jsonBody().getJsonArray(ERRORS_FIELD).size());
  }

  @Test
  void cannotCreateSubjectTypeWithSourceFolio() {
    var subjectType = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_FOLIO);

    var response = get(doPost(client, ResourcePaths.SUBJECT_TYPES, subjectType));

    assertEquals(SC_UNPROCESSABLE_ENTITY, response.status());
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertEquals(1, errors.size());
    assertEquals("Illegal operation: Source field cannot be set to folio",
      errors.getJsonObject(0).getString(MESSAGE_FIELD));
  }

  @Test
  void cannotCreateSubjectTypeWithSourceConsortiumAtNonEcs() {
    var subjectType = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_CONSORTIUM);

    var response = get(doPost(client, ResourcePaths.SUBJECT_TYPES, subjectType));

    assertEquals(SC_UNPROCESSABLE_ENTITY, response.status());
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertEquals(1, errors.size());
    assertEquals("Illegal operation: Source consortium cannot be applied at non-consortium tenant",
      errors.getJsonObject(0).getString(MESSAGE_FIELD));
  }

  @Test
  void canCreateSubjectTypeWithSourceConsortiumAtEcs() {
    var subjectType = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_CONSORTIUM);

    var response = get(doPost(client, ResourcePaths.SUBJECT_TYPES, CONSORTIUM_CENTRAL_TENANT, subjectType));

    assertEquals(SC_CREATED, response.status());
  }

  @Test
  void cannotUpdateNonExistingSubjectType() {
    var subjectType = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);
    var id = UUID.randomUUID().toString();

    var response = get(doPut(client, ResourcePaths.SUBJECT_TYPES + "/" + id, subjectType));

    assertEquals(SC_NOT_FOUND, response.status());
    assertEquals("SubjectType was not found", response.body().toString());
  }

  @Test
  void cannotUpdateSubjectTypeWithSourceFolio() {
    var subjectType = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);

    var response = get(doPut(client, ResourcePaths.SUBJECT_TYPES + "/" + FOLIO_SUBJECT_TYPE_ID, subjectType));

    assertEquals(SC_UNPROCESSABLE_ENTITY, response.status());
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertEquals(1, errors.size());
    assertEquals("Illegal operation: Source folio cannot be updated", errors.getJsonObject(0).getString(MESSAGE_FIELD));
  }

  @Test
  void cannotUpdateSubjectTypeToFolio() {
    var id = UUID.randomUUID().toString();
    var subjectType = new JsonObject().put(ID_FIELD, id).put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);
    createSubjectType(subjectType);

    var response = get(doPut(client, ResourcePaths.SUBJECT_TYPES + "/" + id,
      subjectType.put(SOURCE_FIELD, SOURCE_FOLIO)));

    assertEquals(SC_UNPROCESSABLE_ENTITY, response.status());
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertEquals(1, errors.size());
    assertEquals("Illegal operation: Source field cannot be set to folio",
      errors.getJsonObject(0).getString(MESSAGE_FIELD));
  }

  @Test
  void cannotUpdateSubjectTypeToConsortiumAtNonEcs() {
    var id = UUID.randomUUID().toString();
    var subjectType = new JsonObject().put(ID_FIELD, id).put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);
    createSubjectType(subjectType);

    var response = get(doPut(client, ResourcePaths.SUBJECT_TYPES + "/" + id,
      subjectType.put(SOURCE_FIELD, SOURCE_CONSORTIUM)));

    assertEquals(SC_UNPROCESSABLE_ENTITY, response.status());
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertEquals(1, errors.size());
    assertEquals("Illegal operation: Source field cannot be updated at non-consortium tenant",
      errors.getJsonObject(0).getString(MESSAGE_FIELD));
  }

  @Test
  void canUpdateSubjectTypeToConsortiumAtEcs() {
    var id = UUID.randomUUID().toString();
    var subjectType = new JsonObject().put(ID_FIELD, id).put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);
    get(doPost(client, ResourcePaths.SUBJECT_TYPES, CONSORTIUM_CENTRAL_TENANT, subjectType));

    var response = get(doPut(client, ResourcePaths.SUBJECT_TYPES + "/" + id, CONSORTIUM_CENTRAL_TENANT,
      subjectType.put(SOURCE_FIELD, SOURCE_CONSORTIUM)));

    assertEquals(SC_NO_CONTENT, response.status());
  }

  @Test
  void canUpdateSubjectTypeToLocalAtEcs() {
    var id = UUID.randomUUID().toString();
    var subjectType = new JsonObject().put(ID_FIELD, id).put(NAME_FIELD, randomName())
      .put(SOURCE_FIELD, SOURCE_CONSORTIUM);
    get(doPost(client, ResourcePaths.SUBJECT_TYPES, CONSORTIUM_CENTRAL_TENANT, subjectType));

    var response = get(doPut(client, ResourcePaths.SUBJECT_TYPES + "/" + id, CONSORTIUM_CENTRAL_TENANT,
      subjectType.put(SOURCE_FIELD, SOURCE_LOCAL)));

    assertEquals(SC_NO_CONTENT, response.status());
  }

  @Test
  void cannotDeleteSubjectTypeLinkedToInstance() {
    var subjectType = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);
    var subjectTypeId = createSubjectType(subjectType).jsonBody().getString(ID_FIELD);
    var instanceId = createInstanceWithSubject(randomSubjectSourceId(), subjectTypeId);

    var response = get(doDelete(client, ResourcePaths.SUBJECT_TYPES + "/" + subjectTypeId));

    assertEquals(SC_BAD_REQUEST, response.status());
    assertTrue(response.body().toString().contains("id is still referenced from table instance_subject_type"));

    get(doDelete(client, ResourcePaths.INSTANCES + "/" + instanceId));
  }

  @Test
  void clearLinksBetweenSubjectTypeAndInstance() {
    var subjectType = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);
    var subjectTypeId = createSubjectType(subjectType).jsonBody().getString(ID_FIELD);
    var instanceId = createInstanceWithSubject(randomSubjectSourceId(), subjectTypeId);

    var response = get(doDelete(client, ResourcePaths.INSTANCES + "/" + instanceId));

    assertEquals(SC_NO_CONTENT, response.status());
  }

  private TestResponse createSubjectType(JsonObject subjectType) {
    return get(doPost(client, ResourcePaths.SUBJECT_TYPES, subjectType));
  }

  private static String randomName() {
    return "a subject type " + UUID.randomUUID();
  }

  private static String randomSubjectSourceId() {
    var subjectSource = new JsonObject().put(NAME_FIELD, "a subject source " + UUID.randomUUID())
      .put(SOURCE_FIELD, SOURCE_LOCAL);
    return get(doPost(client, ResourcePaths.SUBJECT_SOURCES, subjectSource)).jsonBody().getString(ID_FIELD);
  }

  private static String createInstanceWithSubject(String subjectSourceId, String subjectTypeId) {
    var instanceTypeId = createInstanceType(client);
    var subject = new Subject().withSourceId(subjectSourceId).withTypeId(subjectTypeId).withValue("a subject");
    var instance = new InstanceRequestBuilder()
      .withTitle("an instance").withSource("TEST").withInstanceTypeId(UUID.fromString(instanceTypeId))
      .create()
      .put("subjects", JsonArray.of(pojo2JsonObject(subject)));

    return get(doPost(client, ResourcePaths.INSTANCES, instance)).jsonBody().getString(ID_FIELD);
  }
}
