package org.folio.rest.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.support.HttpResponseMatchers.errorMessageContains;
import static org.folio.rest.support.HttpResponseMatchers.statusCodeIs;
import static org.folio.rest.support.JsonObjectMatchers.hasSoleMessgeContaining;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageSyncUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.junit.Assert.assertThat;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import org.apache.commons.lang.StringUtils;
import org.folio.HttpStatus;
import org.folio.rest.support.AdditionalHttpStatusCodes;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonArrayHelper;
import org.folio.rest.support.JsonErrorResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class HoldingsStorageTest extends TestBaseWithInventoryUtil {
  private static final String TAG_VALUE = "test-tag";
  public static final String NEW_TEST_TAG = "new test tag";

  // see also @BeforeClass TestBaseWithInventoryUtil.beforeAny()

  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));
  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs("holdings_record");
  }

  @Test
  public void canCreateAHolding()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();

    JsonObject holding = holdingsClient.create(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))).getJson();

    assertThat(holding.getString("id"), is(holdingId.toString()));
    assertThat(holding.getString("instanceId"), is(instanceId.toString()));
    assertThat(holding.getString("permanentLocationId"), is(mainLibraryLocationId.toString()));

    Response getResponse = holdingsClient.getById(holdingId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject holdingFromGet = getResponse.getJson();

    assertThat(holdingFromGet.getString("id"), is(holdingId.toString()));
    assertThat(holdingFromGet.getString("instanceId"), is(instanceId.toString()));
    assertThat(holdingFromGet.getString("permanentLocationId"), is(mainLibraryLocationId.toString()));

    List<String> tags = holdingFromGet.getJsonObject("tags").getJsonArray("tagList").getList();

    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
  }

  @Test
  public void canCreateAHoldingWithoutProvidingAnId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    IndividualResource holdingResponse = holdingsClient.create(new HoldingRequestBuilder()
      .withId(null)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE))));

    JsonObject holding = holdingResponse.getJson();

    assertThat(holding.getString("id"), is(notNullValue()));
    assertThat(holding.getString("instanceId"), is(instanceId.toString()));
    assertThat(holding.getString("permanentLocationId"), is(mainLibraryLocationId.toString()));

    UUID holdingId = holdingResponse.getId();

    Response getResponse = holdingsClient.getById(holdingId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject holdingFromGet = getResponse.getJson();

    assertThat(holdingFromGet.getString("id"), is(holdingId.toString()));
    assertThat(holdingFromGet.getString("instanceId"), is(instanceId.toString()));
    assertThat(holdingFromGet.getString("permanentLocationId"), is(mainLibraryLocationId.toString()));

    List<String> tags = holdingFromGet.getJsonObject("tags").getJsonArray("tagList").getList();

    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
  }

  @Test
  public void cannotCreateAHoldingWithIDThatIsNotUUID()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    String nonUuidId = "6556456";

    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    JsonObject request = new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId).create();

    request.put("id", nonUuidId);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(holdingsStorageUrl(""), request, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(422));
    JsonArray errors = response.getJson().getJsonArray("errors");
    assertThat(errors.size(), is(1));

    JsonObject firstError = errors.getJsonObject(0);
    assertThat(firstError.getString("message"), containsString("must match"));
    assertThat(firstError.getJsonArray("parameters").getJsonObject(0).getString("key"),
      is("id"));
  }

  @Test
  public void canCreateAHoldingAtSpecificLocation()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();

    holdingsClient.replace(holdingId, new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))
      .withPermanentLocation(mainLibraryLocationId));

    Response getResponse = holdingsClient.getById(holdingId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject holdingFromGet = getResponse.getJson();

    assertThat(holdingFromGet.getString("id"), is(holdingId.toString()));
    assertThat(holdingFromGet.getString("instanceId"), is(instanceId.toString()));
    assertThat(holdingFromGet.getString("permanentLocationId"), is(mainLibraryLocationId.toString()));

    List<String> tags = holdingFromGet.getJsonObject("tags").getJsonArray("tagList").getList();

    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
  }

  @Test
  @Ignore("Schema does not have additional properties set to false")
  public void cannotProvideAdditionalPropertiesInAHolding()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    JsonObject request = new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId).create();

    request.put("somethingAdditional", "foo");

    CompletableFuture<JsonErrorResponse> createCompleted = new CompletableFuture<>();

    client.post(holdingsStorageUrl(""), request, StorageTestSuite.TENANT_ID,
      ResponseHandler.jsonErrors(createCompleted));

    JsonErrorResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
    assertThat(response.getErrors(), hasSoleMessgeContaining("Unrecognized field"));
  }

  @Test
  public void canReplaceAHoldingAtSpecificLocation()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    IndividualResource holdingResource = holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))
      .withPermanentLocation(mainLibraryLocationId));

    UUID holdingId = holdingResource.getId();

    JsonObject replacement = holdingResource.copyJson()
      .put("permanentLocationId", annexLibraryLocationId.toString())
      .put("tags", new JsonObject().put("tagList", new JsonArray().add(NEW_TEST_TAG)));

    holdingsClient.replace(holdingId, replacement);

    Response getResponse = holdingsClient.getById(holdingId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject holdingFromGet = getResponse.getJson();

    assertThat(holdingFromGet.getString("id"), is(holdingId.toString()));
    assertThat(holdingFromGet.getString("instanceId"), is(instanceId.toString()));
    assertThat(holdingFromGet.getString("permanentLocationId"), is(annexLibraryLocationId.toString()));

    List<String> tags = holdingFromGet.getJsonObject("tags").getJsonArray("tagList").getList();

    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(NEW_TEST_TAG));
  }

  @Test
  public void canDeleteAHolding()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    IndividualResource holdingResource = holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId));

    UUID holdingId = holdingResource.getId();

    holdingsClient.delete(holdingId);

    Response getResponse = holdingsClient.getById(holdingId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canGetAllHoldings()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID firstInstanceId = UUID.randomUUID();
    UUID secondInstanceId = UUID.randomUUID();
    UUID thirdInstanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(firstInstanceId));
    instancesClient.create(nod(secondInstanceId));
    instancesClient.create(uprooted(thirdInstanceId));

    UUID firstHoldingId = holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(firstInstanceId)
      .withPermanentLocation(mainLibraryLocationId)).getId();

    UUID secondHoldingId = holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(secondInstanceId)
      .withPermanentLocation(annexLibraryLocationId)).getId();

    UUID thirdHoldingId = holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(thirdInstanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))).getId();

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(holdingsStorageUrl(""), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    Response response = getCompleted.get(5, TimeUnit.SECONDS);

    JsonObject responseBody = response.getJson();

    List<JsonObject> allHoldings = JsonArrayHelper.toList(
      responseBody.getJsonArray("holdingsRecords"));

    assertThat(allHoldings.size(), is(3));
    assertThat(responseBody.getInteger("totalRecords"), is(3));

    assertThat(allHoldings.stream().anyMatch(filterById(firstHoldingId)), is(true));
    assertThat(allHoldings.stream().anyMatch(filterById(secondHoldingId)), is(true));
    assertThat(allHoldings.stream().anyMatch(filterById(thirdHoldingId)), is(true));
  }

  @Test
  public void cannotPageWithNegativeLimit() throws Exception {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)).getId();

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(holdingsStorageUrl("?limit=-3"), StorageTestSuite.TENANT_ID,
      ResponseHandler.text(getCompleted));

    Response response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody().trim(),
      is("'limit' parameter is incorrect. parameter value {-3} is not valid: must be greater than or equal to 0"));
  }

  @Test
  public void cannotPageWithNegativeOffset() throws Exception {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)).getId();

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(holdingsStorageUrl("?offset=-3"), StorageTestSuite.TENANT_ID,
      ResponseHandler.text(getCompleted));

    Response response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody().trim(),
      is("'offset' parameter is incorrect. parameter value {-3} is not valid: must be greater than or equal to 0"));
  }

  @Test
  public void cannotDeleteHoldingWhenLangParameterIsTooLong() throws Exception {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)).getId();

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.delete(holdingsStorageUrl("/" + holdingId + "?lang=eng"),
      StorageTestSuite.TENANT_ID, ResponseHandler.text(getCompleted));

    Response response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(),
      containsString("'lang' parameter is incorrect."));
  }

  @Test
  public void canPageAllHoldings()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID firstInstanceId = UUID.randomUUID();
    UUID secondInstanceId = UUID.randomUUID();
    UUID thirdInstanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(firstInstanceId));
    instancesClient.create(nod(secondInstanceId));
    instancesClient.create(uprooted(thirdInstanceId));

    UUID firstHoldingId = holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(firstInstanceId)
      .withPermanentLocation(mainLibraryLocationId)).getId();

    UUID secondHoldingId = holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(secondInstanceId)
      .withPermanentLocation(annexLibraryLocationId)).getId();

    UUID thirdHoldingId = holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(thirdInstanceId)
      .withPermanentLocation(mainLibraryLocationId)).getId();

    UUID fourthHoldingId = holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(secondInstanceId)
      .withPermanentLocation(mainLibraryLocationId)).getId();

    UUID fifthHoldingId = holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(firstInstanceId)
      .withPermanentLocation(annexLibraryLocationId)).getId();

    CompletableFuture<Response> firstPageCompleted = new CompletableFuture<>();
    CompletableFuture<Response> secondPageCompleted = new CompletableFuture<>();

    client.get(holdingsStorageUrl("") + "?limit=3", StorageTestSuite.TENANT_ID,
      ResponseHandler.json(firstPageCompleted));

    client.get(holdingsStorageUrl("") + "?limit=3&offset=3", StorageTestSuite.TENANT_ID,
      ResponseHandler.json(secondPageCompleted));

    Response firstPageResponse = firstPageCompleted.get(5, TimeUnit.SECONDS);
    Response secondPageResponse = secondPageCompleted.get(5, TimeUnit.SECONDS);

    assertThat(firstPageResponse.getStatusCode(), is(200));
    assertThat(secondPageResponse.getStatusCode(), is(200));

    JsonObject firstPage = firstPageResponse.getJson();
    JsonObject secondPage = secondPageResponse.getJson();

    List<JsonObject> firstPageHoldings = JsonArrayHelper.toList(
      firstPage.getJsonArray("holdingsRecords"));

    List<JsonObject> secondPageHoldings = JsonArrayHelper.toList(
      secondPage.getJsonArray("holdingsRecords"));

    assertThat(firstPageHoldings.size(), is(3));
    assertThat(firstPage.getInteger("totalRecords"), is(5));

    assertThat(secondPageHoldings.size(), is(2));
    assertThat(secondPage.getInteger("totalRecords"), is(5));
  }

  @Test
  public void canDeleteAllHoldings()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID firstInstanceId = UUID.randomUUID();
    UUID secondInstanceId = UUID.randomUUID();
    UUID thirdInstanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(firstInstanceId));
    instancesClient.create(nod(secondInstanceId));
    instancesClient.create(uprooted(thirdInstanceId));

    holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(firstInstanceId)
      .withPermanentLocation(mainLibraryLocationId));

    holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(secondInstanceId)
      .withPermanentLocation(annexLibraryLocationId));

    holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(thirdInstanceId)
      .withPermanentLocation(mainLibraryLocationId));

    holdingsClient.deleteAll();

    List<JsonObject> allHoldings = holdingsClient.getAll();

    assertThat(allHoldings.size(), is(0));
  }

  @Test
  public void tenantIsRequiredForCreatingANewHolding()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    JsonObject request = new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId).create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(holdingsStorageUrl(""), request, null, ResponseHandler.any(postCompleted));

    Response response = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @Test
  public void tenantIsRequiredForGettingAHolding()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID id = holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)).getId();

    URL getHoldingUrl = holdingsStorageUrl(String.format("/%s", id.toString()));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(getHoldingUrl, null, ResponseHandler.any(getCompleted));

    Response response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @Test
  public void tenantIsRequiredForGettingAllHoldings()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(holdingsStorageUrl(""), null, ResponseHandler.any(getCompleted));

    Response response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @Test
  public void cannotCreateHoldingWithoutPermanentLocation() throws Exception {
    UUID instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();
    JsonObject holdingsRecord = new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withPermanentLocation(null)
      .create();

    client.post(holdingsStorageUrl(""), holdingsRecord,
      StorageTestSuite.TENANT_ID, ResponseHandler.json(postCompleted));

    Response response = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(422));

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertThat(errors.size(), is(1));

    JsonObject firstError = errors.getJsonObject(0);
    assertThat(firstError.getString("message"), is("may not be null"));
    assertThat(firstError.getJsonArray("parameters").getJsonObject(0).getString("key"),
      is("permanentLocationId"));
  }

  /**
   * Create three instances, and for each of them return a JsonObject of a new holding belonging to it.
   * The holdings are not created.
   */
  private JsonArray threeHoldings() {
    JsonArray holdingsArray = new JsonArray();
    for (int i=0; i<3; i++) {
      UUID instanceId = UUID.randomUUID();
      try {
        instancesClient.create(smallAngryPlanet(instanceId));
      } catch (MalformedURLException | InterruptedException | ExecutionException | TimeoutException e) {
        throw new RuntimeException(e);
      }
      holdingsArray.add(new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("instanceId", instanceId.toString())
          .put("permanentLocationId", mainLibraryLocationId.toString()));
    }
    return holdingsArray;
  }

  private Response postSync(JsonArray holdingsArray) {
    JsonObject holdingsCollection = new JsonObject().put("holdingsRecords", holdingsArray);
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    client.post(holdingsStorageSyncUrl(""), holdingsCollection, TENANT_ID, ResponseHandler.any(createCompleted));
    try {
      return createCompleted.get(5, SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void canSyncPost() {
    JsonArray holdingsArray = threeHoldings();
    assertThat(postSync(holdingsArray), statusCodeIs(HttpStatus.HTTP_CREATED));
    for (Object holding : holdingsArray) {
      assertExists((JsonObject) holding);
    }
  }

  @Test
  public void cannoteSyncPostWithDuplicateId() {
    JsonArray holdingsArray = threeHoldings();
    holdingsArray.getJsonObject(1).put("id", holdingsArray.getJsonObject(0).getString("id"));
    Response response = postSync(holdingsArray);
    assertThat(response, statusCodeIs(HttpStatus.HTTP_UNPROCESSABLE_ENTITY));
    assertThat(response, errorMessageContains("duplicate key"));

    for (int i=0; i<holdingsArray.size(); i++) {
      assertGetNotFound(holdingsStorageUrl("/" + holdingsArray.getJsonObject(i).getString("id")));
    }
  }

  private JsonObject smallAngryPlanet(UUID id) {
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));
    JsonArray contributors = new JsonArray();
    contributors.add(contributor(UUID_PERSONAL_NAME, "Chambers, Becky"));
    JsonArray tags = new JsonArray();
    tags.add("test-tag");

    return createInstanceRequest(id, "TEST", "Long Way to a Small Angry Planet",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags);
  }

  private JsonObject nod(UUID id) {
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ASIN, "B01D1PLMDO"));

    JsonArray contributors = new JsonArray();
    contributors.add(contributor(UUID_PERSONAL_NAME, "Barnes, Adrian"));

    JsonArray tags = new JsonArray();
    tags.add("test-tag");
    return createInstanceRequest(id, "TEST", "Nod",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags);
  }

  private JsonObject uprooted(UUID id) {
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "1447294149"));
    identifiers.add(identifier(UUID_ISBN, "9781447294146"));

    JsonArray contributors = new JsonArray();
    contributors.add(contributor(UUID_PERSONAL_NAME, "Novik, Naomi"));

    JsonArray tags = new JsonArray();
    tags.add("test-tag");

    return createInstanceRequest(id, "TEST", "Uprooted",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags);
  }

  private Predicate<JsonObject> filterById(UUID holdingId) {
    return holding -> StringUtils.equals(holding.getString("id"), holdingId.toString());
  }

  private Response getById(String id) {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(holdingsStorageUrl("/" + id), TENANT_ID, json(getCompleted));
    try {
      return getCompleted.get(5, SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private void assertExists(JsonObject expectedHolding) {
    Response response = getById(expectedHolding.getString("id"));
    assertThat(response, statusCodeIs(HttpStatus.HTTP_OK));
    assertThat(response.getBody(), containsString(expectedHolding.getString("instanceId")));
  }
}
