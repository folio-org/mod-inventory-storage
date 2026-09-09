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
  void cannotCreateSubjectSourceWithDuplicateName() {
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);
    createSubjectSource(subjectSource);

    var response = get(doPost(client, ResourcePaths.SUBJECT_SOURCES, subjectSource));

    assertEquals(SC_UNPROCESSABLE_ENTITY, response.status());
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertEquals(1, errors.size());
    assertTrue(errors.getJsonObject(0).getString(MESSAGE_FIELD)
      .contains("(jsonb ->> 'name'::text)) value already exists"));
  }

  @Test
  void cannotCreateSubjectSourceWithDuplicateCode() {
    var code = UUID.randomUUID().toString().substring(0, 8);
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(CODE_FIELD, code)
      .put(SOURCE_FIELD, SOURCE_LOCAL);
    createSubjectSource(subjectSource);

    var response = get(doPost(client, ResourcePaths.SUBJECT_SOURCES, subjectSource.put(NAME_FIELD, randomName())));

    assertEquals(SC_UNPROCESSABLE_ENTITY, response.status());
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertEquals(1, errors.size());
    assertTrue(errors.getJsonObject(0).getString(MESSAGE_FIELD)
      .contains("(jsonb ->> 'code'::text)) value already exists"));
  }

  @Test
  void cannotCreateSubjectSourceWithSourceFolio() {
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_FOLIO);

    var response = get(doPost(client, ResourcePaths.SUBJECT_SOURCES, subjectSource));

    assertEquals(SC_UNPROCESSABLE_ENTITY, response.status());
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertEquals(1, errors.size());
    assertEquals("Illegal operation: Source field cannot be set to folio",
      errors.getJsonObject(0).getString(MESSAGE_FIELD));
  }

  @Test
  void cannotCreateSubjectSourceWithSourceConsortiumAtNonEcs() {
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_CONSORTIUM);

    var response = get(doPost(client, ResourcePaths.SUBJECT_SOURCES, subjectSource));

    assertEquals(SC_UNPROCESSABLE_ENTITY, response.status());
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertEquals(1, errors.size());
    assertEquals("Illegal operation: Source consortium cannot be applied at non-consortium tenant",
      errors.getJsonObject(0).getString(MESSAGE_FIELD));
  }

  @Test
  void canCreateSubjectSourceWithSourceConsortiumAtEcs() {
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_CONSORTIUM);

    var response = get(doPost(client, ResourcePaths.SUBJECT_SOURCES, CONSORTIUM_CENTRAL_TENANT, subjectSource));

    assertEquals(SC_CREATED, response.status());
  }

  @Test
  void cannotUpdateNonExistingSubjectSource() {
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);
    var id = UUID.randomUUID().toString();

    var response = get(doPut(client, ResourcePaths.SUBJECT_SOURCES + "/" + id, subjectSource));

    assertEquals(SC_NOT_FOUND, response.status());
    assertEquals("SubjectSource was not found", response.body().toString());
  }

  @Test
  void cannotUpdateSubjectSourceWithSourceFolio() {
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);

    var response = get(doPut(client, ResourcePaths.SUBJECT_SOURCES + "/" + FOLIO_SUBJECT_SOURCE_ID, subjectSource));

    assertEquals(SC_UNPROCESSABLE_ENTITY, response.status());
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertEquals(1, errors.size());
    assertEquals("Illegal operation: Source folio cannot be updated", errors.getJsonObject(0).getString(MESSAGE_FIELD));
  }

  @Test
  void cannotUpdateSubjectSourceToFolio() {
    var id = UUID.randomUUID().toString();
    var subjectSource = new JsonObject().put(ID_FIELD, id).put(NAME_FIELD, randomName())
      .put(SOURCE_FIELD, SOURCE_LOCAL);
    createSubjectSource(subjectSource);

    var response = get(doPut(client, ResourcePaths.SUBJECT_SOURCES + "/" + id,
      subjectSource.put(SOURCE_FIELD, SOURCE_FOLIO)));

    assertEquals(SC_UNPROCESSABLE_ENTITY, response.status());
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertEquals(1, errors.size());
    assertEquals("Illegal operation: Source field cannot be set to folio",
      errors.getJsonObject(0).getString(MESSAGE_FIELD));
  }

  @Test
  void cannotUpdateSubjectSourceToConsortiumAtNonEcs() {
    var id = UUID.randomUUID().toString();
    var subjectSource = new JsonObject().put(ID_FIELD, id).put(NAME_FIELD, randomName())
      .put(SOURCE_FIELD, SOURCE_LOCAL);
    createSubjectSource(subjectSource);

    var response = get(doPut(client, ResourcePaths.SUBJECT_SOURCES + "/" + id,
      subjectSource.put(SOURCE_FIELD, SOURCE_CONSORTIUM)));

    assertEquals(SC_UNPROCESSABLE_ENTITY, response.status());
    var errors = response.jsonBody().getJsonArray(ERRORS_FIELD);
    assertEquals(1, errors.size());
    assertEquals("Illegal operation: Source field cannot be updated at non-consortium tenant",
      errors.getJsonObject(0).getString(MESSAGE_FIELD));
  }

  @Test
  void canUpdateSubjectSourceToConsortiumAtEcs() {
    var id = UUID.randomUUID().toString();
    var subjectSource = new JsonObject().put(ID_FIELD, id).put(NAME_FIELD, randomName())
      .put(SOURCE_FIELD, SOURCE_LOCAL);
    get(doPost(client, ResourcePaths.SUBJECT_SOURCES, CONSORTIUM_CENTRAL_TENANT, subjectSource));

    var response = get(doPut(client, ResourcePaths.SUBJECT_SOURCES + "/" + id, CONSORTIUM_CENTRAL_TENANT,
      subjectSource.put(SOURCE_FIELD, SOURCE_CONSORTIUM)));

    assertEquals(SC_NO_CONTENT, response.status());
  }

  @Test
  void canUpdateSubjectSourceToLocalAtEcs() {
    var id = UUID.randomUUID().toString();
    var subjectSource = new JsonObject().put(ID_FIELD, id).put(NAME_FIELD, randomName())
      .put(SOURCE_FIELD, SOURCE_CONSORTIUM);
    get(doPost(client, ResourcePaths.SUBJECT_SOURCES, CONSORTIUM_CENTRAL_TENANT, subjectSource));

    var response = get(doPut(client, ResourcePaths.SUBJECT_SOURCES + "/" + id, CONSORTIUM_CENTRAL_TENANT,
      subjectSource.put(SOURCE_FIELD, SOURCE_LOCAL)));

    assertEquals(SC_NO_CONTENT, response.status());
  }

  @Test
  void cannotDeleteSubjectSourceLinkedToInstance() {
    var subjectSource = new JsonObject().put(NAME_FIELD, randomName()).put(SOURCE_FIELD, SOURCE_LOCAL);
    var subjectSourceId = createSubjectSource(subjectSource).jsonBody().getString(ID_FIELD);
    createInstanceWithSubject(subjectSourceId, randomSubjectTypeId());

    var response = get(doDelete(client, ResourcePaths.SUBJECT_SOURCES + "/" + subjectSourceId));

    assertEquals(SC_BAD_REQUEST, response.status());
    assertTrue(response.body().toString().contains("id is still referenced from table instance_subject_source"));
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
