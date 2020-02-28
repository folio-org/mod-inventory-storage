package org.folio.rest.api;

import static org.folio.rest.api.TestBaseWithInventoryUtil.UUID_ISBN;
import static org.folio.rest.api.TestBaseWithInventoryUtil.identifier;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.rest.api.entities.Instance;
import org.folio.rest.api.entities.PrecedingSucceedingTitle;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.Response;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;


public class PrecedingSucceedingTitleTest extends TestBase {

  private static final String INVALID_UUID_ERROR_MESSAGE = "Invalid UUID format of id, should be " +
    "xxxxxxxx-xxxx-Mxxx-Nxxx-xxxxxxxxxxxx where M is 1-5 and N is 8, 9, a, b, A or B and x is 0-9, a-f or A-F.";
  private static final String PRECEDING_SUCCEEDING_TITLE_TABLE = "preceding_succeeding_title";
  private static final String TEST_TENANT = "test_tenant";
  private static final String INSTANCE_TYPE_ID_TEXT = "6312d172-f0cf-40f6-b27d-9fa8feaf332f";
  private static final String HRID = "inst000000000022";
  private static final String TITLE = "A web primer";

  @BeforeClass
  public static void beforeAll() {
    StorageTestSuite.deleteAll(TEST_TENANT, PRECEDING_SUCCEEDING_TITLE_TABLE);
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));
  }

  @Test
  public void canCreateConnectedPrecedingSucceedingTitle() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {

    IndividualResource instance1Resource = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);
    IndividualResource instance2Resource = createInstance("Title Two", INSTANCE_TYPE_ID_TEXT);
    String instance1Id = instance1Resource.getId().toString();
    String instance2Id = instance2Resource.getId().toString();

    PrecedingSucceedingTitle precedingSucceedingTitle = new PrecedingSucceedingTitle(
      instance1Id, instance2Id, null, null, null);

    IndividualResource response = precedingSucceedingTitleClient.create(precedingSucceedingTitle.getJson());
    assertPrecedingSucceedingTitle(response, instance1Id, instance2Id, null, null, new JsonArray());
  }

  @Test
  public void canCreateUnconnectedPrecedingTitle() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {

    IndividualResource instanceResource = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);
    String instanceId = instanceResource.getId().toString();
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));

    PrecedingSucceedingTitle precedingSucceedingTitle = new PrecedingSucceedingTitle(
      null, instanceId, TITLE, HRID, identifiers);
    IndividualResource response = precedingSucceedingTitleClient.create(precedingSucceedingTitle.getJson());

    assertPrecedingSucceedingTitle(response, null, instanceId, TITLE, HRID, identifiers);
  }

  @Test
  public void canCreateUnconnectedSucceedingTitle() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {

    IndividualResource instanceResource = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);
    String instanceId = instanceResource.getId().toString();
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));

    PrecedingSucceedingTitle precedingSucceedingTitle = new PrecedingSucceedingTitle(
      instanceId, null, TITLE, HRID, identifiers);
    IndividualResource response = precedingSucceedingTitleClient.create(precedingSucceedingTitle.getJson());

    assertPrecedingSucceedingTitle(response, instanceId, null, TITLE, HRID, identifiers);
  }

  @Test
  public void canUpdateConnectedPrecedingSucceedingTitle() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {

    IndividualResource instance1Resource = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);
    IndividualResource instance2Resource = createInstance("Title Two", INSTANCE_TYPE_ID_TEXT);
    String instance1Id = instance1Resource.getId().toString();
    String instance2Id = instance2Resource.getId().toString();

    PrecedingSucceedingTitle precedingSucceedingTitle = new PrecedingSucceedingTitle(
      instance1Id, instance2Id, null, null, null);

    IndividualResource response = precedingSucceedingTitleClient.create(precedingSucceedingTitle.getJson());

    PrecedingSucceedingTitle newPrecedingSucceedingTitle = new PrecedingSucceedingTitle(
      instance2Id, instance1Id, null, null, null);

    precedingSucceedingTitleClient.replace(response.getId(), newPrecedingSucceedingTitle.getJson());

    assertPrecedingSucceedingTitle(response, instance2Id, instance1Id, null, null, new JsonArray());
  }

  @Test
  public void canUpdateUnconnectedPrecedingSucceedingTitle() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {

    IndividualResource instance1Resource = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);
    String instance1Id = instance1Resource.getId().toString();
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));

    PrecedingSucceedingTitle precedingSucceedingTitle = new PrecedingSucceedingTitle(
      instance1Id, null, TITLE, HRID, identifiers);

    IndividualResource response = precedingSucceedingTitleClient.create(precedingSucceedingTitle.getJson());

    IndividualResource instance2Resource = createInstance("Title Two", INSTANCE_TYPE_ID_TEXT);
    String instance2Id = instance2Resource.getId().toString();
    String newTitle = "New";
    String newHrid = "inst000000000133";
    JsonArray newIdentifiers = new JsonArray();
    newIdentifiers.add(identifier(UUID_ISBN, "1081473619777"));
    PrecedingSucceedingTitle newPrecedingSucceedingTitle = new PrecedingSucceedingTitle(
      instance2Id, instance1Id, newTitle, newHrid, newIdentifiers);

    precedingSucceedingTitleClient.replace(response.getId(), newPrecedingSucceedingTitle.getJson());

    assertPrecedingSucceedingTitle(response, instance2Id, instance1Id, newTitle, newHrid, newIdentifiers);
  }

  @Test
  public void canDeletePrecedingSucceedingTitle() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {

    IndividualResource instanceResource = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));

    PrecedingSucceedingTitle precedingSucceedingTitle = new PrecedingSucceedingTitle(
      null, instanceResource.getId().toString(), TITLE, HRID, identifiers);
    IndividualResource response = precedingSucceedingTitleClient.create(
      precedingSucceedingTitle.getJson());

    precedingSucceedingTitleClient.delete(response.getId());

    Response getResponse = precedingSucceedingTitleClient.getById(response.getId());
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canGetPrecedingSucceedingTitleByQuery() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {
    IndividualResource instance1Resource = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);
    IndividualResource instance2Resource = createInstance("Title Two", INSTANCE_TYPE_ID_TEXT);
    String instance1Id = instance1Resource.getId().toString();
    String instance2Id = instance2Resource.getId().toString();
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));

    PrecedingSucceedingTitle precedingSucceedingTitle = new PrecedingSucceedingTitle(
      instance1Id, instance2Id, TITLE, HRID, identifiers);

    IndividualResource response = precedingSucceedingTitleClient.create(
      precedingSucceedingTitle.getJson());

    List<JsonObject> badParameterResponse = precedingSucceedingTitleClient.getByQuery(
      "?query=succeedingInstanceId=" + instance2Id);

    assertThat(badParameterResponse.size(), is(1));
    assertPrecedingSucceedingTitle(badParameterResponse.get(0), response.getId().toString(),
      instance1Id, instance2Id, TITLE, HRID, identifiers);
  }

  @Test
  public void cannotCreatePrecedingSucceedingTitleWithNonExistingPrecedingInstance()
    throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    String nonExistingInstanceId = "14b65645-2e49-4a85-8dc1-43d444710570";
    IndividualResource instanceResource = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);

    PrecedingSucceedingTitle precedingSucceedingTitle = new PrecedingSucceedingTitle(
      nonExistingInstanceId, instanceResource.getId().toString(), null, null, null);
    Response response = precedingSucceedingTitleClient.attemptToCreate(precedingSucceedingTitle
      .getJson());

    assertThat(response.getStatusCode(), is(HttpResponseStatus.UNPROCESSABLE_ENTITY.code()));
    assertErrors(response, "Cannot set preceding_succeeding_title.precedinginstanceid = " +
      "14b65645-2e49-4a85-8dc1-43d444710570 because it does not exist in instance.id.");
  }

  @Test
  public void cannotCreatePrecedingSucceedingTitleWithNonExistingSucceedingInstance()
    throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    String nonExistingInstanceId = "14b65645-2e49-4a85-8dc1-43d444710570";

    IndividualResource instance1Response = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));

    PrecedingSucceedingTitle precedingSucceedingTitle = new PrecedingSucceedingTitle(
      instance1Response.getId().toString(), nonExistingInstanceId, TITLE, HRID, identifiers);

    Response response = precedingSucceedingTitleClient.attemptToCreate(precedingSucceedingTitle.getJson());

    assertThat(response.getStatusCode(), is(HttpResponseStatus.UNPROCESSABLE_ENTITY.code()));
    assertErrors(response, "Cannot set preceding_succeeding_title.succeedinginstanceid = " +
      "14b65645-2e49-4a85-8dc1-43d444710570 because it does not exist in instance.id.");
  }

  @Test
  public void cannotCreatePrecedingSucceedingTitleWithEmptyPrecedingAndSucceedingInstanceId()
    throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));

    PrecedingSucceedingTitle precedingSucceedingTitle = new PrecedingSucceedingTitle(
      null, null, TITLE, HRID, identifiers);

    Response response = precedingSucceedingTitleClient.attemptToCreate(precedingSucceedingTitle
      .getJson());

    assertThat(response.getStatusCode(), is(HttpResponseStatus.UNPROCESSABLE_ENTITY.code()));
    assertErrors(response, "The precedingInstanceId and succeedingInstanceId can't be empty at the same time");
  }

  @Test
  public void cannotGetByInvalidPrecedingSucceedingId() throws MalformedURLException,
    InterruptedException, TimeoutException, ExecutionException {

    Response badParameterResponse = precedingSucceedingTitleClient.getByIdIfPresent("abc");
    assertThat(badParameterResponse.getStatusCode(), is(HttpResponseStatus.UNPROCESSABLE_ENTITY.code()));
    assertErrors(badParameterResponse, INVALID_UUID_ERROR_MESSAGE);
  }

  @Test
  public void cannotPutByInvalidPrecedingSucceedingId() throws InterruptedException,
    TimeoutException, ExecutionException, MalformedURLException {

    IndividualResource instance1Resource = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);

    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));
    PrecedingSucceedingTitle precedingSucceedingTitle = new PrecedingSucceedingTitle(
      instance1Resource.getId().toString(), null, TITLE, HRID, identifiers);

    Response putResponse = precedingSucceedingTitleClient.attemptToReplace("abc",
      precedingSucceedingTitle.getJson());
    assertThat(putResponse.getStatusCode(), is(HttpResponseStatus.UNPROCESSABLE_ENTITY.code()));
    assertErrors(putResponse, INVALID_UUID_ERROR_MESSAGE);
  }

  @Test
  public void cannotDeleteByInvalidPrecedingSucceedingId() throws InterruptedException,
    TimeoutException, ExecutionException, MalformedURLException {

    Response badParameterResponse = precedingSucceedingTitleClient.deleteIfPresent("abc");
    assertThat(badParameterResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    assertThat(badParameterResponse.getBody(), is(INVALID_UUID_ERROR_MESSAGE));
  }

  private IndividualResource createInstance(String title, String instanceTypeId)
    throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {
    Instance instance = new Instance(title, "TEST", instanceTypeId);

    return instancesClient.create(instance.getJson());
  }

  private void assertErrors(Response response, String message) {
    Errors errors = response.getJson().mapTo(Errors.class);
    assertThat(errors.getErrors().get(0).getMessage(), is(message));
  }

  private void assertPrecedingSucceedingTitle(IndividualResource response, String precedingSucceedingTitleId,
    String succeedingTitleId, String title, String hrid, JsonArray identifiers)
    throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    Response getResponse = precedingSucceedingTitleClient.getById(response.getId());
    JsonObject precedingSucceedingTitleResponse = getResponse.getJson();
    assertPrecedingSucceedingTitle(precedingSucceedingTitleResponse, response.getId().toString(),
      precedingSucceedingTitleId, succeedingTitleId, title, hrid, identifiers);
  }

  private void assertPrecedingSucceedingTitle(JsonObject precedingSucceedingTitleResponse, String id,
    String precedingSucceedingTitleId, String succeedingTitleId, String title, String hrid, JsonArray identifiers) {
    assertThat(precedingSucceedingTitleResponse.getString(PrecedingSucceedingTitle.ID_KEY), is(id));
    assertThat(precedingSucceedingTitleResponse.getString(PrecedingSucceedingTitle.PRECEDING_INSTANCE_ID_KEY),
      is(precedingSucceedingTitleId));
    assertThat(precedingSucceedingTitleResponse.getString(PrecedingSucceedingTitle.SUCCEEDING_INSTANCE_ID_KEY),
      is(succeedingTitleId));
    assertThat(precedingSucceedingTitleResponse.getString(PrecedingSucceedingTitle.TITLE_KEY), is(title));
    assertThat(precedingSucceedingTitleResponse.getString(PrecedingSucceedingTitle.HRID_KEY), is(hrid));
    assertThat(precedingSucceedingTitleResponse.getJsonArray(PrecedingSucceedingTitle.IDENTIFIERS_KEY),
      is(identifiers));
  }
}
