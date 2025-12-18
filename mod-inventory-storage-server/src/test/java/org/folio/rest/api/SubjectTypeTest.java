package org.folio.rest.api;

import static org.folio.rest.api.InstanceStorageTest.SUBJECTS_KEY;
import static org.folio.rest.support.http.InterfaceUrls.subjectTypesUrl;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.ModuleUtility.prepareTenant;
import static org.folio.utility.ModuleUtility.removeTenant;
import static org.folio.utility.RestUtility.CONSORTIUM_CENTRAL_TENANT;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import junitparams.JUnitParamsRunner;
import lombok.SneakyThrows;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Subject;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.http.ResourceClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class SubjectTypeTest extends TestBaseWithInventoryUtil {
  private static ResourceClient subjectTypeClient;
  private static final String SUBJECT_TYPE_ID = "d6488f88-1e74-40ce-81b5-b19a928ff5b1";

  @SneakyThrows
  @BeforeClass
  public static void before() {
    subjectTypeClient = ResourceClient.forSubjectTypes(getClient());
    prepareTenant(CONSORTIUM_CENTRAL_TENANT, false);

    mockUserTenantsForNonConsortiumMember();
    mockUserTenantsForConsortiumMember(CONSORTIUM_CENTRAL_TENANT);
    mockConsortiumTenants();
  }

  @SneakyThrows
  @AfterClass
  public static void afterClass() {
    removeTenant(CONSORTIUM_CENTRAL_TENANT);
  }

  @Test
  public void cannotCreateSubjectTypeWithDuplicateName()
    throws InterruptedException, TimeoutException,
    ExecutionException {

    JsonObject subjectType = new JsonObject()
      .put("name", "Topical name")
      .put("source", "local");

    subjectTypeClient.create(subjectType);

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();
    getClient().post(subjectTypesUrl(""), subjectType, TENANT_ID, ResponseHandler.json(postCompleted));

    Response response = postCompleted.get(TIMEOUT, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(422));

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertThat(errors.size(), is(1));
  }

  @Test
  public void cannotCreateSubjectTypeWithSourceFolio() {
    JsonObject subjectType = new JsonObject()
      .put("name", "Topical name2")
      .put("source", "folio");

    Response response = createSubjectType(subjectType);

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertEquals(422, response.getStatusCode());
    assertEquals(1, errors.size());
    assertEquals(
      "Illegal operation: Source field cannot be set to folio",
      errors.getJsonObject(0).getString("message"));
  }

  @Test
  public void cannotCreateSubjectTypeWithSourceConsortiumAtNonEcs() {
    JsonObject subjecType = new JsonObject()
      .put("name", "Topical name2")
      .put("source", "consortium");

    Response resource = createSubjectType(subjecType);

    JsonArray errors = resource.getJson().getJsonArray("errors");
    assertEquals(422, resource.getStatusCode());
    assertEquals(1, errors.size());
    assertEquals(
      "Illegal operation: Source consortium cannot be applied at non-consortium tenant",
      errors.getJsonObject(0).getString("message"));
  }

  @Test
  public void canCreateSubjectTypeWithSourceConsortiumAtEcs() {
    JsonObject subjectType = new JsonObject()
      .put("name", "Topical name2")
      .put("source", "consortium");

    Response resource = createSubjectType(subjectType, CONSORTIUM_CENTRAL_TENANT);

    assertEquals(201, resource.getStatusCode());
  }

  @Test
  public void cannotUpdateNonExistingSubjectType() {
    JsonObject subjectType = new JsonObject()
      .put("name", "Library of Congress Subject Headings")
      .put("source", "local");

    Response response = updateSubjectType(UUID.randomUUID().toString(), subjectType);

    assertEquals(404, response.getStatusCode());
    assertEquals("SubjectType was not found", response.getBody());
  }

  @Test
  public void cannotUpdateSubjectTypeWithSourceFolio() {
    JsonObject subjectType = new JsonObject()
      .put("name", "Topical name2")
      .put("source", "local");

    Response response = updateSubjectType(SUBJECT_TYPE_ID, subjectType);

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertEquals(422, response.getStatusCode());
    assertEquals(1, errors.size());
    assertEquals(
      "Illegal operation: Source folio cannot be updated",
      errors.getJsonObject(0).getString("message"));
  }

  @Test
  public void cannotUpdateSubjectTypeToFolio() {
    String subjectTypeId = UUID.randomUUID().toString();

    JsonObject subjectType = new JsonObject()
      .put("id", subjectTypeId)
      .put("name", "Library Test" + subjectTypeId)
      .put("source", "local");

    Response existingSubjectType = createSubjectType(subjectType);

    Response response = updateSubjectType(subjectTypeId, existingSubjectType.getJson().put("source", "folio"));

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertEquals(422, response.getStatusCode());
    assertEquals(1, errors.size());
    assertEquals("Illegal operation: Source field cannot be set to folio",
      errors.getJsonObject(0).getString("message"));
  }

  @Test
  public void cannotUpdateSubjectTypeToConsortiumAtNonEcs() {
    String subjectTypeId = UUID.randomUUID().toString();

    JsonObject subjectType = new JsonObject()
      .put("id", subjectTypeId)
      .put("name", "Library Test" + subjectTypeId)
      .put("source", "local");

    Response existingSubjectType = createSubjectType(subjectType);

    Response response = updateSubjectType(subjectTypeId, existingSubjectType.getJson().put("source", "consortium"));

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertEquals(422, response.getStatusCode());
    assertEquals(1, errors.size());
    assertEquals("Illegal operation: Source field cannot be updated at non-consortium tenant",
      errors.getJsonObject(0).getString("message"));
  }

  @Test
  public void canUpdateSubjectTypeToConsortiumAtEcs() {
    String subjectTypeId = UUID.randomUUID().toString();

    JsonObject subjectType = new JsonObject()
      .put("id", subjectTypeId)
      .put("name", "Library Test" + subjectTypeId)
      .put("source", "local");

    Response existingSubjectType = createSubjectType(subjectType, CONSORTIUM_CENTRAL_TENANT);

    Response response = updateSubjectType(subjectTypeId,
      existingSubjectType.getJson().put("source", "consortium"), CONSORTIUM_CENTRAL_TENANT);

    assertEquals(204, response.getStatusCode());
  }

  @Test
  public void canUpdateSubjectTypeToLocalAtEcs() {
    String subjectTypeId = UUID.randomUUID().toString();

    JsonObject subjectType = new JsonObject()
      .put("id", subjectTypeId)
      .put("name", "Library Test" + subjectTypeId)
      .put("source", "consortium");

    Response existingSubjectType = createSubjectType(subjectType, CONSORTIUM_CENTRAL_TENANT);

    Response response = updateSubjectType(subjectTypeId,
      existingSubjectType.getJson().put("source", "local"), CONSORTIUM_CENTRAL_TENANT);

    assertEquals(204, response.getStatusCode());
  }

  @Test
  public void cannotDeleteSubjectTypeLinkedToInstance() {
    var instanceId = UUID.randomUUID();

    JsonObject subjectType = new JsonObject()
      .put("name", "Library Test " + UUID_INSTANCE_SUBJECT_TYPE_ID)
      .put("source", "local");

    var subjectTypeId = createSubjectType(subjectType).getJson().getString("id");

    var instance = instance(instanceId);
    var subject = new Subject()
      .withSourceId(UUID_INSTANCE_SUBJECT_SOURCE_ID.toString())
      .withTypeId(subjectTypeId)
      .withValue("subject");
    var subjects = new JsonArray().add(subject);
    instance.put(SUBJECTS_KEY, subjects);

    createInstanceRecord(instance);

    Response response = deleteSubjectType(UUID.fromString(subjectTypeId));

    assertEquals(400, response.getStatusCode());
    assertTrue(response.getBody().contains("id is still referenced from table instance_subject_type"));
  }

  @Test
  public void clearLinksBetweenSubjectTypeAndInstance() {
    var instanceId = UUID.randomUUID();

    JsonObject subjectType = new JsonObject()
      .put("name", "Library Test2 " + UUID_INSTANCE_SUBJECT_TYPE_ID)
      .put("source", "local");

    var subjectTypeId = createSubjectType(subjectType).getJson().getString("id");

    var instance = instance(instanceId);
    var subject = new Subject()
      .withSourceId(UUID_INSTANCE_SUBJECT_SOURCE_ID.toString())
      .withTypeId(subjectTypeId)
      .withValue("subject");
    var subjects = new JsonArray().add(subject);
    instance.put(SUBJECTS_KEY, subjects);

    createInstanceRecord(instance);

    var response = deleteInstanceRecord(instanceId);
    assertEquals(204, response.getStatusCode());
  }

  private Response createSubjectType(JsonObject object) {
    return createSubjectType(object, TENANT_ID);
  }

  private Response createSubjectType(JsonObject object, String tenantId) {
    return subjectTypeClient.attemptToCreate("", object, tenantId, Map.of(XOkapiHeaders.URL, mockServer.baseUrl()));
  }

  private Response updateSubjectType(String id, JsonObject object) {
    return subjectTypeClient.attemptToReplace(id, object, TENANT_ID, Map.of(XOkapiHeaders.URL, mockServer.baseUrl()));
  }

  private Response updateSubjectType(String id, JsonObject object, String tenantId) {
    return subjectTypeClient.attemptToReplace(id, object, tenantId, Map.of(XOkapiHeaders.URL, mockServer.baseUrl()));
  }

  private Response deleteSubjectType(UUID id) {
    return subjectTypeClient.attemptToDelete(id);
  }
}
