package org.folio.rest.api;

import static org.folio.rest.api.TestBaseWithInventoryUtil.UUID_ISBN;
import static org.folio.rest.api.TestBaseWithInventoryUtil.identifier;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.precedingSucceedingTitleUrl;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.rest.api.entities.Instance;
import org.folio.rest.api.entities.PrecedingSucceedingTitle;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.support.Response;
import org.junit.AfterClass;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class PrecedingSucceedingTitleTest extends ResourceTestBase {

  private final static String INSTANCE_TYPE_ID_TEXT = "6312d172-f0cf-40f6-b27d-9fa8feaf332f";
  private final static String HRID = "inst000000000022";
  private final static String TITLE = "A web primer";

  @AfterClass
  public static void afterAll() {
    StorageTestSuite.deleteAll(instancesStorageUrl(""));
  }

  @Test
  public void testPrecedingSucceedingTitleBasicCrud() throws MalformedURLException,
    InterruptedException, TimeoutException, ExecutionException {

    String entityPath = precedingSucceedingTitleUrl("").getPath();
    JsonObject instance1Response = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);
    JsonObject instance2Response = createInstance("Title Two", INSTANCE_TYPE_ID_TEXT);
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));

    PrecedingSucceedingTitle precedingSucceedingTitle = new PrecedingSucceedingTitle(
      instance1Response.getString(Instance.ID_KEY), instance2Response.getString(Instance.ID_KEY),
      TITLE, HRID, identifiers);
    Response postResponse = createReferenceRecord(entityPath, precedingSucceedingTitle);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String entityUUID = postResponse.getJson().getString("id");

    String updateProperty = PrecedingSucceedingTitle.TITLE_KEY;

    testGetPutDeletePost(entityPath, entityUUID, precedingSucceedingTitle, updateProperty);
  }

  @Test
  public void canCreateConnectedPrecedingSucceedingTitle() throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    JsonObject instance1Response = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);
    JsonObject instance2Response = createInstance("Title Two", INSTANCE_TYPE_ID_TEXT);
    String instance1Id = instance1Response.getString(Instance.ID_KEY);
    String instance2Id = instance2Response.getString(Instance.ID_KEY);

    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));

    PrecedingSucceedingTitle precedingSucceedingTitle = new PrecedingSucceedingTitle(
      instance1Id, instance2Id, TITLE, HRID, identifiers);

    Response response = createReferenceRecord(
      precedingSucceedingTitleUrl("").getPath(), precedingSucceedingTitle);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    assertPrecedingSucceedingTitleIsCreated(response, instance1Id, instance2Id, TITLE, HRID, identifiers);
    deletePrecedingSucceedingTitle(response.getJson().getString("id"));
  }

  @Test
  public void canCreateUnconnectedPrecedingTitle() throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    JsonObject instanceResponse = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);
    String instanceId = instanceResponse.getString(Instance.ID_KEY);
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));

    PrecedingSucceedingTitle instanceRelationshipRequestObject = new PrecedingSucceedingTitle(
      null, instanceResponse.getString(Instance.ID_KEY), TITLE, HRID, identifiers);
    Response response = createReferenceRecord(
      precedingSucceedingTitleUrl("").getPath(), instanceRelationshipRequestObject);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    assertPrecedingSucceedingTitleIsCreated(response, null, instanceId, TITLE, HRID, identifiers);
    deletePrecedingSucceedingTitle(response.getJson().getString("id"));
  }

  @Test
  public void canCreateUnconnectedSucceedingTitle() throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    JsonObject instanceResponse = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);
    String instanceId = instanceResponse.getString(Instance.ID_KEY);
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));

    PrecedingSucceedingTitle instanceRelationshipRequestObject = new PrecedingSucceedingTitle(
      instanceResponse.getString(Instance.ID_KEY), null, TITLE, HRID, identifiers);
    Response response = createReferenceRecord(
      precedingSucceedingTitleUrl("").getPath(), instanceRelationshipRequestObject);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertPrecedingSucceedingTitleIsCreated(response, instanceId, null, TITLE, HRID, identifiers);
    deletePrecedingSucceedingTitle(response.getJson().getString("id"));
  }

  @Test
  public void canUpdateConnectedPrecedingSucceedingTitle() throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    JsonObject instance1Response = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);
    JsonObject instance2Response = createInstance("Title Two", INSTANCE_TYPE_ID_TEXT);
    String instance1Id = instance1Response.getString(Instance.ID_KEY);
    String instance2Id = instance2Response.getString(Instance.ID_KEY);
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));

    PrecedingSucceedingTitle precedingSucceedingTitle = new PrecedingSucceedingTitle(
      instance1Response.getString(Instance.ID_KEY), instance2Response.getString(Instance.ID_KEY),
      TITLE, HRID, identifiers);

    Response response = createReferenceRecord(
      precedingSucceedingTitleUrl("").getPath(), precedingSucceedingTitle);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String id = response.getJson().getString("id");

    final String newTitle = "New";
    final String newHrid = "inst000000000133";
    final JsonArray newIdentifiers = new JsonArray();
    newIdentifiers.add(identifier(UUID_ISBN, "1081473619777"));
    final PrecedingSucceedingTitle newPrecedingSucceedingTitle = new PrecedingSucceedingTitle(
      instance2Id, instance1Id, newTitle, newHrid, newIdentifiers);
    Response putResponse = updateRecord(precedingSucceedingTitleUrl("/" + id), newPrecedingSucceedingTitle);
    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    assertPrecedingSucceedingTitleIsCreated(response, instance2Id, instance1Id, newTitle, newHrid, newIdentifiers);
    deletePrecedingSucceedingTitle(response.getJson().getString("id"));
  }

  @Test
  public void canUpdateConnectedPrecedingTitle() throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    JsonObject instance1Response = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);
    String instance1Id = instance1Response.getString(Instance.ID_KEY);
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));

    PrecedingSucceedingTitle precedingSucceedingTitle = new PrecedingSucceedingTitle(
      instance1Id, null, TITLE, HRID, identifiers);

    Response response = createReferenceRecord(
      precedingSucceedingTitleUrl("").getPath(), precedingSucceedingTitle);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    String id = response.getJson().getString("id");

    JsonObject instance2Response = createInstance("Title Two", INSTANCE_TYPE_ID_TEXT);
    String instance2Id = instance2Response.getString(Instance.ID_KEY);
    final String newTitle = "New";
    final String newHrid = "inst000000000133";
    final JsonArray newIdentifiers = new JsonArray();
    newIdentifiers.add(identifier(UUID_ISBN, "1081473619777"));
    final PrecedingSucceedingTitle newPrecedingSucceedingTitle = new PrecedingSucceedingTitle(
      instance2Id, instance1Id, newTitle, newHrid, newIdentifiers);
    Response putResponse = updateRecord(precedingSucceedingTitleUrl("/" + id), newPrecedingSucceedingTitle);
    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    assertPrecedingSucceedingTitleIsCreated(response, instance2Id, instance1Id, newTitle, newHrid, newIdentifiers);
    deletePrecedingSucceedingTitle(response.getJson().getString("id"));
  }

  @Test
  public void canGetPrecedingSucceedingTitleByQuery() throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    JsonObject instance1Response = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);
    JsonObject instance2Response = createInstance("Title Two", INSTANCE_TYPE_ID_TEXT);
    String instance1Id = instance1Response.getString(Instance.ID_KEY);
    String instance2Id = instance2Response.getString(Instance.ID_KEY);
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));

    PrecedingSucceedingTitle precedingSucceedingTitle = new PrecedingSucceedingTitle(
      instance1Response.getString(Instance.ID_KEY), instance2Response.getString(Instance.ID_KEY),
      TITLE, HRID, identifiers);

    Response response = createReferenceRecord(
      precedingSucceedingTitleUrl("").getPath(), precedingSucceedingTitle);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    Response badParameterResponse = getByQuery(precedingSucceedingTitleUrl("?query=succeedingInstanceId=" + instance2Id));
    assertThat(badParameterResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    assertPrecedingSucceedingTitleIsCreated(response, instance1Id, instance2Id, TITLE, HRID, identifiers);
    deletePrecedingSucceedingTitle(response.getJson().getString("id"));
  }

  @Test
  public void cannotCreatePrecedingSucceedingTitleWithNonExistingPrecedingInstance()
    throws InterruptedException, ExecutionException, TimeoutException {

    final String nonExistingInstanceId = "14b65645-2e49-4a85-8dc1-43d444710570";

    JsonObject instance1Response = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));

    PrecedingSucceedingTitle instanceRelationshipRequestObject = new PrecedingSucceedingTitle(
      nonExistingInstanceId, instance1Response.getString(Instance.ID_KEY), TITLE, HRID, identifiers);
    Response response = createReferenceRecord(
      precedingSucceedingTitleUrl("").getPath(), instanceRelationshipRequestObject);

    assertThat(response.getStatusCode(), is(UNPROCESSABLE_ENTITY));
    assertErrors(response, "Cannot set preceding_succeeding_title.precedinginstanceid = 14b65645-2e49-4a85-8dc1-43d444710570 because it does not exist in instance.id.");
  }

  @Test
  public void cannotCreatePrecedingSucceedingTitleWithNonExistingSucceedingInstance()
    throws InterruptedException, ExecutionException, TimeoutException {

    final String nonExistingInstanceId = "14b65645-2e49-4a85-8dc1-43d444710570";

    JsonObject instance1Response = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));

    PrecedingSucceedingTitle instanceRelationshipRequestObject = new PrecedingSucceedingTitle(
      instance1Response.getString(Instance.ID_KEY), nonExistingInstanceId, TITLE, HRID, identifiers);

    Response response = createReferenceRecord(
      precedingSucceedingTitleUrl("").getPath(), instanceRelationshipRequestObject);

    assertThat(response.getStatusCode(), is(UNPROCESSABLE_ENTITY));
    assertErrors(response, "Cannot set preceding_succeeding_title.succeedinginstanceid = 14b65645-2e49-4a85-8dc1-43d444710570 because it does not exist in instance.id.");
  }

  @Test
  public void cannotCreatePrecedingSucceedingTitleWithEmptyPrecedingAndSucceedingInstanceId()
    throws InterruptedException, ExecutionException, TimeoutException {

    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));

    PrecedingSucceedingTitle instanceRelationshipRequestObject = new PrecedingSucceedingTitle(
      null, null, TITLE, HRID, identifiers);

    Response response = createReferenceRecord(
      precedingSucceedingTitleUrl("").getPath(), instanceRelationshipRequestObject);

    assertThat(response.getStatusCode(), is(UNPROCESSABLE_ENTITY));
    assertErrors(response, "The precedingInstanceId and succeedingInstanceId can't be empty at the same time");
  }

  private JsonObject createInstance(String title, String instanceTypeId)
    throws InterruptedException, ExecutionException, TimeoutException {
    Instance requestObject = new Instance(title, "TEST", instanceTypeId);

    Response postResponse = createReferenceRecord(
      instancesStorageUrl("").getPath(), requestObject);

    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    return postResponse.getJson();
  }

  private void assertErrors(Response response, String message) {
    final Errors errors = response.getJson().mapTo(Errors.class);
    assertThat(errors.getErrors().get(0).getMessage(), is(message));
  }

  private void assertPrecedingSucceedingTitleIsCreated(Response response, String precedingSucceedingTitleId,
                                                       String succeedingTitleId, String title, String hrid, JsonArray identifiers)
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {
    String id = response.getJson().getString("id");

    Response getResponse = getById(precedingSucceedingTitleUrl("/" + id));
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject precedingSucceedingTitleResponse = getResponse.getJson();
    assertEquals(precedingSucceedingTitleResponse, id, precedingSucceedingTitleId, succeedingTitleId, title, hrid, identifiers);
  }

  private void assertEquals(JsonObject precedingSucceedingTitleResponse, String id, String precedingSucceedingTitleId,
                            String succeedingTitleId, String title, String hrid, JsonArray identifiers) {
    assertThat(precedingSucceedingTitleResponse.getString(PrecedingSucceedingTitle.ID_KEY), is(id));
    assertThat(precedingSucceedingTitleResponse.getString(PrecedingSucceedingTitle.PRECEDING_INSTANCE_ID_KEY), is(precedingSucceedingTitleId));
    assertThat(precedingSucceedingTitleResponse.getString(PrecedingSucceedingTitle.SUCCEEDING_INSTANCE_ID_KEY), is(succeedingTitleId));
    assertThat(precedingSucceedingTitleResponse.getString(PrecedingSucceedingTitle.TITLE_KEY), is(title));
    assertThat(precedingSucceedingTitleResponse.getString(PrecedingSucceedingTitle.HRID_KEY), is(hrid));
    assertThat(precedingSucceedingTitleResponse.getJsonArray(PrecedingSucceedingTitle.IDENTIFIERS_KEY), is(identifiers));
  }

  private void deletePrecedingSucceedingTitle(String id)
    throws ExecutionException, InterruptedException, TimeoutException {
    Response response = deleteReferenceRecordById(precedingSucceedingTitleUrl("/" + id));
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }
}
