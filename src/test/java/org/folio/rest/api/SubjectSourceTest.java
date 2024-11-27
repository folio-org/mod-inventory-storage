package org.folio.rest.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.api.InstanceStorageTest.SUBJECTS_KEY;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.ResponseUtil.SOURCE_CANNOT_BE_DELETED_USED_BY_INSTANCE;
import static org.folio.rest.support.http.InterfaceUrls.subjectSourcesUrl;
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
import java.util.concurrent.TimeoutException;
import junitparams.JUnitParamsRunner;
import lombok.SneakyThrows;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Subject;
import org.folio.rest.support.Response;
import org.folio.rest.support.http.ResourceClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class SubjectSourceTest extends TestBaseWithInventoryUtil {
  private static ResourceClient subjectSourceClient;
  private static final String SUBJECT_SOURCE_ID = "e894d0dc-621d-4b1d-98f6-6f7120eb0d40";

  @SneakyThrows
  @BeforeClass
  public static void before() {
    prepareTenant(CONSORTIUM_CENTRAL_TENANT, false);
    subjectSourceClient = ResourceClient.forSubjectSources(getClient());

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
  public void cannotCreateSubjectSourceWithDuplicateName() {

    JsonObject subjectSource = new JsonObject()
      .put("name", "Library of Congress Subject Headings2")
      .put("source", "local");

    createSubjectSource(subjectSource);

    Response response = createSubjectSource(subjectSource);
    assertThat(response.getStatusCode(), is(422));

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertThat(errors.size(), is(1));
    assertTrue(errors.getJsonObject(0).getString("message").contains("(jsonb ->> 'name'::text)) value already exists"));
  }

  @Test
  public void cannotCreateSubjectSourceWithDuplicateCode()
    throws InterruptedException, TimeoutException,
    ExecutionException {

    JsonObject subjectSource = new JsonObject()
      .put("name", "Test")
      .put("code", "test")
      .put("source", "local");

    createSubjectSource(subjectSource);

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();
    getClient().post(subjectSourcesUrl(""), subjectSource.put("name", "Test2"),
      TENANT_ID, json(postCompleted));

    Response response = postCompleted.get(TIMEOUT, SECONDS);
    assertThat(response.getStatusCode(), is(422));

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertThat(errors.size(), is(1));
    assertTrue(errors.getJsonObject(0).getString("message").contains("(jsonb ->> 'code'::text)) value already exists"));
  }

  @Test
  public void cannotCreateSubjectSourceWithSourceFolio() {
    JsonObject subjectSource = new JsonObject()
      .put("name", "Library of Congress Subject Headings2")
      .put("source", "folio");

    Response response = createSubjectSource(subjectSource);

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertEquals(422, response.getStatusCode());
    assertEquals(1, errors.size());
    assertEquals(
      "Illegal operation: Source field cannot be set to folio",
      errors.getJsonObject(0).getString("message"));
  }

  @Test
  public void cannotCreateSubjectSourceWithSourceConsortiumAtNonEcs() {
    JsonObject subjectSource = new JsonObject()
      .put("name", "Library of Congress Subject Headings2")
      .put("source", "consortium");

    Response resource = createSubjectSource(subjectSource);

    JsonArray errors = resource.getJson().getJsonArray("errors");
    assertEquals(422, resource.getStatusCode());
    assertEquals(1, errors.size());
    assertEquals(
      "Illegal operation: Source consortium cannot be applied at non-consortium tenant",
      errors.getJsonObject(0).getString("message"));
  }

  @Test
  public void canCreateSubjectSourceWithSourceConsortiumAtEcs() {
    JsonObject subjectSource = new JsonObject()
      .put("name", "Library of Congress Subject Headings2")
      .put("source", "consortium");

    Response resource = createSubjectSource(subjectSource, CONSORTIUM_CENTRAL_TENANT);

    assertEquals(201, resource.getStatusCode());
  }

  @Test
  public void cannotUpdateNonExistingSubjectSource() {
    JsonObject subjectSource = new JsonObject()
      .put("name", "Library of Congress Subject Headings")
      .put("source", "local");

    Response response = updateSubjectSource(UUID.randomUUID().toString(), subjectSource);

    assertEquals(404, response.getStatusCode());
    assertEquals("SubjectSource was not found", response.getBody());
  }

  @Test
  public void cannotUpdateSubjectSourceWithSourceFolio() {
    JsonObject subjectSource = new JsonObject()
      .put("name", "Library of Congress Subject Headings")
      .put("source", "local");

    Response response = updateSubjectSource(SUBJECT_SOURCE_ID, subjectSource);

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertEquals(422, response.getStatusCode());
    assertEquals(1, errors.size());
    assertEquals("Illegal operation: Source folio cannot be updated",
      errors.getJsonObject(0).getString("message"));
  }

  @Test
  public void cannotUpdateSubjectSourceToFolio() {
    String subjectSourceId = UUID.randomUUID().toString();

    JsonObject subjectSource = new JsonObject()
      .put("id", subjectSourceId)
      .put("name", "Library Test" + subjectSourceId)
      .put("source", "local");

    Response existingSubjectSource = createSubjectSource(subjectSource);

    Response response = updateSubjectSource(subjectSourceId, existingSubjectSource.getJson().put("source", "folio"));

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertEquals(422, response.getStatusCode());
    assertEquals(1, errors.size());
    assertEquals("Illegal operation: Source field cannot be set to folio",
      errors.getJsonObject(0).getString("message"));
  }

  @Test
  public void cannotUpdateSubjectSourceToConsortiumAtNonEcs() {
    String subjectSourceId = UUID.randomUUID().toString();

    JsonObject subjectSource = new JsonObject()
      .put("id", subjectSourceId)
      .put("name", "Library Test" + subjectSourceId)
      .put("source", "local");

    Response existingSubjectSource = createSubjectSource(subjectSource);

    Response response = updateSubjectSource(subjectSourceId,
      existingSubjectSource.getJson().put("source", "consortium"));

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertEquals(422, response.getStatusCode());
    assertEquals(1, errors.size());
    assertEquals("Illegal operation: Source field cannot be updated at non-consortium tenant",
      errors.getJsonObject(0).getString("message"));
  }

  @Test
  public void canUpdateSubjectSourceToConsortiumAtEcs() {
    String subjectSourceId = UUID.randomUUID().toString();

    JsonObject subjectSource = new JsonObject()
      .put("id", subjectSourceId)
      .put("name", "Library Test" + subjectSourceId)
      .put("source", "local");

    Response existingSubjectSource = createSubjectSource(subjectSource, CONSORTIUM_CENTRAL_TENANT);

    Response response = updateSubjectSource(subjectSourceId,
      existingSubjectSource.getJson().put("source", "consortium"), CONSORTIUM_CENTRAL_TENANT);

    assertEquals(204, response.getStatusCode());
  }

  @Test
  public void canUpdateSubjectSourceToLocalAtEcs() {
    String subjectSourceId = UUID.randomUUID().toString();

    JsonObject subjectSource = new JsonObject()
      .put("id", subjectSourceId)
      .put("name", "Library Test" + subjectSourceId)
      .put("source", "consortium");

    Response existingSubjectSource = createSubjectSource(subjectSource, CONSORTIUM_CENTRAL_TENANT);

    Response response = updateSubjectSource(subjectSourceId,
      existingSubjectSource.getJson().put("source", "local"), CONSORTIUM_CENTRAL_TENANT);

    assertEquals(204, response.getStatusCode());
  }

  @Test
  public void cannotDeleteSubjectSourceLinkedToInstance() {
    var instanceId = UUID.randomUUID();

    JsonObject subjectSource = new JsonObject()
      .put("name", "Library Test " + UUID_INSTANCE_SUBJECT_SOURCE_ID)
      .put("source", "local");

    var subjectSourceId = createSubjectSource(subjectSource).getJson().getString("id");

    var instance = instance(instanceId);
    var subject = new Subject()
      .withSourceId(subjectSourceId)
      .withTypeId(UUID_INSTANCE_SUBJECT_TYPE_ID.toString())
      .withValue("subject");
    var subjects = new JsonArray().add(subject);
    instance.put(SUBJECTS_KEY, subjects);

    createInstanceRecord(instance);

    Response response = deleteSubjectSource(UUID.fromString(subjectSourceId));

    assertEquals(422, response.getStatusCode());
    JsonArray errors = response.getJson().getJsonArray("errors");
    assertThat(errors.size(), is(1));
    assertTrue(errors.getJsonObject(0).getString("message").contains(SOURCE_CANNOT_BE_DELETED_USED_BY_INSTANCE));
  }

  private Response createSubjectSource(JsonObject object) {
    return createSubjectSource(object, TENANT_ID);
  }

  private Response createSubjectSource(JsonObject object, String tenantId) {
    return subjectSourceClient.attemptToCreate("", object, tenantId, Map.of(XOkapiHeaders.URL, mockServer.baseUrl()));
  }

  private Response updateSubjectSource(String id, JsonObject object) {
    return subjectSourceClient.attemptToReplace(id, object, TENANT_ID, Map.of(XOkapiHeaders.URL, mockServer.baseUrl()));
  }

  private Response updateSubjectSource(String id, JsonObject object, String tenantId) {
    return subjectSourceClient.attemptToReplace(id, object, tenantId, Map.of(XOkapiHeaders.URL, mockServer.baseUrl()));
  }

  private Response deleteSubjectSource(UUID id) {
    return subjectSourceClient.attemptToDelete(id);
  }

}
