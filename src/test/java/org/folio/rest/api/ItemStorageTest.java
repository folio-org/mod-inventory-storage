package org.folio.rest.api;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.nio.file.Files.readAllBytes;
import static java.nio.file.Paths.get;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.support.AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY;
import static org.folio.rest.support.HttpResponseMatchers.errorMessageContains;
import static org.folio.rest.support.HttpResponseMatchers.errorParametersValueIs;
import static org.folio.rest.support.HttpResponseMatchers.statusCodeIs;
import static org.folio.rest.support.JsonObjectMatchers.hasSoleMessageContaining;
import static org.folio.rest.support.JsonObjectMatchers.validationErrorMatches;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.ResponseHandler.text;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageSyncUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.matchers.DateTimeMatchers.withinSecondsBeforeNow;
import static org.folio.rest.support.matchers.DateTimeMatchers.withinSecondsBeforeNowAsString;
import static org.folio.rest.support.matchers.PostgresErrorMessageMatchers.isMaximumSequenceValueError;
import static org.folio.rest.support.matchers.ResponseMatcher.hasValidationError;
import static org.folio.util.StringUtil.urlEncode;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.joda.time.Seconds.seconds;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.folio.HttpStatus;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Items;
import org.folio.rest.jaxrs.model.LastCheckIn;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.AdditionalHttpStatusCodes;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonArrayHelper;
import org.folio.rest.support.JsonErrorResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class ItemStorageTest extends TestBaseWithInventoryUtil {
  private static final Logger log = LoggerFactory.getLogger(ItemStorageTest.class);
  private static final String TAG_VALUE = "test-tag";

  // see also @BeforeClass TestBaseWithInventoryUtil.beforeAny()

  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));
  }

  @After
  public void resetItemHRID() {
    setItemSequence(1);
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
    itemToCreate.put("copyNumber", "copy1");

    //TODO: Replace with real service point when validated
    itemToCreate.put("inTransitDestinationServicePointId", inTransitServicePointId);

    setItemSequence(1);

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
    assertThat(itemFromPost.getString("hrid"), is("it00000000001"));
    assertThat(itemFromPost.getString("copyNumber"), is("copy1"));

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
    assertThat(itemFromGet.getString("inTransitDestinationServicePointId"),
      is(inTransitServicePointId));
    assertThat(itemFromGet.getString("hrid"), is("it00000000001"));

    List<String> tags = itemFromGet.getJsonObject("tags").getJsonArray("tagList").getList();

    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
    assertThat(itemFromGet.getString("copyNumber"), is("copy1"));
  }

  @Test
  public void canCreateAnItemWithMinimalProperties()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    UUID id = UUID.randomUUID();

    JsonObject itemToCreate = new JsonObject()
      .put("id", id.toString())
      .put("status", new JsonObject().put("name", "Available"))
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
    assertThat(itemFromGet.getJsonObject("status").getString("name"),
      is("Available"));

    List<String> tags = itemFromGet.getJsonObject("tags").getJsonArray("tagList").getList();

    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
  }

  @Test
  public void canReplaceItemWithNewProperties() throws Exception {
    final UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    final UUID id = UUID.randomUUID();
    final String expectedCopyNumber = "copy1";

    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId);
    createItem(itemToCreate);

    JsonObject createdItem = getById(id).getJson();
    assertThat(createdItem.getString("copyNumber"), nullValue());

    JsonObject updatedItem = createdItem.copy()
      .put("copyNumber", expectedCopyNumber);

    itemsClient.replace(id, updatedItem);

    JsonObject updatedItemResponse = itemsClient.getById(id).getJson();
    assertThat(updatedItemResponse.getString("copyNumber"), is(expectedCopyNumber));
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
  public void canCreateAnItemWithHRIDSupplied()
      throws MalformedURLException, InterruptedException,
      ExecutionException, TimeoutException {
    log.info("Starting canCreateAnItemWithHRIDSupplied");

    final UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    final UUID id = UUID.randomUUID();

    final JsonObject itemToCreate = new JsonObject()
      .put("id", id.toString())
      .put("status", new JsonObject().put("name", "Available"))
      .put("holdingsRecordId", holdingsRecordId.toString())
      .put("materialTypeId", journalMaterialTypeID)
      .put("permanentLoanTypeId", canCirculateLoanTypeID)
      .put("hrid", "ITEM12345")
      .put("tags", new JsonObject().put("tagList",new JsonArray().add(TAG_VALUE)));

    final CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(itemsStorageUrl(""), itemToCreate, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    final Response postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create item: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(postResponse.getJson().getString("hrid"), is("ITEM12345"));

    final Response getResponse = getById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getResponse.getJson().getString("hrid"), is("ITEM12345"));

    log.info("Finished canCreateAnItemWithHRIDSupplied");
  }

  @Test
  public void canUpdateAnItemWhenHRIDHasNotChanged()
      throws MalformedURLException, InterruptedException,
      ExecutionException, TimeoutException {
    log.info("Starting canUpdateAnItemHRIDDoesNotChange");

    final UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    final JsonObject itemToCreate = new JsonObject();
    final String itemId = UUID.randomUUID().toString();

    itemToCreate.put("id", itemId);
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("materialTypeId", bookMaterialTypeID);

    setItemSequence(1);

    createItem(itemToCreate);

    final CompletableFuture<Response> completed = new CompletableFuture<>();

    client.put(itemsStorageUrl("/" + itemId), itemToCreate, StorageTestSuite.TENANT_ID,
        ResponseHandler.text(completed));

    final Response getResponse = getById(UUID.fromString(itemId));

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getResponse.getJson().getString("hrid"), is("it00000000001"));

    log.info("Finished canUpdateAnItemHRIDDoesNotChange");
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
      .put("status", new JsonObject().put("name", "Available"))
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
      .put("status", new JsonObject().put("name", "Available"))
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
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
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
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
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
  public void cannotCreateAnItemWithDuplicateHRID()
      throws MalformedURLException, InterruptedException,
      ExecutionException, TimeoutException {
    log.info("Starting cannotCreateAnItemWithDuplicateHRID");

    final UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    final JsonObject itemToCreate = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("status", new JsonObject().put("name", "Available"))
      .put("holdingsRecordId", holdingsRecordId.toString())
      .put("permanentLoanTypeId", canCirculateLoanTypeID)
      .put("materialTypeId", journalMaterialTypeID);

    setItemSequence(1);

    createItem(itemToCreate);

    final Response response = getById(itemToCreate.getString("id"));

    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getString("hrid"), is("it00000000001"));

    final CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    itemToCreate.put("id", UUID.randomUUID().toString());
    itemToCreate.put("hrid", "it00000000001");

    client.post(itemsStorageUrl(""), itemToCreate, StorageTestSuite.TENANT_ID,
        json(createCompleted));

    final Response postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(UNPROCESSABLE_ENTITY));

    final Errors errors = postResponse.getJson().mapTo(Errors.class);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), notNullValue());
    assertThat(errors.getErrors().get(0), notNullValue());
    assertThat(errors.getErrors().get(0).getMessage(),
        is("lower(f_unaccent(jsonb ->> 'hrid'::text)) value already exists in table item: it00000000001"));
    assertThat(errors.getErrors().get(0).getParameters(), notNullValue());
    assertThat(errors.getErrors().get(0).getParameters().get(0), notNullValue());
    assertThat(errors.getErrors().get(0).getParameters().get(0).getKey(),
        is("lower(f_unaccent(jsonb ->> 'hrid'::text))"));
    assertThat(errors.getErrors().get(0).getParameters().get(0).getValue(),
        is("it00000000001"));

    log.info("Finished cannotCreateAnItemWithDuplicateHRID");
  }

  @Test
  public void cannotCreateAnItemWithHRIDFailure()
      throws MalformedURLException, InterruptedException,
      ExecutionException, TimeoutException {
    log.info("Starting cannotCreateAnItemWithHRIDFailure");

    final UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    final JsonObject itemToCreate = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("status", new JsonObject().put("name", "Available"))
      .put("holdingsRecordId", holdingsRecordId.toString())
      .put("permanentLoanTypeId", canCirculateLoanTypeID)
      .put("materialTypeId", journalMaterialTypeID);

    setItemSequence(99_999_999_999L);

    createItem(itemToCreate);

    final Response response = getById(itemToCreate.getString("id"));

    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getString("hrid"), is("it99999999999"));

    final CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    itemToCreate.put("id", UUID.randomUUID().toString());

    client.post(itemsStorageUrl(""), itemToCreate, StorageTestSuite.TENANT_ID,
        text(createCompleted));

    final Response postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(HTTP_INTERNAL_ERROR));
    assertThat(postResponse.getBody(), isMaximumSequenceValueError("hrid_items_seq"));

    log.info("Finished cannotCreateAnItemWithHRIDFailure");
  }

  @Test
  public void cannotUpdateAnItemWithNonexistingMaterialType()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    JsonObject itemToCreate = new JsonObject();
    String itemId = UUID.randomUUID().toString();
    itemToCreate.put("id", itemId);
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("materialTypeId", bookMaterialTypeID);
    itemToCreate.put("hrid", "testHRID");
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
  public void cannotUpdateAnItemWithChangedHRID()
      throws MalformedURLException, InterruptedException,
      ExecutionException, TimeoutException {
    log.info("Starting cannotUpdateAnItemWithChangedHRID");

    final UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    final String itemId = UUID.randomUUID().toString();
    final JsonObject itemToCreate = new JsonObject()
      .put("id", itemId)
      .put("holdingsRecordId", holdingsRecordId.toString())
      .put("status", new JsonObject().put("name", "Available"))
      .put("permanentLoanTypeId", canCirculateLoanTypeID)
      .put("materialTypeId", bookMaterialTypeID);

    setItemSequence(1);

    createItem(itemToCreate);

    itemToCreate.put("hrid", "ABC123");

    final CompletableFuture<Response> completed = new CompletableFuture<>();

    client.put(itemsStorageUrl("/" + itemId), itemToCreate, StorageTestSuite.TENANT_ID,
        ResponseHandler.text(completed));

    final Response response = completed.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    assertThat(response.getBody(),
        is("The hrid field cannot be changed: new=ABC123, old=it00000000001"));

    log.info("Finished cannotUpdateAnItemWithChangedHRID");
  }

  @Test
  public void cannotUpdateAnItemWithRemovedHRID()
      throws MalformedURLException, InterruptedException,
      ExecutionException, TimeoutException {
    log.info("Starting cannotUpdateAnItemWithRemovedHRID");

    final UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    final String itemId = UUID.randomUUID().toString();
    final JsonObject itemToCreate = new JsonObject()
      .put("id", itemId)
      .put("status", new JsonObject().put("name", "Available"))
      .put("holdingsRecordId", holdingsRecordId.toString())
      .put("permanentLoanTypeId", canCirculateLoanTypeID)
      .put("materialTypeId", bookMaterialTypeID);

    setItemSequence(1);

    createItem(itemToCreate);

    itemToCreate.remove("hrid");

    final CompletableFuture<Response> completed = new CompletableFuture<>();

    client.put(itemsStorageUrl("/" + itemId), itemToCreate, StorageTestSuite.TENANT_ID,
        ResponseHandler.text(completed));

    final Response response = completed.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    assertThat(response.getBody(),
        is("The hrid field cannot be changed: new=null, old=it00000000001"));

    log.info("Finished cannotUpdateAnItemWithRemovedHRID");
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
    assertThat(response.getErrors(), hasSoleMessageContaining("Unrecognized field"));
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
    assertThat(response.getErrors(), hasSoleMessageContaining("Unrecognized field"));
  }

  @Test
  public void cannotProvideAdditionalPropertiesInItemLocation()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    JsonObject requestWithAdditionalProperty = nod(UUID.randomUUID(),holdingsRecordId);

    requestWithAdditionalProperty
      .put("location", new JsonObject().put("somethingAdditional", "foo"));

    CompletableFuture<JsonErrorResponse> createCompleted = new CompletableFuture<>();

    client.post(itemsStorageUrl(""), requestWithAdditionalProperty,
      StorageTestSuite.TENANT_ID, ResponseHandler.jsonErrors(createCompleted));

    JsonErrorResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
    assertThat(response.getErrors(), hasSoleMessageContaining("Unrecognized field"));
  }

  private JsonArray threeItems() {
    try {
      UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
      JsonArray jsonArray = new JsonArray()
          .add(nod(holdingsRecordId))
          .add(smallAngryPlanet(holdingsRecordId))
          .add(interestingTimes(UUID.randomUUID(), holdingsRecordId));
      return jsonArray;
    } catch (MalformedURLException | ExecutionException | InterruptedException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private Response postSynchronousBatch(JsonArray itemsArray) {
    JsonObject itemsCollection = new JsonObject().put("items", itemsArray);
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    client.post(itemsStorageSyncUrl(""), itemsCollection, TENANT_ID, ResponseHandler.any(createCompleted));
    try {
      return createCompleted.get(5, SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void canPostSynchronousBatch() {
    JsonArray itemsArray = threeItems();
    assertThat(postSynchronousBatch(itemsArray), statusCodeIs(HttpStatus.HTTP_CREATED));
    for (Object item : itemsArray) {
      assertExists((JsonObject) item);
    }
  }

  @Test
  public void cannotSyncPostWithDuplicateId() {
    JsonArray itemsArray = threeItems();
    String duplicateId = itemsArray.getJsonObject(0).getString("id");
    itemsArray.getJsonObject(1).put("id", duplicateId);
    assertThat(postSynchronousBatch(itemsArray), allOf(
        statusCodeIs(HttpStatus.HTTP_UNPROCESSABLE_ENTITY),
        errorMessageContains("duplicate key"),
        errorParametersValueIs(duplicateId)));
    for (int i=0; i<itemsArray.size(); i++) {
      assertGetNotFound(itemsStorageUrl("/" + itemsArray.getJsonObject(i).getString("id")));
    }
  }

  @Test
  public void canPostSynchronousBatchWithGeneratedHRID() {
    log.info("Starting canPostSynchronousBatchWithGeneratedHRID");

    setItemSequence(1);

    final JsonArray itemsArray = threeItems();

    assertThat(postSynchronousBatch(itemsArray), statusCodeIs(HTTP_CREATED));

    for (Object item : itemsArray) {
      assertExists((JsonObject) item);
    }

    itemsArray.stream()
        .map(o -> (JsonObject) o)
        .forEach(item -> {
          final Response response = getById(item.getString("id"));
          assertExists(response, item);
          assertHRIDRange(response, "it00000000001", "it00000000003");
        });

    log.info("Finished canPostSynchronousBatchWithGeneratedHRID");
  }

  @Test
  public void canPostSynchronousBatchWithSuppliedAndGeneratedHRID() {
    log.info("Starting canPostSynchronousBatchWithSuppliedAndGeneratedHRID");

    setItemSequence(1);

    final String hrid = "ABC123";
    final JsonArray itemsArray = threeItems();

    itemsArray.getJsonObject(1).put("hrid", hrid);

    assertThat(postSynchronousBatch(itemsArray), statusCodeIs(HttpStatus.HTTP_CREATED));

    Response response = getById(itemsArray.getJsonObject(0).getString("id"));
    assertExists(response, itemsArray.getJsonObject(0));
    assertHRIDRange(response, "it00000000001", "it00000000002");

    response = getById(itemsArray.getJsonObject(1).getString("id"));
    assertExists(response, itemsArray.getJsonObject(1));
    assertThat(response.getJson().getString("hrid"), is(hrid));

    response = getById(itemsArray.getJsonObject(2).getString("id"));
    assertExists(response, itemsArray.getJsonObject(2));
    assertHRIDRange(response, "it00000000001", "it00000000002");

    log.info("Finished canPostSynchronousBatchWithSuppliedAndGeneratedHRID");
  }

  @Test
  public void cannotPostSynchronousBatchWithDuplicateHRIDs() {
    log.info("Starting cannotPostSynchronousBatchWithDuplicateHRIDs");

    setItemSequence(1);

    final JsonArray itemsArray = threeItems();
    final String duplicateHRID = "it00000000001";
    itemsArray.getJsonObject(0).put("hrid", duplicateHRID);
    itemsArray.getJsonObject(1).put("hrid", duplicateHRID);

    assertThat(postSynchronousBatch(itemsArray), allOf(
        statusCodeIs(HttpStatus.HTTP_UNPROCESSABLE_ENTITY),
        errorMessageContains("duplicate key"),
        errorParametersValueIs(duplicateHRID)));

    for (int i = 0; i < itemsArray.size(); i++) {
      assertGetNotFound(itemsStorageUrl("/" + itemsArray.getJsonObject(i).getString("id")));
    }

    log.info("Finished cannotPostSynchronousBatchWithDuplicateHRIDs");
  }

  @Test
  public void cannotPostSynchronousBatchWithHRIDFailure() {
    log.info("Starting cannotPostSynchronousBatchWithHRIDFailure");

    setItemSequence(99_999_999_999L);

    final JsonArray itemArray = threeItems();

    final Response response = postSynchronousBatch(itemArray);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_INTERNAL_ERROR));
    assertThat(response.getBody(), isMaximumSequenceValueError("hrid_items_seq"));

    for (int i = 0; i < itemArray.size(); i++) {
      assertGetNotFound(itemsStorageUrl("/" + itemArray.getJsonObject(i).getString("id")));
    }

    log.info("Finished cannotPostSynchronousBatchWithHRIDFailure");
  }

  @Test
  public void canReplaceAnItemAtSpecificLocation()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    UUID id = UUID.randomUUID();
    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId)
        .put("hrid", "testHRID");

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
    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId)
        .put("hrid", "testHRID");

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
    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId)
        .put("hrid", "testHRID");

    createItem(itemToCreate);

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

    Item item = getResponse.getJson().mapTo(Item.class);

    assertThat(item.getId(), is(id.toString()));

    assertThat(item.getStatus().getName().value(), is("Checked out"));

    assertThat(item.getStatus().getDate().toInstant(), withinSecondsBeforeNow(seconds(2)));
  }

  @Test
  public void checkIfStatusDateChangesWhenItemStatusUpdated()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    UUID id = UUID.randomUUID();
    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId)
        .put("hrid", "testHRID");

    createItem(itemToCreate);

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


    Item item = getResponse.getJson().mapTo(Item.class);

    assertThat(item.getId(), is(id.toString()));

    assertThat(item.getStatus().getName().value(), is("Checked out"));

    assertThat(item.getStatus().getDate(),
      notNullValue());

    Instant changedStatusDate = item.getStatus().getDate().toInstant();

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

    Item resultItem = getResponse.getJson().mapTo(Item.class);

    assertThat(resultItem.getId(), is(id.toString()));

    assertThat(resultItem.getStatus().getName().value(), is("Available"));

    Instant itemStatusDate = resultItem.getStatus().getDate().toInstant();

    assertThat(itemStatusDate, withinSecondsBeforeNow(seconds(2)));

    assertThat(itemStatusDate, not(changedStatusDate));
  }

  @Test
  public void cannotUpdateStatusDate() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    UUID id = UUID.randomUUID();
    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId);
    createItem(itemToCreate);

    JsonObject itemWithUpdatedStatus = getById(id).getJson().copy()
      .put("status", new JsonObject().put("name", "Checked out"));

    itemsClient.replace(id, itemWithUpdatedStatus);

    Response updatedItemResponse = itemsClient.getById(id);
    JsonObject updatedStatus = updatedItemResponse.getJson().getJsonObject("status");

    assertThat(updatedStatus.getString("name"), is("Checked out"));
    assertThat(updatedStatus.getString("date"), withinSecondsBeforeNowAsString(seconds(2)));

    JsonObject itemWithUpdatedStatusDate = updatedItemResponse.getJson().copy();
    itemWithUpdatedStatusDate.getJsonObject("status")
      .put("date", DateTime.now(DateTimeZone.UTC).plusDays(1).toString());

    itemsClient.replace(id, itemWithUpdatedStatusDate);
    JsonObject itemWithUpdatedStatusDateResponse = itemsClient.getById(id).getJson();

    String oldStatusDate = updatedStatus.getString("date");
    String newStatusDate = itemWithUpdatedStatusDateResponse.getJsonObject("status").getString("date");

    assertThat(newStatusDate, is(oldStatusDate));
  }

  @Test
  public void cannotSetStatusDate() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    UUID id = UUID.randomUUID();
    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId);
    createItem(itemToCreate);

    JsonObject createdItem = getById(id).getJson();
    JsonObject initialStatus = createdItem.getJsonObject("status");

    assertThat(initialStatus.getString("name"), is("Available"));
    assertThat(initialStatus.getString("date"), nullValue());

    itemsClient.replace(id,
      createdItem.copy()
        .put("status", initialStatus.put("date", DateTime.now().toString()))
    );

    Response updatedItemResponse = itemsClient.getById(id);
    JsonObject updatedStatus = updatedItemResponse.getJson().getJsonObject("status");

    assertThat(updatedStatus.getString("name"), is("Available"));
    assertThat(updatedStatus.getString("date"), nullValue());
  }

  @Test
  public void statusUpdatedDateRemainsAfterUpdate() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    UUID id = UUID.randomUUID();
    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId);
    createItem(itemToCreate);

    JsonObject itemWithUpdatedStatus = getById(id).getJson().copy()
      .put("status", new JsonObject().put("name", "Checked out"));

    itemsClient.replace(id, itemWithUpdatedStatus);

    Response updatedItemResponse = itemsClient.getById(id);
    JsonObject updatedStatus = updatedItemResponse.getJson().getJsonObject("status");

    assertThat(updatedStatus.getString("name"), is("Checked out"));
    assertThat(updatedStatus.getString("date"), withinSecondsBeforeNowAsString(seconds(2)));

    JsonObject itemWithUpdatedCallNumber = updatedItemResponse.getJson().copy()
      .put("itemLevelCallNumber", "newItemLevelCallNumber");

    itemsClient.replace(id, itemWithUpdatedCallNumber);
    JsonObject itemWithUpdatedCallNumberResponse = itemsClient.getById(id).getJson();

    assertThat(itemWithUpdatedCallNumberResponse.getString("itemLevelCallNumber"),
      is("newItemLevelCallNumber"));

    String oldStatusDate = updatedStatus.getString("date");
    String newStatusDate = itemWithUpdatedCallNumberResponse.getJsonObject("status").getString("date");

    assertThat(oldStatusDate, is(newStatusDate));
  }

  @Test
  public void statusUpdatedDateIsNullOnSubsequentUpdates() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    UUID id = UUID.randomUUID();
    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId)
      .put("status", new JsonObject().put("name", "Available"));
    createItem(itemToCreate);

    JsonObject createdItem = getById(id).getJson();
    assertThat(createdItem.getJsonObject("status").getString("name"), is("Available"));
    assertThat(createdItem.getJsonObject("status").getString("date"), nullValue());

    JsonObject firstUpdateItem = createdItem.copy()
      .put("itemLevelCallNumber", "newItCn");

    itemsClient.replace(id, firstUpdateItem);

    JsonObject firstUpdatedItemResponse = itemsClient.getById(id).getJson();

    assertThat(firstUpdatedItemResponse.getString("itemLevelCallNumber"),
      is("newItCn"));
    assertThat(firstUpdatedItemResponse.getJsonObject("status").getString("name"),
      is("Available"));
    assertThat(firstUpdatedItemResponse.getJsonObject("status").getString("date"),
      nullValue());

    JsonObject secondUpdateItem = firstUpdatedItemResponse.copy()
      .put("temporaryLocationId", onlineLocationId.toString());

    itemsClient.replace(id, secondUpdateItem);

    JsonObject secondUpdatedItemResponse = itemsClient.getById(id).getJson();

    assertThat(secondUpdatedItemResponse.getString("temporaryLocationId"),
      is(onlineLocationId.toString()));
    assertThat(secondUpdatedItemResponse.getJsonObject("status").getString("name"),
      is("Available"));
    assertThat(secondUpdatedItemResponse.getJsonObject("status").getString("date"),
      nullValue());
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

  @Test
  public void testItemHasLastCheckInProperties()
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    UUID itemId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID servicePointId = UUID.randomUUID();
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    JsonObject itemData = smallAngryPlanet(itemId, holdingsRecordId)
        .put("hrid", "testHRID");
    createItem(itemData);

    LastCheckIn expected = new LastCheckIn();
    expected.setStaffMemberId(userId.toString());
    expected.setServicePointId(servicePointId.toString());
    expected.setDateTime(new Date());
    JsonObject lastCheckInData = JsonObject.mapFrom(expected);
    itemData.put("lastCheckIn", lastCheckInData);

    itemsClient.replace(itemId, itemData);
    JsonObject actualItem = itemsClient.getById(itemId).getJson();
    JsonObject actualLastCheckin = actualItem.getJsonObject("lastCheckIn");

    LastCheckIn actual = actualLastCheckin.mapTo(LastCheckIn.class);

    assertThat(expected.getDateTime(), is(actual.getDateTime()));
    assertThat(expected.getServicePointId(), is(actual.getServicePointId()));
    assertThat(expected.getStaffMemberId(), is(actual.getStaffMemberId()));
  }

  @Test
  public void cannotCreateItemWithWrongStatus() throws Exception {
    JsonObject itemToCreate = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("status", new JsonObject().put("name", "Wrong status name"));

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    client.post(itemsStorageUrl(""), itemToCreate, StorageTestSuite.TENANT_ID,
      ResponseHandler.text(createCompleted));

    Response postResponse = createCompleted.get(5, TimeUnit.SECONDS);
    assertThat(postResponse.getStatusCode(), is(400));
    assertThat(postResponse.getBody(),
      matchesPattern("(?s)Json content error Cannot construct instance of `.+`, problem: Wrong status name.+")
    );
  }

  @Test
  public void cannotRemoveItemStatus() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    UUID id = UUID.randomUUID();

    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId)
      .put("status", new JsonObject().put("name", "Available"));

    createItem(itemToCreate);

    JsonObject createdItem = getById(id).getJson();
    assertThat(createdItem.getJsonObject("status").getString("name"),
      is("Available")
    );

    JsonObject replacement = itemToCreate.copy();
    replacement.remove("status");

    CompletableFuture<JsonErrorResponse> updateCompleted = new CompletableFuture<>();
    client.put(itemsStorageUrl("/" + id), replacement,
      TENANT_ID, ResponseHandler.jsonErrors(updateCompleted));

    JsonErrorResponse updateResponse = updateCompleted.get(5, TimeUnit.SECONDS);
    assertThat(updateResponse.getStatusCode(), is(422));
    assertThat(updateResponse.getErrors().size(), is(1));

    JsonObject error = updateResponse.getErrors().get(0);
    assertThat(error.getString("message"), is("may not be null"));
    assertThat(error.getJsonArray("parameters").getJsonObject(0).getString("key"),
      is("status"));
  }

  @Test
  public void cannotRemoveItemStatusName() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    UUID id = UUID.randomUUID();

    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId)
      .put("status", new JsonObject().put("name", "Available"));

    createItem(itemToCreate);

    JsonObject createdItem = getById(id).getJson();
    assertThat(createdItem.getJsonObject("status").getString("name"),
      is("Available")
    );

    JsonObject replacement = itemToCreate.copy();
    replacement.getJsonObject("status").remove("name");

    CompletableFuture<JsonErrorResponse> updateCompleted = new CompletableFuture<>();
    client.put(itemsStorageUrl("/" + id), replacement,
      TENANT_ID, ResponseHandler.jsonErrors(updateCompleted));

    JsonErrorResponse updateResponse = updateCompleted.get(5, TimeUnit.SECONDS);
    assertThat(updateResponse.getStatusCode(), is(422));
    assertThat(updateResponse.getErrors().size(), is(1));

    JsonObject error = updateResponse.getErrors().get(0);
    assertThat(error.getString("message"), is("may not be null"));
    assertThat(error.getJsonArray("parameters").getJsonObject(0).getString("key"),
      is("status.name"));
  }

  @Test
  public void cannotPostSynchronousBatchWithoutStatus() {
    final JsonArray itemArray = threeItems();
    itemArray.getJsonObject(1).remove("status");

    final Response response = postSynchronousBatch(itemArray);
    assertThat(response,
      hasValidationError("may not be null", "items[1].status", "null")
    );

    for (int i = 0; i < itemArray.size(); i++) {
      assertGetNotFound(itemsStorageUrl("/" + itemArray.getJsonObject(i).getString("id")));
    }
  }

  @Test
  @Parameters(method = "getAllowedItemStatuses")
  public void canCreateItemWithAllAllowedStatuses(String status) throws Exception {
    final UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    final ItemRequestBuilder itemToCreate = new ItemRequestBuilder()
      .forHolding(holdingsRecordId)
      .withMaterialType(journalMaterialTypeId)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withStatus(status);

    final IndividualResource createdItem = itemsClient.create(itemToCreate);
    assertThat(createdItem.getJson().getJsonObject("status")
      .getString("name"), is(status));

    JsonObject itemInStorage = itemsClient.getById(createdItem.getId()).getJson();
    assertThat(itemInStorage.getJsonObject("status").getString("name"), is(status));
  }

  @Test
  public void canFilterByFullCallNumber() throws Exception {
    final UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    final IndividualResource itemWithWholeCallNumber = itemsClient.create(
      new ItemRequestBuilder()
        .forHolding(holdingsRecordId)
        .withMaterialType(journalMaterialTypeId)
        .withPermanentLoanType(canCirculateLoanTypeId)
        .withItemLevelCallNumberPrefix("prefix")
        .withItemLevelCallNumber("callNumber")
        .withItemLevelCallNumberSuffix("suffix")
        .available());

    itemsClient.create(new ItemRequestBuilder()
      .forHolding(holdingsRecordId)
      .withMaterialType(journalMaterialTypeId)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withItemLevelCallNumberPrefix("prefix")
      .withItemLevelCallNumber("callNumber")
      .available());

    itemsClient.create(new ItemRequestBuilder()
      .forHolding(holdingsRecordId)
      .withMaterialType(journalMaterialTypeId)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withItemLevelCallNumberPrefix("prefix")
      .withItemLevelCallNumber("differentCallNumber")
      .withItemLevelCallNumberSuffix("suffix")
      .available());

    final List<IndividualResource> foundItems = itemsClient
      .getMany("fullCallNumber == \"%s\"", "prefix callNumber suffix");

    assertThat(foundItems.size(), is(1));
    assertThat(foundItems.get(0).getId(), is(itemWithWholeCallNumber.getId()));
  }

  @Test
  public void canFilterByCallNumberAndSuffix() throws Exception {
    final UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    final IndividualResource itemWithWholeCallNumber = itemsClient.create(
      new ItemRequestBuilder()
        .forHolding(holdingsRecordId)
        .withMaterialType(journalMaterialTypeId)
        .withPermanentLoanType(canCirculateLoanTypeId)
        .withItemLevelCallNumberPrefix("prefix")
        .withItemLevelCallNumber("callNumber")
        .withItemLevelCallNumberSuffix("suffix")
        .available());

    itemsClient.create(new ItemRequestBuilder()
      .forHolding(holdingsRecordId)
      .withMaterialType(journalMaterialTypeId)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withItemLevelCallNumberPrefix("prefix")
      .withItemLevelCallNumber("callNumber")
      .available());

    final IndividualResource itemNoPrefix = itemsClient.create(
      new ItemRequestBuilder()
        .forHolding(holdingsRecordId)
        .withMaterialType(journalMaterialTypeId)
        .withPermanentLoanType(canCirculateLoanTypeId)
        .withItemLevelCallNumber("callNumber")
        .withItemLevelCallNumberSuffix("suffix")
        .available());

    final List<IndividualResource> foundItems = itemsClient
      .getMany("callNumberAndSuffix == \"%s\"", "callNumber suffix");

    assertThat(foundItems.size(), is(2));

    final Set<UUID> allFoundIds = foundItems.stream()
      .map(IndividualResource::getId)
      .collect(Collectors.toSet());
    assertThat(allFoundIds, hasItems(itemWithWholeCallNumber.getId(), itemNoPrefix.getId()));
  }

  @SuppressWarnings("unused")
  private Set<String> getAllowedItemStatuses() throws IOException {
    final String itemJson = new String(readAllBytes(get("ramls/item.json")),
      StandardCharsets.UTF_8);

    final JsonObject itemSchema = new JsonObject(itemJson);

    JsonArray allowedStatuses = itemSchema.getJsonObject("properties")
      .getJsonObject("status").getJsonObject("properties")
      .getJsonObject("name").getJsonArray("enum");

    return allowedStatuses.stream()
      .map(element -> (String) element)
      .collect(Collectors.toSet());
  }

  private Response getById(UUID id) throws InterruptedException,
    ExecutionException, TimeoutException {

    URL getItemUrl = itemsStorageUrl(String.format("/%s", id));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(getItemUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

  private static JsonObject createItemRequest(
      UUID id,
      UUID holdingsRecordId,
      String barcode) {

    return createItemRequest(id, holdingsRecordId, barcode, journalMaterialTypeID);
  }

  private static JsonObject createItemRequest(
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

  private static JsonObject smallAngryPlanet(UUID itemId, UUID holdingsRecordId) {
    return createItemRequest(itemId, holdingsRecordId, "036000291452");
  }

  private static JsonObject smallAngryPlanet(UUID holdingsRecordId) {
    return smallAngryPlanet(UUID.randomUUID(), holdingsRecordId);
  }

  static JsonObject nod(UUID itemId, UUID holdingsRecordId) {
    return createItemRequest(itemId, holdingsRecordId, "565578437802");
  }

  static JsonObject nod(UUID holdingsRecordId) {
    return nod(UUID.randomUUID(), holdingsRecordId);
  }

  private static JsonObject uprooted(UUID itemId, UUID holdingsRecordId) {
    return createItemRequest(itemId, holdingsRecordId, "657670342075");
  }

  private static JsonObject temeraire(UUID itemId, UUID holdingsRecordId) {
    return createItemRequest(itemId, holdingsRecordId, "232142443432");
  }

  private static JsonObject interestingTimes(UUID itemId, UUID holdingsRecordId) {
    return createItemRequest(itemId, holdingsRecordId, "56454543534");
  }

  private Items findItems(String searchQuery) throws Exception {
    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

    client.get(itemsStorageUrl("?query=") + urlEncode(searchQuery),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(searchCompleted));

    return searchCompleted.get(5, TimeUnit.SECONDS).getJson()
      .mapTo(Items.class);
  }

  private Response getById(String id) {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(itemsStorageUrl("/" + id), TENANT_ID, json(getCompleted));
    try {
      return getCompleted.get(5, SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private void assertExists(JsonObject expectedItem) {
    Response response = getById(expectedItem.getString("id"));
    assertExists(response, expectedItem);
  }

  private void assertExists(Response response, JsonObject expectedItem) {
    assertThat(response, statusCodeIs(HttpStatus.HTTP_OK));
    assertThat(response.getBody(), containsString(expectedItem.getString("holdingsRecordId")));
  }

  private void assertHRIDRange(Response response, String minHRID, String maxHRID) {
    assertThat(response.getJson().getString("hrid"),
        is(both(greaterThanOrEqualTo(minHRID)).and(lessThanOrEqualTo(maxHRID))));
  }

  private JsonObject addTags(String tagValue, UUID holdingsRecordId) {
    return smallAngryPlanet(holdingsRecordId)
      .put("tags", new JsonObject()
        .put("tagList", new JsonArray()
          .add(tagValue)));
  }

  private void setItemSequence(long sequenceNumber) {
    final Vertx vertx = StorageTestSuite.getVertx();
    final PostgresClient postgresClient =
        PostgresClient.getInstance(vertx, TENANT_ID);
    final CompletableFuture<Void> sequenceSet = new CompletableFuture<>();

    vertx.runOnContext(v -> {
      postgresClient.selectSingle("select setval('hrid_items_seq',"
          + sequenceNumber + ",FALSE)", r -> {
            if (r.succeeded()) {
              sequenceSet.complete(null);
            } else {
              sequenceSet.completeExceptionally(r.cause());
            }
          });
    });

    try {
      sequenceSet.get(2, SECONDS);
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }
}
