package org.folio.rest.api;

import static org.folio.rest.support.JsonObjectMatchers.hasSoleMessgeContaining;
import static org.folio.rest.support.JsonObjectMatchers.validationErrorMatches;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.util.StringUtil.urlEncode;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Items;
import org.folio.rest.support.AdditionalHttpStatusCodes;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonArrayHelper;
import org.folio.rest.support.JsonErrorResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ItemStorageTest extends TestBaseWithInventoryUtil {

  private static final String TAG_VALUE = "test-tag";

  // see also @BeforeClass TestBaseWithInventoryUtil.beforeAny()

  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));
  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs("item");
  }

  @Test
  public void canCreateAnItemViaCollectionResource()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    UUID id = UUID.randomUUID();
    final String inTransitServicePointId = UUID.randomUUID().toString();

    JsonObject itemToCreate = new JsonObject();

    itemToCreate.put("id", id.toString());
    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("barcode", "565578437802");
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("materialTypeId", journalMaterialTypeID);
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", annexLibraryLocationId.toString());
    itemToCreate.put("tags", new JsonObject().put("tagList",new JsonArray().add(TAG_VALUE)));

    //TODO: Replace with real service point when validated
    itemToCreate.put("inTransitDestinationServicePointId", inTransitServicePointId);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(itemsStorageUrl(""), itemToCreate, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject itemFromPost = postResponse.getJson();

    assertThat(itemFromPost.getString("id"), is(id.toString()));
    assertThat(itemFromPost.getString("holdingsRecordId"), is(holdingsRecordId.toString()));
    assertThat(itemFromPost.getString("barcode"), is("565578437802"));
    assertThat(itemFromPost.getJsonObject("status").getString("name"),
      is("Available"));
    assertThat(itemFromPost.getString("materialTypeId"),
      is(journalMaterialTypeID));
    assertThat(itemFromPost.getString("permanentLoanTypeId"),
      is(canCirculateLoanTypeID));
    assertThat(itemFromPost.getString("temporaryLocationId"),
      is(annexLibraryLocationId.toString()));
    assertThat(itemFromPost.getString("inTransitDestinationServicePointId"),
      is(inTransitServicePointId));

    Response getResponse = getById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject itemFromGet = getResponse.getJson();

    assertThat(itemFromGet.getString("id"), is(id.toString()));
    assertThat(itemFromGet.getString("holdingsRecordId"), is(holdingsRecordId.toString()));
    assertThat(itemFromGet.getString("barcode"), is("565578437802"));
    assertThat(itemFromGet.getJsonObject("status").getString("name"),
      is("Available"));
    assertThat(itemFromGet.getString("materialTypeId"),
      is(journalMaterialTypeID));
    assertThat(itemFromGet.getString("permanentLoanTypeId"),
      is(canCirculateLoanTypeID));
    assertThat(itemFromGet.getString("temporaryLocationId"),
      is(annexLibraryLocationId.toString()));
    assertThat(itemFromPost.getString("inTransitDestinationServicePointId"),
      is(inTransitServicePointId));

    List<String> tags = itemFromGet.getJsonObject("tags").getJsonArray("tagList").getList();

    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
  }

  @Test
  public void canCreateAnItemWithMinimalProperties()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    UUID id = UUID.randomUUID();

    JsonObject itemToCreate = new JsonObject()
      .put("id", id.toString())
      .put("holdingsRecordId", holdingsRecordId.toString())
      .put("materialTypeId", journalMaterialTypeID)
      .put("permanentLoanTypeId", canCirculateLoanTypeID)
      .put("tags", new JsonObject().put("tagList",new JsonArray().add(TAG_VALUE)));

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(itemsStorageUrl(""), itemToCreate, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create item: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject itemFromPost = postResponse.getJson();

    assertThat(itemFromPost.getString("id"), is(id.toString()));

    Response getResponse = getById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject itemFromGet = getResponse.getJson();

    assertThat(itemFromGet.getString("id"), is(id.toString()));
    assertThat(itemFromGet.getJsonObject("status").getString("name"), is("Available"));

    List<String> tags = itemFromGet.getJsonObject("tags").getJsonArray("tagList").getList();

    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
  }

  @Test
  public void canCreateAnItemWithoutProvidingID()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    JsonObject itemToCreate = nod(null, holdingsRecordId);

    itemToCreate.put("tags", new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)));

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(itemsStorageUrl(""), itemToCreate, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject itemFromPost = postResponse.getJson();

    String newId = itemFromPost.getString("id");

    assertThat(newId, is(notNullValue()));

    Response getResponse = getById(UUID.fromString(newId));

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject itemFromGet = getResponse.getJson();

    assertThat(itemFromGet.getString("id"), is(newId));
    assertThat(itemFromGet.getString("holdingsRecordId"), is(holdingsRecordId.toString()));
    assertThat(itemFromGet.getString("barcode"), is("565578437802"));
    assertThat(itemFromGet.getJsonObject("status").getString("name"),
      is("Available"));
    assertThat(itemFromGet.getString("materialTypeId"),
      is(journalMaterialTypeID));
    assertThat(itemFromGet.getString("permanentLoanTypeId"),
      is(canCirculateLoanTypeID));
    assertThat(itemFromGet.getString("temporaryLocationId"),
      is(annexLibraryLocationId.toString()));

    List<String> tags = itemFromGet.getJsonObject("tags").getJsonArray("tagList").getList();

    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
  }

  @Test
  public void cannotAddANonExistentPermanentLocation()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    String badLocation = UUID.randomUUID().toString();
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    String id = UUID.randomUUID().toString();

    JsonObject itemToCreate = new JsonObject()
      .put("id", id)
      .put("holdingsRecordId", holdingsRecordId.toString())
      .put("materialTypeId", journalMaterialTypeID)
      .put("permanentLoanTypeId", canCirculateLoanTypeID)
      .put("permanentLocationId", badLocation);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(itemsStorageUrl(""), itemToCreate, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));

    assertThat(postResponse.getBody(),
        containsString("Cannot set item.permanentlocationid"));
  }

  @Test
  public void cannotAddANonExistentTemporaryLocation()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    String badLocation = UUID.randomUUID().toString();
    String id = UUID.randomUUID().toString();

    JsonObject itemToCreate = new JsonObject()
      .put("id", id)
      .put("holdingsRecordId", holdingsRecordId.toString())
      .put("materialTypeId", journalMaterialTypeID)
      .put("permanentLoanTypeId", canCirculateLoanTypeID)
      .put("temporaryLocationId", badLocation);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(itemsStorageUrl(""), itemToCreate, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));

    assertThat(postResponse.getBody(),
        containsString("Cannot set item.temporarylocationid"));
  }

  @Test
  public void cannotCreateAnItemWithIDThatIsNotUUID()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    String id = "1234";
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    JsonObject itemToCreate = new JsonObject();

    itemToCreate.put("id", id);
    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("barcode", "565578437802");
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("materialTypeId", journalMaterialTypeID);
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", annexLibraryLocationId.toString());

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(itemsStorageUrl(""), itemToCreate, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));

    assertThat(postResponse.getBody(), containsString("UUID"));
  }

  @Test
  public void cannotCreateAnItemWithoutMaterialType()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    JsonObject itemToCreate = new JsonObject();

    UUID id = UUID.randomUUID();
    itemToCreate.put("id", id.toString());
    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(itemsStorageUrl(""), itemToCreate, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));

    List<JsonObject> errors = JsonArrayHelper.toList(
      postResponse.getJson().getJsonArray("errors"));

    assertThat(errors.size(), is(1));
    assertThat(errors, hasItem(
      validationErrorMatches("may not be null", "materialTypeId")));
  }

  @Test
  public void cannotCreateAnItemWithNonexistingMaterialType()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    JsonObject itemToCreate = new JsonObject();
    itemToCreate.put("id", UUID.randomUUID().toString());
    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("materialTypeId", UUID.randomUUID().toString());

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(itemsStorageUrl(""), itemToCreate, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
    assertThat(postResponse.getBody(),
        containsString("Cannot set item.materialtypeid"));
  }

  @Test
  public void cannotUpdateAnItemWithNonexistingMaterialType()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    JsonObject itemToCreate = new JsonObject();
    String itemId = UUID.randomUUID().toString();
    itemToCreate.put("id", itemId);
    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("materialTypeId", bookMaterialTypeID);
    createItem(itemToCreate);

    itemToCreate.put("materialTypeId", UUID.randomUUID().toString());

    CompletableFuture<Response> completed = new CompletableFuture<>();
    client.put(itemsStorageUrl("/" + itemId), itemToCreate, StorageTestSuite.TENANT_ID,
        ResponseHandler.text(completed));
    Response response = completed.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    assertThat(response.getBody(),
        containsString("Cannot set item.materialtypeid"));
  }

  @Test
  public void canCreateAnItemWithManyProperties()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    UUID id = UUID.randomUUID();

    final String inTransitServicePointId = UUID.randomUUID().toString();

    JsonObject itemToCreate = new JsonObject();

    itemToCreate.put("id", id.toString());
    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("barcode", "565578437802");
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("materialTypeId", journalMaterialTypeID);
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", annexLibraryLocationId.toString());
    itemToCreate.put("tags", new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)));

    //TODO: Replace with real service point when validated
    itemToCreate.put("inTransitDestinationServicePointId", inTransitServicePointId);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    client.post(itemsStorageUrl(""), itemToCreate,
      StorageTestSuite.TENANT_ID, ResponseHandler.empty(createCompleted));

    Response postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    Response getResponse = getById(id);

    //PUT currently cannot return a response
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject item = getResponse.getJson();

    List<String> tags = item.getJsonObject("tags").getJsonArray("tagList").getList();

    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("holdingsRecordId"), is(holdingsRecordId.toString()));
    assertThat(item.getString("barcode"), is("565578437802"));
    assertThat(item.getJsonObject("status").getString("name"), is("Available"));
    assertThat(item.getString("materialTypeId"), is(journalMaterialTypeID));
    assertThat(item.getString("permanentLoanTypeId"), is(canCirculateLoanTypeID));
    assertThat(item.getString("temporaryLocationId"), is(annexLibraryLocationId.toString()));
    assertThat(item.getString("inTransitDestinationServicePointId"), is(inTransitServicePointId));
    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
  }

  @Test
  public void cannotProvideAdditionalPropertiesInItem()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    JsonObject requestWithAdditionalProperty = nod(UUID.randomUUID(),holdingsRecordId);

    requestWithAdditionalProperty.put("somethingAdditional", "foo");

    CompletableFuture<JsonErrorResponse> createCompleted = new CompletableFuture<>();

    client.post(itemsStorageUrl(""), requestWithAdditionalProperty,
      StorageTestSuite.TENANT_ID, ResponseHandler.jsonErrors(createCompleted));

    JsonErrorResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
    assertThat(response.getErrors(), hasSoleMessgeContaining("Unrecognized field"));
  }

  @Test
  public void cannotProvideAdditionalPropertiesInItemStatus()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    JsonObject requestWithAdditionalProperty = nod(UUID.randomUUID(),holdingsRecordId);

    requestWithAdditionalProperty
      .put("status", new JsonObject().put("somethingAdditional", "foo"));

    CompletableFuture<JsonErrorResponse> createCompleted = new CompletableFuture<>();

    client.post(itemsStorageUrl(""), requestWithAdditionalProperty,
      StorageTestSuite.TENANT_ID, ResponseHandler.jsonErrors(createCompleted));

    JsonErrorResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
    assertThat(response.getErrors(), hasSoleMessgeContaining("Unrecognized field"));
  }

  //Test invalid due to data format change
  /*
  @Test
  public void cannotProvideAdditionalPropertiesInItemLocation()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    JsonObject requestWithAdditionalProperty = nod();

    requestWithAdditionalProperty
      .put("location", new JsonObject().put("somethingAdditional", "foo"));

    CompletableFuture<JsonErrorResponse> createCompleted = new CompletableFuture<>();

    client.post(itemsUrl(), requestWithAdditionalProperty,
      StorageTestSuite.TENANT_ID, ResponseHandler.jsonErrors(createCompleted));

    JsonErrorResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
    assertThat(response.getErrors(), hasSoleMessgeContaining("Unrecognized field"));
  }
*/

  @Test
  public void canReplaceAnItemAtSpecificLocation()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    UUID id = UUID.randomUUID();
    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId);

    createItem(itemToCreate);

    JsonObject replacement = itemToCreate.copy();
      replacement.put("barcode", "125845734657")
              .put("temporaryLocationId", mainLibraryLocationId.toString())
              .put("tags", new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)));


    CompletableFuture<Response> replaceCompleted = new CompletableFuture<>();

    client.put(itemsStorageUrl(String.format("/%s", id)), replacement,
      StorageTestSuite.TENANT_ID, ResponseHandler.empty(replaceCompleted));

    Response putResponse = replaceCompleted.get(5, TimeUnit.SECONDS);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse = getById(id);

    //PUT currently cannot return a response
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject item = getResponse.getJson();

    List<String> tags = item.getJsonObject("tags").getJsonArray("tagList").getList();

    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("holdingsRecordId"), is(holdingsRecordId.toString()));
    assertThat(item.getString("barcode"), is("125845734657"));
    assertThat(item.getJsonObject("status").getString("name"),
      is("Available"));
    assertThat(item.getString("materialTypeId"),
      is(journalMaterialTypeID));
    assertThat(item.getString("temporaryLocationId"),
      is(mainLibraryLocationId.toString()));
    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
  }

  @Test
  public void canPlaceAnItemInTransit()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    UUID id = UUID.randomUUID();
    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId);

    createItem(itemToCreate);

    final String inTransitServicePointId = UUID.randomUUID().toString();

    JsonObject replacement = itemToCreate.copy();

    replacement
      .put("status", new JsonObject().put("name", "In transit"))
      .put("inTransitDestinationServicePointId", inTransitServicePointId)
      .put("tags", new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)));

    CompletableFuture<Response> replaceCompleted = new CompletableFuture<>();

    client.put(itemsStorageUrl(String.format("/%s", id)), replacement,
      StorageTestSuite.TENANT_ID, ResponseHandler.empty(replaceCompleted));

    Response putResponse = replaceCompleted.get(5, TimeUnit.SECONDS);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse = getById(id);

    //PUT currently cannot return a response
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject item = getResponse.getJson();

    List<String> tags = item.getJsonObject("tags").getJsonArray("tagList").getList();

    assertThat(item.getString("id"), is(id.toString()));

    assertThat(item.getJsonObject("status").getString("name"),
      is("In transit"));

    assertThat(item.getString("inTransitDestinationServicePointId"),
      is(inTransitServicePointId));
    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
  }

  @Test
  public void checkIfStatusDateExistsWhenItemStatusUpdated()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    UUID id = UUID.randomUUID();
    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId);

    createItem(itemToCreate);

    final String inTransitServicePointId = UUID.randomUUID().toString();

    JsonObject replacement = itemToCreate.copy();

    replacement
      .put("status", new JsonObject().put("name", "Checked out"));

    CompletableFuture<Response> replaceCompleted = new CompletableFuture<>();

    client.put(itemsStorageUrl(String.format("/%s", id)), replacement,
      StorageTestSuite.TENANT_ID, ResponseHandler.empty(replaceCompleted));

    Response putResponse = replaceCompleted.get(5, TimeUnit.SECONDS);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse = getById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject item = getResponse.getJson();

    assertThat(item.getString("id"), is(id.toString()));

    assertThat(item.getJsonObject("status").getString("name"),
      is("Checked out"));

    assertThat(item.getJsonObject("status").getString("date"),
      notNullValue());
  }

  @Test
  public void checkIfStatusDateChangesWhenItemStatusUpdated()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    UUID id = UUID.randomUUID();
    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId);

    createItem(itemToCreate);

    final String inTransitServicePointId = UUID.randomUUID().toString();

    JsonObject replacement = itemToCreate.copy();

    replacement
      .put("status", new JsonObject().put("name", "Checked out"));

    CompletableFuture<Response> replaceCompleted = new CompletableFuture<>();

    client.put(itemsStorageUrl(String.format("/%s", id)), replacement,
      StorageTestSuite.TENANT_ID, ResponseHandler.empty(replaceCompleted));

    Response putResponse = replaceCompleted.get(5, TimeUnit.SECONDS);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse = getById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject item = getResponse.getJson();

    assertThat(item.getString("id"), is(id.toString()));

    assertThat(item.getJsonObject("status").getString("name"),
      is("Checked out"));

    assertThat(item.getJsonObject("status").getString("date"),
      notNullValue());

    String changedStatusDate = item.getJsonObject("status").getString("date");

    JsonObject secondReplacement = itemToCreate.copy();

    secondReplacement
      .put("status", new JsonObject().put("name", "Available"));

    CompletableFuture<Response> secondReplaceCompleted = new CompletableFuture<>();

    client.put(itemsStorageUrl(String.format("/%s", id)), secondReplacement,
      StorageTestSuite.TENANT_ID, ResponseHandler.empty(secondReplaceCompleted));

    Response secondPutResponse = secondReplaceCompleted.get(5, TimeUnit.SECONDS);

    assertThat(secondPutResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    getResponse = getById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject resultItem = getResponse.getJson();

    assertThat(resultItem.getString("id"), is(id.toString()));

    assertThat(resultItem.getJsonObject("status").getString("name"),
      is("Available"));

    assertThat(resultItem.getJsonObject("status").getString("date"),
      notNullValue());

    assertThat(resultItem.getJsonObject("status").getString("date"),
      not(changedStatusDate));
  }

  @Test
  public void canDeleteAnItem() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    UUID id = UUID.randomUUID();
    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId);

    createItem(itemToCreate);

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    client.delete(itemsStorageUrl(String.format("/%s", id)),
      StorageTestSuite.TENANT_ID, ResponseHandler.empty(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(itemsStorageUrl(String.format("/%s", id)),
      StorageTestSuite.TENANT_ID, ResponseHandler.empty(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canPageAllItems()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    createItem(smallAngryPlanet(UUID.randomUUID(), holdingsRecordId));
    createItem(nod(UUID.randomUUID(), holdingsRecordId));
    createItem(uprooted(UUID.randomUUID(), holdingsRecordId));
    createItem(temeraire(UUID.randomUUID(), holdingsRecordId));
    createItem(interestingTimes(UUID.randomUUID(), holdingsRecordId));

    CompletableFuture<Response> firstPageCompleted = new CompletableFuture<>();
    CompletableFuture<Response> secondPageCompleted = new CompletableFuture<>();

    client.get(itemsStorageUrl("") + "?limit=3", StorageTestSuite.TENANT_ID,
      ResponseHandler.json(firstPageCompleted));

    client.get(itemsStorageUrl("") + "?limit=3&offset=3", StorageTestSuite.TENANT_ID,
      ResponseHandler.json(secondPageCompleted));

    Response firstPageResponse = firstPageCompleted.get(5, TimeUnit.SECONDS);
    Response secondPageResponse = secondPageCompleted.get(5, TimeUnit.SECONDS);

    assertThat(firstPageResponse.getStatusCode(), is(200));
    assertThat(secondPageResponse.getStatusCode(), is(200));

    JsonObject firstPage = firstPageResponse.getJson();
    JsonObject secondPage = secondPageResponse.getJson();

    JsonArray firstPageItems = firstPage.getJsonArray("items");
    JsonArray secondPageItems = secondPage.getJsonArray("items");

    assertThat(firstPageItems.size(), is(3));
    assertThat(firstPage.getInteger("totalRecords"), is(5));

    assertThat(secondPageItems.size(), is(2));
    assertThat(secondPage.getInteger("totalRecords"), is(5));
  }

  @Test
  public void canSearchForItemsByBarcodeWithLeadingZero()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    createItem(nod(holdingsRecordId));
    createItem(uprooted(UUID.randomUUID(), holdingsRecordId));
    createItem(smallAngryPlanet(holdingsRecordId).put("barcode", "036000291452"));
    createItem(temeraire(UUID.randomUUID(), holdingsRecordId));
    createItem(interestingTimes(UUID.randomUUID(), holdingsRecordId));

    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

    String url = itemsStorageUrl("") + "?query=barcode==036000291452";

    client.get(url,
      StorageTestSuite.TENANT_ID, ResponseHandler.json(searchCompleted));

    Response searchResponse = searchCompleted.get(5, TimeUnit.SECONDS);

    assertThat(searchResponse.getStatusCode(), is(200));

    JsonObject searchBody = searchResponse.getJson();

    JsonArray foundItems = searchBody.getJsonArray("items");

    assertThat(foundItems.size(), is(1));
    assertThat(searchBody.getInteger("totalRecords"), is(1));

    assertThat(foundItems.getJsonObject(0).getString("barcode"),
      is("036000291452"));
  }

  @Test
  public void canSearchForItemsByBarcode()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    createItem(nod(holdingsRecordId));
    createItem(uprooted(UUID.randomUUID(), holdingsRecordId));
    createItem(smallAngryPlanet(holdingsRecordId).put("barcode", "673274826203"));
    createItem(temeraire(UUID.randomUUID(), holdingsRecordId));
    createItem(interestingTimes(UUID.randomUUID(), holdingsRecordId));

    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

    String url = itemsStorageUrl("") + "?query=barcode==673274826203";

    client.get(url,
      StorageTestSuite.TENANT_ID, ResponseHandler.json(searchCompleted));

    Response searchResponse = searchCompleted.get(5, TimeUnit.SECONDS);

    assertThat(searchResponse.getStatusCode(), is(200));

    JsonObject searchBody = searchResponse.getJson();

    JsonArray foundItems = searchBody.getJsonArray("items");

    assertThat(foundItems.size(), is(1));
    assertThat(searchBody.getInteger("totalRecords"), is(1));

    assertThat(foundItems.getJsonObject(0).getString("barcode"),
      is("673274826203"));
  }

  @Test
  public void canSearchForItemsByTags()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    createItem(addTags(TAG_VALUE, holdingsRecordId));
    createItem(nod(holdingsRecordId));

    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

    String url = itemsStorageUrl("") + "?query=" + urlEncode("tags.tagList=" + TAG_VALUE);

    client.get(url,
      StorageTestSuite.TENANT_ID, ResponseHandler.json(searchCompleted));

    Response searchResponse = searchCompleted.get(5, TimeUnit.SECONDS);

    assertThat(searchResponse.getStatusCode(), is(200));

    JsonObject searchBody = searchResponse.getJson();

    JsonArray foundItems = searchBody.getJsonArray("items");

    assertThat(searchBody.getInteger("totalRecords"), is(1));

    assertThat(foundItems.size(), is(1));

    assertTrue(searchResponse.getBody().contains(TAG_VALUE));

    LinkedHashMap item = (LinkedHashMap) foundItems.getList().get(0);
    LinkedHashMap<String, ArrayList<String>> itemTags = (LinkedHashMap<String, ArrayList<String>>) item.get("tags");

    assertThat(itemTags.get("tagList"), hasItem(TAG_VALUE));
  }

  @Test
  public void canSearchForItemsByStatus()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    createItem(smallAngryPlanet(UUID.randomUUID(), holdingsRecordId));
    createItem(nod(UUID.randomUUID(), holdingsRecordId));
    createItem(uprooted(UUID.randomUUID(), holdingsRecordId));
    createItem(temeraire(UUID.randomUUID(), holdingsRecordId));
    createItem(interestingTimes(UUID.randomUUID(), holdingsRecordId));

    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

    String url = itemsStorageUrl("") + "?query=" + urlEncode("status.name==\"Available\"");

    client.get(url,
      StorageTestSuite.TENANT_ID, ResponseHandler.json(searchCompleted));

    Response searchResponse = searchCompleted.get(5, TimeUnit.SECONDS);

    assertThat(searchResponse.getStatusCode(), is(200));

    JsonObject searchBody = searchResponse.getJson();

    JsonArray foundItems = searchBody.getJsonArray("items");

    assertThat(searchBody.getInteger("totalRecords"), is(5));

    assertThat(foundItems.size(), is(5));
  }

  @Test
  public void cannotSearchForItemsByBarcodeAndNotMatchingId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    createItem(nod(holdingsRecordId));
    createItem(uprooted(UUID.randomUUID(), holdingsRecordId));
    createItem(smallAngryPlanet(holdingsRecordId).put("barcode", "673274826203"));
    createItem(temeraire(UUID.randomUUID(), holdingsRecordId));
    createItem(interestingTimes(UUID.randomUUID(), holdingsRecordId));

    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

    String url = itemsStorageUrl("") + "?query=" + urlEncode(String.format(
      "barcode==\"673274826203\" and id<>%s'", UUID.randomUUID()));

    client.get(url,
      StorageTestSuite.TENANT_ID, ResponseHandler.json(searchCompleted));

    Response searchResponse = searchCompleted.get(5, TimeUnit.SECONDS);

    assertThat(searchResponse.getStatusCode(), is(200));
    JsonObject searchBody = searchResponse.getJson();

    JsonArray foundItems = searchBody.getJsonArray("items");

    assertThat(foundItems.size(), is(1));
    assertThat(searchBody.getInteger("totalRecords"), is(1));

    assertThat(foundItems.getJsonObject(0).getString("barcode"),
      is("673274826203"));
  }

  @Test
  public void canSearchForManyItemsByBarcode() throws Exception {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    createItem(smallAngryPlanet(holdingsRecordId)
      .put("barcode", "673274826203")
      .put("tags", new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE))));

    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

    // StackOverflowError in java.util.regex.Pattern https://issues.folio.org/browse/CIRC-119
    String url = itemsStorageUrl("") + "?query=" + urlEncode("barcode==("
        + "a or b or c or d or e or f or g or h or j or k or l or m or n or o or p or q or s or t or u or v or w or x or y or z or "
        + "673274826203)");

    client.get(url,
        StorageTestSuite.TENANT_ID, ResponseHandler.json(searchCompleted));

    Response searchResponse = searchCompleted.get(5, TimeUnit.SECONDS);
    JsonObject searchBody = searchResponse.getJson();
    JsonArray foundItems = searchBody.getJsonArray("items");
    assertThat(foundItems.size(), is(1));
    assertThat(searchBody.getInteger("totalRecords"), is(1));
    assertThat(foundItems.getJsonObject(0).getString("barcode"), is("673274826203"));

    LinkedHashMap item = (LinkedHashMap) foundItems.getList().get(0);
    LinkedHashMap<String, ArrayList<String>> itemTags = (LinkedHashMap<String, ArrayList<String>>) item.get("tags");

    assertThat(itemTags.get("tagList"), hasItem(TAG_VALUE));
  }

  @Test
  public void canSearchItemByEffectiveLocation() throws Exception {
    UUID holdingsWithPermLocation = createInstanceAndHolding(mainLibraryLocationId);
    UUID holdingsWithTempLocation = createInstanceAndHolding(mainLibraryLocationId, annexLibraryLocationId);

    Item itemWithHoldingPermLocation = buildItem(holdingsWithPermLocation, null, null);
    Item itemWithHoldingTempLocation = buildItem(holdingsWithTempLocation, null, null);
    Item itemWithTempLocation = buildItem(holdingsWithPermLocation, onlineLocationId, null);
    Item itemWithPermLocation = buildItem(holdingsWithTempLocation, null, secondFloorLocationId);
    Item itemWithAllLocation = buildItem(holdingsWithTempLocation, secondFloorLocationId, onlineLocationId);

    Item[] itemsToCreate = {itemWithHoldingPermLocation, itemWithHoldingTempLocation,
      itemWithTempLocation, itemWithPermLocation, itemWithAllLocation};

    for (Item item : itemsToCreate) {
      IndividualResource createdItem = createItem(item);
      assertTrue(createdItem.getJson().containsKey("effectiveLocationId"));
    }

    Items mainLibraryItems = findItems("effectiveLocationId=" + mainLibraryLocationId);
    Items annexLibraryItems = findItems("effectiveLocationId=" + annexLibraryLocationId);
    Items onlineLibraryItems = findItems("effectiveLocationId=" + onlineLocationId);
    Items secondFloorLibraryItems = findItems("effectiveLocationId=" + secondFloorLocationId);

    assertEquals(1, mainLibraryItems.getTotalRecords().intValue());
    assertThat(mainLibraryItems.getItems().get(0).getId(), is(itemWithHoldingPermLocation.getId()));

    assertEquals(1, annexLibraryItems.getTotalRecords().intValue());
    assertThat(annexLibraryItems.getItems().get(0).getId(), is(itemWithHoldingTempLocation.getId()));

    assertEquals(2, onlineLibraryItems.getTotalRecords().intValue());

    assertThat(onlineLibraryItems.getItems()
        .stream()
        .map(Item::getId)
        .collect(Collectors.toList()),
      hasItems(itemWithTempLocation.getId(), itemWithAllLocation.getId()));

    assertEquals(1, secondFloorLibraryItems.getTotalRecords().intValue());
    assertThat(secondFloorLibraryItems.getItems().get(0).getId(), is(itemWithPermLocation.getId()));
  }

  @Test
  public void cannotSearchForItemsUsingADefaultField()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    createItem(smallAngryPlanet(holdingsRecordId));
    createItem(nod(holdingsRecordId));
    createItem(uprooted(UUID.randomUUID(), holdingsRecordId));
    createItem(temeraire(UUID.randomUUID(), holdingsRecordId));
    createItem(interestingTimes(UUID.randomUUID(), holdingsRecordId));

    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

    String url = itemsStorageUrl("") + "?query=t";

    client.get(url,
      StorageTestSuite.TENANT_ID, ResponseHandler.text(searchCompleted));

    Response searchResponse = searchCompleted.get(5, TimeUnit.SECONDS);

    assertThat(searchResponse.getStatusCode(), is(400));

    String error = searchResponse.getBody();

    assertThat(error, containsString(
        "QueryValidationException: cql.serverChoice requested, but no serverChoiceIndexes defined."));
  }

  @Test
  public void canDeleteAllItems()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    createItem(smallAngryPlanet(holdingsRecordId));
    createItem(nod(holdingsRecordId));
    createItem(uprooted(UUID.randomUUID(), holdingsRecordId));
    createItem(temeraire(UUID.randomUUID(), holdingsRecordId));
    createItem(interestingTimes(UUID.randomUUID(), holdingsRecordId));

    CompletableFuture<Response> deleteAllFinished = new CompletableFuture<>();

    client.delete(itemsStorageUrl(""), StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(deleteAllFinished));

    Response deleteResponse = deleteAllFinished.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(itemsStorageUrl(""), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    Response response = getCompleted.get(5, TimeUnit.SECONDS);

    JsonObject responseBody = response.getJson();

    JsonArray allItems = responseBody.getJsonArray("items");

    assertThat(allItems.size(), is(0));
    assertThat(responseBody.getInteger("totalRecords"), is(0));
  }

  @Test
  public void tenantIsRequiredForCreatingNewItem()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(itemsStorageUrl(""), smallAngryPlanet(holdingsRecordId), null, ResponseHandler.any(postCompleted));

    Response response = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @Test
  public void tenantIsRequiredForGettingAnItem()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    URL getInstanceUrl = itemsStorageUrl(String.format("/%s",
      UUID.randomUUID().toString()));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(getInstanceUrl, null, ResponseHandler.any(getCompleted));

    Response response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @Test
  public void tenantIsRequiredForGettingAllItems()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(itemsStorageUrl(""), null, ResponseHandler.any(getCompleted));

    Response response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

   private Response getById(UUID id)
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    URL getItemUrl = itemsStorageUrl(String.format("/%s", id));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(getItemUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

  private JsonObject createItemRequest(
      UUID id,
      UUID holdingsRecordId,
      String barcode) {

    return createItemRequest(id, holdingsRecordId, barcode, journalMaterialTypeID);
  }

  private JsonObject createItemRequest(
    UUID id,
    UUID holdingsRecordId,
    String barcode,
    String materialType) {

    JsonObject itemToCreate = new JsonObject();

    if(id != null) {
      itemToCreate.put("id", id.toString());
    }

    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("barcode", barcode);
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("materialTypeId", materialType);
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", annexLibraryLocationId.toString());

    return itemToCreate;
  }

  private JsonObject smallAngryPlanet(UUID itemId, UUID holdingsRecordId) {
    return createItemRequest(itemId, holdingsRecordId, "036000291452");
  }

  private JsonObject smallAngryPlanet(UUID holdingsRecordId) {
    return smallAngryPlanet(UUID.randomUUID(), holdingsRecordId);
  }

  private JsonObject nod(UUID itemId, UUID holdingsRecordId) {
    return createItemRequest(itemId, holdingsRecordId, "565578437802");
  }

  private JsonObject nod(UUID holdingsRecordId) {
    return nod(UUID.randomUUID(), holdingsRecordId);
  }

  private JsonObject uprooted(UUID itemId, UUID holdingsRecordId) {
    return createItemRequest(itemId, holdingsRecordId, "657670342075");
  }

  private JsonObject temeraire(UUID itemId, UUID holdingsRecordId) {
    return createItemRequest(itemId, holdingsRecordId, "232142443432");
  }

  private JsonObject interestingTimes(UUID itemId, UUID holdingsRecordId) {
    return createItemRequest(itemId, holdingsRecordId, "56454543534");
  }

  private Items findItems(String searchQuery) throws Exception {
    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

    client.get(itemsStorageUrl("?query=") + urlEncode(searchQuery),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(searchCompleted));

    return searchCompleted.get(5, TimeUnit.SECONDS).getJson()
      .mapTo(Items.class);
  }

  private JsonObject addTags(String tagValue, UUID holdingsRecordId) {
    return smallAngryPlanet(holdingsRecordId)
      .put("tags", new JsonObject()
        .put("tagList", new JsonArray()
          .add(tagValue)));
  }
}
