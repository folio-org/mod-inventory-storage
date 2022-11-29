package org.folio.rest.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.api.entities.PrecedingSucceedingTitle.PRECEDING_INSTANCE_ID_KEY;
import static org.folio.rest.api.entities.PrecedingSucceedingTitle.SUCCEEDING_INSTANCE_ID_KEY;
import static org.folio.rest.support.http.InterfaceUrls.precedingSucceedingTitleUrl;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.folio.utility.VertxUtility.getClient;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import org.folio.rest.api.entities.PrecedingSucceedingTitle;
import org.folio.rest.api.entities.PrecedingSucceedingTitles;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

public class PrecedingSucceedingTitleTest extends TestBaseWithInventoryUtil {
  private static final String INVALID_UUID_ERROR_MESSAGE = "Invalid UUID format of id, should be " +
    "xxxxxxxx-xxxx-Mxxx-Nxxx-xxxxxxxxxxxx where M is 1-5 and N is 8, 9, a, b, A or B and x is 0-9, a-f or A-F.";
  private static final String HRID = "inst000000000022";
  private static final String TITLE = "A web primer";

  @SneakyThrows
  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(TENANT_ID, "preceding_succeeding_title");
    StorageTestSuite.deleteAll(TENANT_ID, "instance_relationship");
    StorageTestSuite.deleteAll(TENANT_ID, "bound_with_part");

    deleteAllById(precedingSucceedingTitleClient);

    clearData();
    setupMaterialTypes();
    setupLoanTypes();
    setupLocations();
    removeAllEvents();
  }

  @AfterClass
  public static void afterAll() {
    TestBase.afterAll();

    // Prevent tests from other classes from being affected by this data.
    StorageTestSuite.deleteAll(TENANT_ID, "preceding_succeeding_title");
    StorageTestSuite.deleteAll(TENANT_ID, "instance_relationship");
    StorageTestSuite.deleteAll(TENANT_ID, "bound_with_part");

    deleteAllById(precedingSucceedingTitleClient);
  }

  @Test
  public void canCreateConnectedPrecedingSucceedingTitle() {
    IndividualResource instance1Resource = createInstance("Title One");
    IndividualResource instance2Resource = createInstance("Title Two");
    String instance1Id = instance1Resource.getId().toString();
    String instance2Id = instance2Resource.getId().toString();

    PrecedingSucceedingTitle precedingSucceedingTitle = new PrecedingSucceedingTitle(
      instance1Id, instance2Id, null, null, null);

    IndividualResource response = precedingSucceedingTitleClient.create(precedingSucceedingTitle.getJson());
    assertPrecedingSucceedingTitle(response, instance1Id, instance2Id, null, null, new JsonArray());
  }

  @Test
  public void canCreateUnconnectedPrecedingTitle() {
    IndividualResource instanceResource = createInstance("Title One");
    String instanceId = instanceResource.getId().toString();
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));

    PrecedingSucceedingTitle precedingSucceedingTitle = new PrecedingSucceedingTitle(
      null, instanceId, TITLE, HRID, identifiers);
    IndividualResource response = precedingSucceedingTitleClient.create(precedingSucceedingTitle.getJson());

    assertPrecedingSucceedingTitle(response, null, instanceId, TITLE, HRID, identifiers);
  }

  @Test
  public void canCreateUnconnectedSucceedingTitle() {
    IndividualResource instanceResource = createInstance("Title One");
    String instanceId = instanceResource.getId().toString();
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));

    PrecedingSucceedingTitle precedingSucceedingTitle = new PrecedingSucceedingTitle(
      instanceId, null, TITLE, HRID, identifiers);
    IndividualResource response = precedingSucceedingTitleClient.create(precedingSucceedingTitle.getJson());

    assertPrecedingSucceedingTitle(response, instanceId, null, TITLE, HRID, identifiers);
  }

  @Test
  public void canUpdateConnectedPrecedingSucceedingTitle() {
    IndividualResource instance1Resource = createInstance("Title One");
    IndividualResource instance2Resource = createInstance("Title Two");
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
  public void canUpdateUnconnectedPrecedingSucceedingTitle() {
    IndividualResource instance1Resource = createInstance("Title One");
    String instance1Id = instance1Resource.getId().toString();
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));

    PrecedingSucceedingTitle precedingSucceedingTitle = new PrecedingSucceedingTitle(
      instance1Id, null, TITLE, HRID, identifiers);

    IndividualResource response = precedingSucceedingTitleClient.create(precedingSucceedingTitle.getJson());

    IndividualResource instance2Resource = createInstance("Title Two");
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
  public void canDeletePrecedingSucceedingTitle() {
    IndividualResource instanceResource = createInstance("Title One");
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
  public void canGetPrecedingSucceedingTitleByQuery() {
    IndividualResource instance1Resource = createInstance("Title One");
    IndividualResource instance2Resource = createInstance("Title Two");
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
  public void cannotCreatePrecedingSucceedingTitleWithNonExistingPrecedingInstance() {
    String nonExistingInstanceId = "14b65645-2e49-4a85-8dc1-43d444710570";
    IndividualResource instanceResource = createInstance("Title One");

    PrecedingSucceedingTitle precedingSucceedingTitle = new PrecedingSucceedingTitle(
      nonExistingInstanceId, instanceResource.getId().toString(), null, null, null);
    Response response = precedingSucceedingTitleClient.attemptToCreate(precedingSucceedingTitle
      .getJson());

    assertThat(response.getStatusCode(), is(HttpResponseStatus.UNPROCESSABLE_ENTITY.code()));
    assertErrors(response, "Cannot set preceding_succeeding_title.precedinginstanceid = " +
      "14b65645-2e49-4a85-8dc1-43d444710570 because it does not exist in instance.id.");
  }

  @Test
  public void cannotCreatePrecedingSucceedingTitleWithNonExistingSucceedingInstance() {
    String nonExistingInstanceId = "14b65645-2e49-4a85-8dc1-43d444710570";

    IndividualResource instance1Response = createInstance("Title One");
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
  public void cannotCreatePrecedingSucceedingTitleWithEmptyPrecedingAndSucceedingInstanceId() {
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
  public void cannotGetByInvalidPrecedingSucceedingId() {
    Response badParameterResponse = precedingSucceedingTitleClient.getByIdIfPresent("abc");
    assertThat(badParameterResponse.getStatusCode(), is(HttpResponseStatus.UNPROCESSABLE_ENTITY.code()));
    assertErrors(badParameterResponse, INVALID_UUID_ERROR_MESSAGE);
  }

  @Test
  public void cannotPutByInvalidPrecedingSucceedingId() {
    IndividualResource instance1Resource = createInstance("Title One");

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
  public void cannotDeleteByInvalidPrecedingSucceedingId() {
    Response badParameterResponse = precedingSucceedingTitleClient.deleteIfPresent("abc");
    assertThat(badParameterResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    assertThat(badParameterResponse.getBody(), is(INVALID_UUID_ERROR_MESSAGE));
  }

  @Test
  public void canUpdatePrecedingSucceedingTitleCollection() throws Exception {
    IndividualResource instance1Resource = createInstance("Title One");
    String instanceId = instance1Resource.getId().toString();

    PrecedingSucceedingTitle precedingSucceedingTitle1 = new PrecedingSucceedingTitle(
      instanceId, null, null, null, null);

    precedingSucceedingTitleClient.create(precedingSucceedingTitle1.getJson());

    PrecedingSucceedingTitle precedingSucceedingTitle2 = new PrecedingSucceedingTitle(
      instanceId, null, null, null, null);

    precedingSucceedingTitleClient.create(precedingSucceedingTitle2.getJson());

    precedingSucceedingTitle1.put(PRECEDING_INSTANCE_ID_KEY, null);
    precedingSucceedingTitle1.put(SUCCEEDING_INSTANCE_ID_KEY, instanceId);

    precedingSucceedingTitle2.put(PRECEDING_INSTANCE_ID_KEY, null);
    precedingSucceedingTitle2.put(SUCCEEDING_INSTANCE_ID_KEY, instanceId);

    var titles =
      new PrecedingSucceedingTitles(List.of(precedingSucceedingTitle1, precedingSucceedingTitle2));
    CompletableFuture<Response> putCompleted = new CompletableFuture<>();
    getClient().put(precedingSucceedingTitleUrl("/instances/" + instanceId), titles.getJson(),
      TENANT_ID, ResponseHandler.empty(putCompleted));
    Response response = putCompleted.get(10, SECONDS);
    assertThat(response.getStatusCode(), is(204));
    var existedTitles = precedingSucceedingTitleClient
      .getByQuery(
        String.format("?query=succeedingInstanceId==(%s)+or+precedingInstanceId==(%s)", instanceId, instanceId));
    existedTitles.forEach(entry -> {
      assertThat(entry.getString("succeedingInstanceId"), equalTo(instanceId));
      assertThat(entry.getString("precedingInstanceId"), nullValue());
    });
    precedingSucceedingTitleClient.delete(UUID.fromString(existedTitles.get(0).getString("id")));
    precedingSucceedingTitleClient.delete(UUID.fromString(existedTitles.get(1).getString("id")));
  }

  @Test
  public void failedUpdatePrecedingSucceedingTitleCollectionWhenInstanceIsMissing() throws Exception {
    String missedInstanceId = UUID.randomUUID().toString();
    PrecedingSucceedingTitle precedingSucceedingTitle1 = new PrecedingSucceedingTitle(
      missedInstanceId, null, null, null, null);

    PrecedingSucceedingTitle precedingSucceedingTitle2 = new PrecedingSucceedingTitle(
      null, missedInstanceId, null, null, null);

    var titles =
      new PrecedingSucceedingTitles(List.of(precedingSucceedingTitle1, precedingSucceedingTitle2));
    CompletableFuture<Response> putCompleted = new CompletableFuture<>();
    getClient().put(precedingSucceedingTitleUrl("/instances/" + missedInstanceId), titles.getJson(),
      TENANT_ID, ResponseHandler.any(putCompleted));
    Response response = putCompleted.get(10, SECONDS);
    assertThat(response.getStatusCode(), is(404));
    assertThat(response.getBody(), is("Instance not found"));
  }

  @Test
  public void failedUpdatePrecedingSucceedingTitleCollectionWhenTitleNotContainsInstanceId() throws Exception {
    String instanceId = UUID.randomUUID().toString();
    PrecedingSucceedingTitle precedingSucceedingTitle = new PrecedingSucceedingTitle(
      null, null, null, null, null);

    var titles =
      new PrecedingSucceedingTitles(List.of(precedingSucceedingTitle));
    CompletableFuture<Response> putCompleted = new CompletableFuture<>();
    getClient().put(precedingSucceedingTitleUrl("/instances/" + instanceId), titles.getJson(),
      TENANT_ID, ResponseHandler.any(putCompleted));
    Response response = putCompleted.get(10, SECONDS);
    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getBody(),
      containsString("The precedingInstanceId or succeedingInstanceId should contain instanceId"));
  }

  private IndividualResource createInstance(String title) {
    JsonObject instanceRequest = createInstanceRequest(UUID.randomUUID(), "TEST",
      title, new JsonArray(), new JsonArray(), UUID_INSTANCE_TYPE, new JsonArray());

    return instancesClient.create(instanceRequest);
  }

  private void assertErrors(Response response, String message) {
    Errors errors = response.getJson().mapTo(Errors.class);
    assertThat(errors.getErrors().get(0).getMessage(), is(message));
  }

  private void assertPrecedingSucceedingTitle(IndividualResource response,
    String precedingSucceedingTitleId, String succeedingTitleId, String title,
    String hrid, JsonArray identifiers) {

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
