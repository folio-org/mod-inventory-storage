package org.folio.rest.api;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.rest.api.ItemEffectiveCallNumberComponentsTest.ITEM_LEVEL_CALL_NUMBER_TYPE;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.support.AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY;
import static org.folio.rest.support.HttpResponseMatchers.errorMessageContains;
import static org.folio.rest.support.HttpResponseMatchers.errorParametersValueIs;
import static org.folio.rest.support.HttpResponseMatchers.statusCodeIs;
import static org.folio.rest.support.JsonArrayHelper.toList;
import static org.folio.rest.support.JsonObjectMatchers.hasSoleMessageContaining;
import static org.folio.rest.support.JsonObjectMatchers.validationErrorMatches;
import static org.folio.rest.support.ResponseHandler.empty;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.ResponseHandler.text;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageSyncUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertCreateEventForItem;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertRemoveAllEventForItem;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertRemoveEventForItem;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertUpdateEventForItem;
import static org.folio.rest.support.matchers.PostgresErrorMessageMatchers.isMaximumSequenceValueError;
import static org.folio.rest.support.matchers.ResponseMatcher.hasValidationError;
import static org.folio.util.StringUtil.urlEncode;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.folio.rest.support.builders.StatisticalCodeBuilder;
import org.folio.rest.support.db.OptimisticLocking;
import org.folio.rest.support.matchers.DomainEventAssertions;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import lombok.SneakyThrows;

@RunWith(JUnitParamsRunner.class)
public class ItemStorageTest extends TestBaseWithInventoryUtil {
  private static final Logger log = LogManager.getLogger();
  private static final String TAG_VALUE = "test-tag";
  private static final String DISCOVERY_SUPPRESS = "discoverySuppress";

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

  @After
  public void removeStatisticalCodes() {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    statisticalCodeFixture.removeTestStatisticalCodes();
  }

  @Parameters({
    "PN 12 A6,PN12 .A6,,PN2 .A6,,,,,",
    "PN 12 A6 V 13 NO 12 41999,PN2 .A6 v.3 no.2 1999,,PN2 .A6,v. 3,no. 2,1999,,",
    "PN 12 A6 41999,PN12 .A6 41999,,PN2 .A6 1999,,,,,",
    "PN 12 A6 41999 CD,PN12 .A6 41999 CD,,PN2 .A6 1999,,,,,CD",
    "PN 12 A6 41999 12,PN12 .A6 41999 C.12,,PN2 .A6 1999,,,,2,",
    "PN 12 A69 41922 12,PN12 .A69 41922 C.12,,PN2 .A69,,,1922,2,",
    "PN 12 A69 NO 12,PN12 .A69 NO.12,,PN2 .A69,,no. 2,,,",
    "PN 12 A69 NO 12 41922 11,PN12 .A69 NO.12 41922 C.11,,PN2 .A69,,no. 2,1922,1,",
    "PN 12 A69 NO 12 41922 12,PN12 .A69 NO.12 41922 C.12,Wordsworth,PN2 .A69,,no. 2,1922,2,",
    "PN 12 A69 V 11 NO 11,PN12 .A69 V.11 NO.11,,PN2 .A69,v.1,no. 1,,,",
    "PN 12 A69 V 11 NO 11 +,PN12 .A69 V.11 NO.11 +,Over,PN2 .A69,v.1,no. 1,,,+",
    "PN 12 A69 V 11 NO 11 41921,PN12 .A69 V.11 NO.11 41921,,PN2 .A69,v.1,no. 1,1921,,",
    "PR 49199.3 41920 L33 41475 A6,PR 49199.3 41920 .L33 41475 .A6,,PR9199.3 1920 .L33 1475 .A6,,,,,",
    "PQ 42678 K26 P54,PQ 42678 .K26 P54,,PQ2678.K26 P54,,,,,",
    "PQ 48550.21 R57 V5 41992,PQ 48550.21 .R57 V15 41992,,PQ8550.21.R57 V5 1992,,,,,",
    "PQ 48550.21 R57 V5 41992,PQ 48550.21 .R57 V15 41992,,PQ8550.21.R57 V5,,,1992,,",
    "PR 3919 L33 41990,PR 3919 .L33 41990,,PR919 .L33 1990,,,,,",
    "PR 49199 A39,PR 49199 .A39,,PR9199 .A39,,,,,",
    "PR 49199.48 B3,PR 49199.48 .B3,,PR9199.48 .B3,,,,,"
  })
  @Test
  public void canCreateItemEffectiveShelvingOrder(
    String desiredShelvingOrder,
    String initiallyDesiredShelvesOrder,
    String prefix,
    String callNumber,
    String volume,
    String enumeration,
    String chronology,
    String copy,
    String suffix
  ) throws InterruptedException, ExecutionException, TimeoutException {

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

    itemToCreate.put("itemLevelCallNumber", callNumber);
    itemToCreate.put("itemLevelCallNumberSuffix", suffix);
    itemToCreate.put("itemLevelCallNumberPrefix", prefix);
    itemToCreate.put("itemLevelCallNumberTypeId", ITEM_LEVEL_CALL_NUMBER_TYPE);
    itemToCreate.put("volume",volume);
    itemToCreate.put("enumeration",enumeration);
    itemToCreate.put("chronology",chronology);
    itemToCreate.put("copyNumber",copy);

    itemToCreate.put("inTransitDestinationServicePointId", inTransitServicePointId);

    setItemSequence(1);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(itemsStorageUrl(""), itemToCreate, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject itemFromPost = postResponse.getJson();

    assertThat(itemFromPost.getString("effectiveShelvingOrder"), is(desiredShelvingOrder));
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
    String adminNote = "an admin note";

    final var statisticalCode = statisticalCodeFixture
      .createSerialManagementCode(new StatisticalCodeBuilder()
        .withCode("stcone")
        .withName("Statistical code 1"));
    final UUID statisticalCodeId = UUID.fromString(
      statisticalCode.getJson().getString("id")
    );

    JsonObject itemToCreate = new JsonObject();
    itemToCreate.put("administrativeNotes", new JsonArray().add(adminNote));
    itemToCreate.put("id", id.toString());
    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("barcode", "565578437802");
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("materialTypeId", journalMaterialTypeID);
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", annexLibraryLocationId.toString());
    itemToCreate.put("tags", new JsonObject().put("tagList",new JsonArray().add(TAG_VALUE)));
    itemToCreate.put("copyNumber", "copy1");

    itemToCreate.put("itemLevelCallNumber", "PS3623.R534 P37 2005");
    itemToCreate.put("itemLevelCallNumberSuffix", "allOwnComponentsCNS");
    itemToCreate.put("itemLevelCallNumberPrefix", "allOwnComponentsCNP");
    itemToCreate.put("itemLevelCallNumberTypeId", ITEM_LEVEL_CALL_NUMBER_TYPE);

    itemToCreate.put("statisticalCodeIds", Arrays.asList(statisticalCodeId));


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
    assertThat(itemFromPost.getJsonArray("administrativeNotes").contains(adminNote), is(true));
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
    assertThat(itemFromPost.getString("effectiveShelvingOrder"), is("PS 43623 R534 P37 42005 COP Y1 allOwnComponentsCNS"));

    Response getResponse = getById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject itemFromGet = getResponse.getJson();

    assertThat(itemFromGet.getString("id"), is(id.toString()));
    assertThat(itemFromGet.getJsonArray("administrativeNotes").contains(adminNote), is(true));
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

    List<String> tags = getTags(itemFromGet);

    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
    assertThat(itemFromGet.getString("copyNumber"), is("copy1"));
    assertCreateEventForItem(itemFromGet);
    assertThat(itemFromGet.getString("effectiveShelvingOrder"), is("PS 43623 R534 P37 42005 COP Y1 allOwnComponentsCNS"));

    assertThat(itemFromGet.getJsonArray("statisticalCodeIds"), hasItem(statisticalCodeId.toString()));
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

    List<String> tags = getTags(itemFromGet);

    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
  }

  @Test
  public void canReplaceItemWithNewProperties() throws Exception {
    final UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    final UUID id = UUID.randomUUID();
    final String expectedCopyNumber = "copy1";
    final String adminNote = "an admin note";

    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId);
    createItem(itemToCreate);

    JsonObject createdItem = getById(id).getJson();
    assertThat(createdItem.getString("copyNumber"), nullValue());

    JsonObject updatedItem = createdItem.copy()
      .put("copyNumber", expectedCopyNumber);
    updatedItem.put("administrativeNotes", new JsonArray().add(adminNote));
    itemsClient.replace(id, updatedItem);

    JsonObject updatedItemResponse = itemsClient.getById(id).getJson();
    assertThat(updatedItemResponse.getString("copyNumber"), is(expectedCopyNumber));
    assertUpdateEventForItem(createdItem, getById(id).getJson());
    assertThat(updatedItemResponse.getJsonArray("administrativeNotes").contains(adminNote), is(true));
  }

  @Test
  public void optimisticLockingVersion() {
    UUID itemId = UUID.randomUUID();
    UUID holdingId = createInstanceAndHolding(mainLibraryLocationId);
    JsonObject item = createItem(nod(itemId, holdingId));
    item.put(PERMANENT_LOCATION_ID_KEY, annexLibraryLocationId);
    // updating with current _version 1 succeeds and increments _version to 2
    assertThat(update(item).getStatusCode(), is(204));
    item.put(PERMANENT_LOCATION_ID_KEY, secondFloorLocationId);
    // updating with outdated _version 1 fails, current _version is 2
    int expected = OptimisticLocking.hasFailOnConflict("item") ? 409 : 204;
    assertThat(update(item).getStatusCode(), is(expected));
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

    List<String> tags = getTags(itemFromGet);

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

    List<JsonObject> errors = toList(
      postResponse.getJson().getJsonArray("errors"));

    assertThat(errors.size(), is(1));
    assertThat(errors, anyOf(
        hasItem(validationErrorMatches("may not be null", "materialTypeId")),
        hasItem(validationErrorMatches("must not be null", "materialTypeId"))));
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

    itemToCreate = getById(itemId).getJson();
    itemToCreate.put("materialTypeId", UUID.randomUUID().toString());

    CompletableFuture<Response> completed = new CompletableFuture<>();
    client.put(itemsStorageUrl("/" + itemId), itemToCreate, StorageTestSuite.TENANT_ID,
        ResponseHandler.text(completed));
    Response response = completed.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    assertThat(response.getBody(), allOf(
        containsString("Cannot set item"), containsString("materialtypeid")));
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

    List<String> tags = getTags(item);

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
    UUID holdingsRecordId = createInstanceAndHoldingWithBuilder(mainLibraryLocationId,
      holdingRequestBuilder -> holdingRequestBuilder.withCallNumber("hrCallNumber"));

    return new JsonArray()
        .add(nod(holdingsRecordId).put("barcode", UUID.randomUUID().toString()))
        .add(smallAngryPlanet(holdingsRecordId).put("barcode", UUID.randomUUID().toString()))
        .add(interestingTimes(holdingsRecordId).put("barcode", UUID.randomUUID().toString()));
  }

  private Response postSynchronousBatch(JsonArray itemsArray) {
    return postSynchronousBatch("", itemsArray);
  }

  private Response postSynchronousBatch(String subPath, JsonArray itemsArray) {
    JsonObject itemsCollection = new JsonObject().put("items", itemsArray);
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    client.post(itemsStorageSyncUrl(subPath), itemsCollection, TENANT_ID, ResponseHandler.any(createCompleted));
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

    itemsArray.stream()
      .map(obj -> (JsonObject) obj)
      .map(obj -> getById(obj.getString("id")).getJson())
      .forEach(DomainEventAssertions::assertCreateEventForItem);
  }

  @Test
  public void cannotSyncPostWithDuplicateId() {
    JsonArray itemsArray = threeItems();
    String duplicateId = itemsArray.getJsonObject(0).getString("id");
    itemsArray.getJsonObject(1).put("id", duplicateId);
    assertThat(postSynchronousBatch(itemsArray), allOf(
        statusCodeIs(HTTP_UNPROCESSABLE_ENTITY),
        anyOf(errorMessageContains("value already exists"), errorMessageContains("duplicate key")),
        errorParametersValueIs(duplicateId)));
    for (int i=0; i<itemsArray.size(); i++) {
      assertGetNotFound(itemsStorageUrl("/" + itemsArray.getJsonObject(i).getString("id")));
    }
  }

  public Response postSynchronousBatchWithExistingId(String subPath) {
    JsonArray itemsArray1 = threeItems();
    JsonArray itemsArray2 = threeItems();
    String existingId = itemsArray1.getJsonObject(1).getString("id");
    itemsArray2.getJsonObject(1).put("id", existingId);
    // create the three item of itemsArray1
    assertThat(postSynchronousBatch(subPath, itemsArray1), statusCodeIs(HttpStatus.HTTP_CREATED));
    // itemsArray2 has new items at position 0 and 2, but the same old item at position 1
    return postSynchronousBatch(subPath, itemsArray2);
  }

  @Test
  public void cannotPostSynchronousBatchWithExistingIdWithoutUpsertParameter() {
    assertThat(postSynchronousBatchWithExistingId(""), statusCodeIs(HTTP_UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotPostSynchronousBatchWithExistingIdUpsertFalse() {
    assertThat(postSynchronousBatchWithExistingId("?upsert=false"), statusCodeIs(HTTP_UNPROCESSABLE_ENTITY));
  }

  @Test
  public void canPostSynchronousBatchWithExistingIdUpsertTrue() throws Exception {
    final UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    final UUID existingItemId = UUID.randomUUID();

    final JsonArray itemsArray1 = new JsonArray()
      .add(nod(existingItemId, holdingsRecordId))
      .add(smallAngryPlanet(holdingsRecordId))
      .add(interestingTimes(holdingsRecordId));

    final JsonArray itemsArray2 = new JsonArray()
      .add(nod(existingItemId, holdingsRecordId))
      .add(temeraire(holdingsRecordId))
      .add(uprooted(holdingsRecordId));

    final var firstResponse = postSynchronousBatch("?upsert=true", itemsArray1);
    final var existingItemBeforeUpdate = getById(existingItemId).getJson();
    final var secondResponse = postSynchronousBatch("?upsert=true", itemsArray2);

    assertThat(firstResponse.getStatusCode(), is(201));
    assertThat(secondResponse.getStatusCode(), is(201));

    Stream.concat(itemsArray1.stream(), itemsArray2.stream())
      .map(json -> ((JsonObject) json).getString("id"))
      .filter(id -> !id.equals(existingItemId.toString()))
      .map(this::getById)
      .map(Response::getJson)
      .forEach(DomainEventAssertions::assertCreateEventForItem);

    assertUpdateEventForItem(existingItemBeforeUpdate, getById(existingItemId).getJson());
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
        statusCodeIs(HTTP_UNPROCESSABLE_ENTITY),
        anyOf(errorMessageContains("value already exists"), errorMessageContains("duplicate key")),
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

    List<String> tags = getTags(item);

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

    List<String> tags = getTags(item);

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

    Item initialItem = createItem(itemToCreate).mapTo(Item.class);
    Instant initialStatusDate = initialItem.getStatus().getDate().toInstant();

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
    Instant finalStatusDate = item.getStatus().getDate().toInstant();

    assertThat(item.getId(), is(id.toString()));

    assertThat(item.getStatus().getName().value(), is("Checked out"));

    assertThat(finalStatusDate.isAfter(initialStatusDate), is(true));
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

    JsonObject secondReplacement = getResponse.getJson().copy();

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

    assertThat(itemStatusDate, not(changedStatusDate));

    assertThat(itemStatusDate.isAfter(changedStatusDate), is(true));
  }

  @Test
  public void cannotUpdateStatusDate() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    UUID id = UUID.randomUUID();
    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId);
    JsonObject createdItem = createItem(itemToCreate);
    String initalDateTime = createdItem.getJsonObject("status").getString("date");

    JsonObject itemWithUpdatedStatus = getById(id).getJson().copy()
      .put("status", new JsonObject().put("name", "Checked out"));

    itemsClient.replace(id, itemWithUpdatedStatus);

    Response updatedItemResponse = itemsClient.getById(id);
    JsonObject updatedStatus = updatedItemResponse.getJson().getJsonObject("status");

    assertThat(updatedStatus.getString("name"), is("Checked out"));
    assertThat(isBefore(initalDateTime, updatedStatus.getString("date")), is(true));

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
  public void cannotChangeStatusDate() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    UUID id = UUID.randomUUID();
    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId);
    createItem(itemToCreate);

    JsonObject createdItem = getById(id).getJson();
    JsonObject initialStatus = createdItem.getJsonObject("status");

    assertThat(initialStatus.getString("name"), is("Available"));
    assertThat(initialStatus.getString("date"), notNullValue());

    final String initialStatusDate = initialStatus.getString("date");

    itemsClient.replace(id,
      createdItem.copy()
        .put("status", initialStatus.put("date", DateTime.now().toString()))
    );

    Response updatedItemResponse = itemsClient.getById(id);
    JsonObject updatedStatus = updatedItemResponse.getJson().getJsonObject("status");

    assertThat(updatedStatus.getString("name"), is("Available"));
    assertThat(updatedStatus.getString("date"), is(initialStatusDate));
  }

  @Test
  public void statusUpdatedDateRemainsAfterUpdate() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    UUID id = UUID.randomUUID();
    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId);
    JsonObject createdItem = createItem(itemToCreate);
    String initialStatusDate = createdItem.getJsonObject("status").getString("date");

    JsonObject itemWithUpdatedStatus = getById(id).getJson().copy()
      .put("status", new JsonObject().put("name", "Checked out"));

    itemsClient.replace(id, itemWithUpdatedStatus);

    Response updatedItemResponse = itemsClient.getById(id);
    JsonObject updatedStatus = updatedItemResponse.getJson().getJsonObject("status");

    assertThat(updatedStatus.getString("name"), is("Checked out"));
    assertThat(isBefore(initialStatusDate, updatedStatus.getString("date")), is(true));

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
  public void statusUpdatedDateIsUnchangedAfterUpdatesThatDoNotChangeStatus() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    UUID id = UUID.randomUUID();
    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId)
      .put("status", new JsonObject().put("name", "Available"));
    createItem(itemToCreate);

    JsonObject createdItem = getById(id).getJson();

    final String createdDate = createdItem.getJsonObject("status").getString("date");

    assertThat(createdItem.getJsonObject("status").getString("name"), is("Available"));
    assertThat(createdItem.getJsonObject("status").getString("date"), is(createdDate));

    JsonObject firstUpdateItem = createdItem.copy()
      .put("itemLevelCallNumber", "newItCn");

    itemsClient.replace(id, firstUpdateItem);

    JsonObject firstUpdatedItemResponse = itemsClient.getById(id).getJson();

    assertThat(firstUpdatedItemResponse.getString("itemLevelCallNumber"),
      is("newItCn"));
    assertThat(firstUpdatedItemResponse.getJsonObject("status").getString("name"),
      is("Available"));
    assertThat(firstUpdatedItemResponse.getJsonObject("status").getString("date"),
      is(createdDate));

    JsonObject secondUpdateItem = firstUpdatedItemResponse.copy()
      .put("temporaryLocationId", onlineLocationId.toString());

    itemsClient.replace(id, secondUpdateItem);

    JsonObject secondUpdatedItemResponse = itemsClient.getById(id).getJson();

    assertThat(secondUpdatedItemResponse.getString("temporaryLocationId"),
      is(onlineLocationId.toString()));
    assertThat(secondUpdatedItemResponse.getJsonObject("status").getString("name"),
      is("Available"));
    assertThat(secondUpdatedItemResponse.getJsonObject("status").getString("date"),
      is(createdDate));
  }

  @Test
  public void canDeleteAnItem() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    UUID id = UUID.randomUUID();
    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId);

    final JsonObject createdItem = createItem(itemToCreate);

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
    assertRemoveEventForItem(createdItem);
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
  public void canCreateMultipleItemsWithoutBarcode() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    createItem(removeBarcode(nod(holdingsRecordId)));
    createItem(removeBarcode(uprooted(holdingsRecordId)));
    createItem(temeraire(holdingsRecordId).put("barcode", null));
    createItem(interestingTimes(holdingsRecordId).put("barcode", null));
    assertCqlFindsBarcodes("id==*", null, null, null, null);
  }

  @Test
  public void cannotCreateItemWithDuplicateBarcode() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    createItem(nod(holdingsRecordId).put("barcode", "9876a"));
    assertThat(itemsClient.attemptToCreate(uprooted(holdingsRecordId).put("barcode", "9876a")),
        hasValidationError("9876a"));
    assertThat(itemsClient.attemptToCreate(uprooted(holdingsRecordId).put("barcode", "9876A")),
        hasValidationError("9876a"));
  }

  @Test
  public void cannotUpdateItemWithDuplicateBarcode() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    createItem(uprooted(holdingsRecordId).put("barcode", "9876a"));
    UUID nodId = UUID.randomUUID();
    JsonObject nod = createItem(nod(nodId, holdingsRecordId).put("barcode", "123"));

    Response response = itemsClient.attemptToReplace(nodId, nod.put("barcode", "9876A"));
    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), containsString("already exists in table item: 9876a"));
  }

  public void canSearchForItemsByBarcodeWithLeadingZero() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    createItem(nod(holdingsRecordId));
    createItem(uprooted(holdingsRecordId).put("barcode", "36000291452"));
    createItem(temeraire(holdingsRecordId).put("barcode", "036000291452"));
    createItem(smallAngryPlanet(holdingsRecordId));
    createItem(interestingTimes(holdingsRecordId));
    assertCqlFindsBarcodes("barcode==036000291452", "036000291452");
    assertCqlFindsBarcodes("barcode==36000291452", "36000291452");
  }

  @Test
  public void canSearchForItemsByBarcode() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    createItem(nod(holdingsRecordId).put("barcode", "123456a"));
    createItem(uprooted(holdingsRecordId).put("barcode", "123456ä"));
    createItem(smallAngryPlanet(holdingsRecordId).put("barcode", "673274826203"));
    createItem(temeraire(holdingsRecordId));
    createItem(interestingTimes(holdingsRecordId));
    assertCqlFindsBarcodes("barcode==673274826203", "673274826203");
    // respect accents, ignore case
    assertCqlFindsBarcodes("barcode==123456a", "123456a");
    assertCqlFindsBarcodes("barcode==123456A", "123456a");
    assertCqlFindsBarcodes("barcode==123456ä", "123456ä");
    assertCqlFindsBarcodes("barcode==123456Ä", "123456ä");
    assertCqlFindsBarcodes("barcode==123456*", "123456a", "123456ä");
  }

  @Test
  public void canSearchForItemsByTags() throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

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

    List<String> itemTags = getTags(foundItems.getJsonObject(0));

    assertThat(itemTags, hasItem(TAG_VALUE));
  }

  @Test
  public void canSearchForItemsByStatus() throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

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

    List<String> itemTags = getTags(foundItems.getJsonObject(0));

    assertThat(itemTags, hasItem(TAG_VALUE));
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

    final var createdItems = List.of(
      createItem(smallAngryPlanet(holdingsRecordId)),
      createItem(nod(holdingsRecordId)),
      createItem(uprooted(UUID.randomUUID(), holdingsRecordId)),
      createItem(temeraire(UUID.randomUUID(), holdingsRecordId)),
      createItem(interestingTimes(UUID.randomUUID(), holdingsRecordId)));

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

    assertRemoveAllEventForItem();
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
    throws InterruptedException, ExecutionException, TimeoutException {

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
    throws InterruptedException, ExecutionException, TimeoutException {

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
      containsString("problem: Wrong status name")
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
    assertThat(error.getString("message"), anyOf(is("may not be null"), is("must not be null")));
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
    assertThat(error.getString("message"), anyOf(is("may not be null"), is("must not be null")));
    assertThat(error.getJsonArray("parameters").getJsonObject(0).getString("key"),
      is("status.name"));
  }

  @Test
  public void cannotPostSynchronousBatchWithoutStatus() {
    final JsonArray itemArray = threeItems();
    itemArray.getJsonObject(1).remove("status");

    final Response response = postSynchronousBatch(itemArray);
    assertThat(response, anyOf(
        hasValidationError("may not be null", "items[1].status", "null"),
        hasValidationError("must not be null", "items[1].status", "null")
    ));

    for (int i = 0; i < itemArray.size(); i++) {
      assertGetNotFound(itemsStorageUrl("/" + itemArray.getJsonObject(i).getString("id")));
    }
  }

  @Test
  public void synchronousBatchItemsShouldHaveStatusDateOnCreation() {
    final JsonArray itemArray = threeItems();

    assertThat(postSynchronousBatch(itemArray), statusCodeIs(HttpStatus.HTTP_CREATED));

    JsonArrayHelper.toList(itemArray).forEach(itemInArray -> {
      final var fetchedItem = getById(itemInArray.getString("id")).getJson();

      assertThat(fetchedItem.getJsonObject("status").getString("date"), notNullValue());
    });
  }

  @Test
  @Parameters({
    "Aged to lost",
    "Available",
    "Awaiting pickup",
    "Awaiting delivery",
    "Checked out",
    "Claimed returned",
    "Declared lost",
    "In process",
    "In process (non-requestable)",
    "In transit",
    "Intellectual item",
    "Long missing",
    "Lost and paid",
    "Missing",
    "On order",
    "Paged",
    "Restricted",
    "Order closed",
    "Unavailable",
    "Unknown",
    "Withdrawn"
  })
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

  @Test
  public void cannotCreateItemWithNonExistentHoldingsRecordId() throws Exception {
    final UUID nonExistentHoldingsRecordId = UUID.randomUUID();

    final JsonObject itemToCreate = new ItemRequestBuilder()
      .forHolding(nonExistentHoldingsRecordId)
      .withMaterialType(journalMaterialTypeId)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .create();

    final Response createdItem = itemsClient.attemptToCreate(itemToCreate);

    assertThat(createdItem, hasValidationError(
      "Holdings record does not exist", "holdingsRecordId",
      nonExistentHoldingsRecordId.toString()));
  }

  @Test
  public void cannotBatchCreateItemsWithNonExistentHoldingsRecordId() throws Exception {
    final String nonExistentHoldingsRecordId = UUID.randomUUID().toString();
    final JsonArray items = threeItems();

    items.getJsonObject(2)
      .put("holdingsRecordId", nonExistentHoldingsRecordId);

    final Response response = itemsStorageSyncClient
      .attemptToCreate(new JsonObject().put("items", items));

    assertThat(response, hasValidationError(
      "Holdings record does not exist", "holdingsRecordId",
      nonExistentHoldingsRecordId));
  }

  @Test
  public void canSearchByDiscoverySuppressProperty() throws Exception {
    final UUID holdingsId = createInstanceAndHolding(mainLibraryLocationId);

    final IndividualResource suppressedItem = itemsClient.create(
      nod(holdingsId).put(DISCOVERY_SUPPRESS, true));
    final IndividualResource notSuppressedItem = itemsClient.create(
      smallAngryPlanet(holdingsId).put(DISCOVERY_SUPPRESS, false));
    final IndividualResource notSuppressedItemDefault = itemsClient.create(
      uprooted(holdingsId));

    final List<IndividualResource> suppressedItems = itemsClient
      .getMany("%s==true", DISCOVERY_SUPPRESS);
    final List<IndividualResource> notSuppressedItems = itemsClient
      .getMany("cql.allRecords=1 not %s==true", DISCOVERY_SUPPRESS);

    assertThat(suppressedItems.size(), is(1));
    assertThat(suppressedItems.get(0).getId(), is(suppressedItem.getId()));

    assertThat(notSuppressedItems.size(), is(2));
    assertThat(notSuppressedItems.stream()
        .map(IndividualResource::getId)
        .collect(Collectors.toList()),
      containsInAnyOrder(notSuppressedItem.getId(), notSuppressedItemDefault.getId()));
  }

  @Test
  public void shouldFindItemByCallNumberWhenThereIsSuffix() throws Exception {
    final UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    final IndividualResource firstItemToMatch = itemsClient.create(
      new ItemRequestBuilder()
        .forHolding(holdingsRecordId)
        .withMaterialType(journalMaterialTypeId)
        .withPermanentLoanType(canCirculateLoanTypeId)
        .withItemLevelCallNumber("GE77 .F73 2014")
        .available());

    final IndividualResource secondItemToMatch = itemsClient.create(
      new ItemRequestBuilder()
        .forHolding(holdingsRecordId)
        .withMaterialType(journalMaterialTypeId)
        .withPermanentLoanType(canCirculateLoanTypeId)
        .withItemLevelCallNumber("GE77 .F73 2014")
        .withItemLevelCallNumberSuffix("Curriculum Materials Collection")
        .available());

    itemsClient.create(
      new ItemRequestBuilder()
        .forHolding(holdingsRecordId)
        .withMaterialType(journalMaterialTypeId)
        .withPermanentLoanType(canCirculateLoanTypeId)
        .withItemLevelCallNumber("GE77 .F73 ")
        .withItemLevelCallNumberSuffix("2014 Curriculum Materials Collection")
        .available());

    final List<UUID> foundItems = searchByCallNumberEyeReadable("GE77 .F73 2014");

    assertThat(foundItems.size(), is(2));
    assertThat(foundItems, hasItems(firstItemToMatch.getId(), secondItemToMatch.getId()));
  }

  @Test
  public void explicitRightTruncationCanBeApplied() throws Exception {
    final UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    final IndividualResource firstItemToMatch = itemsClient.create(
      new ItemRequestBuilder()
        .forHolding(holdingsRecordId)
        .withMaterialType(journalMaterialTypeId)
        .withPermanentLoanType(canCirculateLoanTypeId)
        .withItemLevelCallNumber("GE77 .F73 2014")
        .available());

    final IndividualResource secondItemToMatch = itemsClient.create(
      new ItemRequestBuilder()
        .forHolding(holdingsRecordId)
        .withMaterialType(journalMaterialTypeId)
        .withPermanentLoanType(canCirculateLoanTypeId)
        .withItemLevelCallNumber("GE77 .F73 2014")
        .withItemLevelCallNumberSuffix("Curriculum Materials Collection")
        .available());

    final IndividualResource thirdItemToMatch = itemsClient.create(
      new ItemRequestBuilder()
        .forHolding(holdingsRecordId)
        .withMaterialType(journalMaterialTypeId)
        .withPermanentLoanType(canCirculateLoanTypeId)
        .withItemLevelCallNumber("GE77 .F73 ")
        .withItemLevelCallNumberSuffix("2014 Curriculum Materials Collection")
        .available());

    itemsClient.create(
      new ItemRequestBuilder()
        .forHolding(holdingsRecordId)
        .withMaterialType(journalMaterialTypeId)
        .withPermanentLoanType(canCirculateLoanTypeId)
        .withItemLevelCallNumber("GE77 .F74 ")
        .withItemLevelCallNumberSuffix("2014 Curriculum Materials Collection")
        .available());

    final List<UUID> foundItems = searchByCallNumberEyeReadable("GE77 .F73*");

    assertThat(foundItems.size(), is(3));
    assertThat(foundItems, hasItems(firstItemToMatch.getId(), secondItemToMatch.getId(),
      thirdItemToMatch.getId()));
  }

  @Test
  public void canSearchByPurchaseOrderLineIdentifierProperty() throws Exception {
    final UUID holdingsId = createInstanceAndHolding(mainLibraryLocationId);

    final IndividualResource firstItem = itemsClient.create(
      smallAngryPlanet(holdingsId).put("purchaseOrderLineIdentifier", "poli-1"));

    itemsClient.create(nod(holdingsId)
      .put("purchaseOrderLineIdentifier", "poli-2"));

    final List<IndividualResource> poli1Items = itemsClient
      .getMany("purchaseOrderLineIdentifier==\"poli-1\"");

    assertThat(poli1Items.size(), is(1));
    assertThat(poli1Items.get(0).getId(), is(firstItem.getId()));
  }

  @Test
  public void cannotCreateItemWithNonExistentStatisticalCodeId() throws Exception {
    final UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    final UUID nonExistentStatisticalCodeId = UUID.randomUUID();
    final String status = "Available";

    final JsonObject itemToCreate = new ItemRequestBuilder()
      .forHolding(holdingsRecordId)
      .withMaterialType(journalMaterialTypeId)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withStatus(status)
      .withStatisticalCodeIds(Arrays.asList(nonExistentStatisticalCodeId))
      .create();

    final String itemId = itemToCreate.getString("id");

    final Response createdItem = itemsClient.attemptToCreate(itemToCreate);

    String expectedMessage = String.format(
      "statistical code doesn't exist: %s foreign key violation in statisticalCodeIds array of item with id=%s",
      nonExistentStatisticalCodeId.toString(),
      itemId
    );

    assertThat(createdItem, hasValidationError(
      expectedMessage,
      "item",
      itemId
    ));
  }

  @Test
  public void canCreateItemWithMultipleStatisticalCodeIds() throws Exception {
    final var firstStatisticalCode = statisticalCodeFixture
      .createSerialManagementCode(new StatisticalCodeBuilder()
        .withCode("stcone")
        .withName("Statistical code 1"));

    final var secondStatisticalCode = statisticalCodeFixture
      .attemptCreateSerialManagementCode(new StatisticalCodeBuilder()
        .withCode("stctwo")
        .withName("Statistical code 2"));

    final UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    final UUID firstStatisticalCodeId = UUID.fromString(
      firstStatisticalCode.getJson().getString("id")
    );
    final UUID secondStatisticalCodeId = UUID.fromString(
      secondStatisticalCode.getJson().getString("id")
    );

    final String status = "Available";

    final JsonObject itemToCreate = new ItemRequestBuilder()
      .forHolding(holdingsRecordId)
      .withMaterialType(journalMaterialTypeId)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withStatus(status)
      .withStatisticalCodeIds(Arrays.asList(
        firstStatisticalCodeId,
        secondStatisticalCodeId
      ))
      .create();

    final Response createdItem = itemsClient.attemptToCreate(itemToCreate);

    assertThat(createdItem.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
  }

  @Test
  public void cannotCreateItemWithAtLeastOneNonExistentStatisticalCodeId() throws Exception {
    final var statisticalCode = statisticalCodeFixture
      .createSerialManagementCode(new StatisticalCodeBuilder()
        .withCode("stcone")
        .withName("Statistical code 1"));

    final UUID nonExistentStatisticalCodeId = UUID.randomUUID();

    final UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    final UUID statisticalCodeId = UUID.fromString(
      statisticalCode.getJson().getString("id")
    );

    final String status = "Available";

    final JsonObject itemToCreate = new ItemRequestBuilder()
      .forHolding(holdingsRecordId)
      .withMaterialType(journalMaterialTypeId)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withStatus(status)
      .withStatisticalCodeIds(Arrays.asList(
        statisticalCodeId,
        nonExistentStatisticalCodeId
      ))
      .create();

    final String itemId = itemToCreate.getString("id");

    final Response createdItem = itemsClient.attemptToCreate(itemToCreate);

    String expectedMessage = String.format(
      "statistical code doesn't exist: %s foreign key violation in statisticalCodeIds array of item with id=%s",
      nonExistentStatisticalCodeId.toString(),
      itemId
    );

    assertThat(createdItem, hasValidationError(
      expectedMessage,
      "item",
      itemId
    ));
  }

  @Test
  public void canUpdateItemWithStatisticalCodeId() throws Exception {
    final var statisticalCode = statisticalCodeFixture
      .createSerialManagementCode(new StatisticalCodeBuilder()
        .withCode("stcone")
        .withName("Statistical code 1"));

    final UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    final UUID statisticalCodeId = UUID.fromString(
      statisticalCode.getJson().getString("id")
    );

    JsonObject item = new JsonObject();
    String itemId = UUID.randomUUID().toString();
    item.put("id", itemId);
    item.put("status", new JsonObject().put("name", "Available"));
    item.put("holdingsRecordId", holdingsRecordId.toString());
    item.put("permanentLoanTypeId", canCirculateLoanTypeID);
    item.put("materialTypeId", bookMaterialTypeID);
    item.put("hrid", "testHRID");
    createItem(item);

    item = getById(itemId).getJson();

    item.put("statisticalCodeIds", Arrays.asList(statisticalCodeId));

    CompletableFuture<Response> completed = new CompletableFuture<>();
    client.put(itemsStorageUrl("/" + itemId), item, StorageTestSuite.TENANT_ID,
        ResponseHandler.empty(completed));
    Response response = completed.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  @Test
  public void cannotUpdateItemWithNonExistentStatisticalCodeId() throws Exception {
    UUID nonExistentStatisticalCodeId = UUID.randomUUID();
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    JsonObject item = new JsonObject();
    String itemId = UUID.randomUUID().toString();
    item.put("id", itemId);
    item.put("status", new JsonObject().put("name", "Available"));
    item.put("holdingsRecordId", holdingsRecordId.toString());
    item.put("permanentLoanTypeId", canCirculateLoanTypeID);
    item.put("materialTypeId", bookMaterialTypeID);
    item.put("hrid", "testHRID");
    createItem(item);

    item = getById(itemId).getJson();

    item.put("statisticalCodeIds", Arrays.asList(nonExistentStatisticalCodeId.toString()));

    CompletableFuture<Response> completed = new CompletableFuture<>();
    client.put(itemsStorageUrl("/" + itemId), item, StorageTestSuite.TENANT_ID,
        ResponseHandler.text(completed));
    Response response = completed.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    assertThat(response.getBody(), allOf(
        containsString("statistical code doesn't exist:"),
        containsString(nonExistentStatisticalCodeId.toString()),
        containsString("foreign key violation in statisticalCodeIds array of item"),
        containsString(itemId)));
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
    itemToCreate.put("_version", 1);

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

  static JsonObject nodWithNoBarcode(UUID holdingsRecordId) {
    return removeBarcode(nod(holdingsRecordId));
  }

  private static JsonObject uprooted(UUID itemId, UUID holdingsRecordId) {
    return createItemRequest(itemId, holdingsRecordId, "657670342075");
  }

  private static JsonObject uprooted(UUID holdingsRecordId) {
    return uprooted(UUID.randomUUID(), holdingsRecordId);
  }

  private static JsonObject temeraire(UUID itemId, UUID holdingsRecordId) {
    return createItemRequest(itemId, holdingsRecordId, "232142443432");
  }

  private static JsonObject temeraire(UUID holdingsRecordId) {
    return temeraire(UUID.randomUUID(), holdingsRecordId);
  }

  private static JsonObject interestingTimes(UUID itemId, UUID holdingsRecordId) {
    return createItemRequest(itemId, holdingsRecordId, "56454543534");
  }

  private static JsonObject interestingTimes(UUID holdingsRecordId) {
    return interestingTimes(UUID.randomUUID(), holdingsRecordId);
  }

  static JsonObject removeBarcode(JsonObject item) {
    item.remove("barcode");
    return item;
  }

  private Items findItems(String searchQuery) throws Exception {
    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

    client.get(itemsStorageUrl("?query=") + urlEncode(searchQuery),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(searchCompleted));

    Response response = searchCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(200));

    return response.getJson().mapTo(Items.class);
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

  private Response getById(UUID id) {
    return getById(id.toString());
  }

  private Response update(JsonObject item) {
    CompletableFuture<Response> completed = new CompletableFuture<>();
    client.put(itemsStorageUrl("/" + item.getString("id")), item, TENANT_ID, empty(completed));
    try {
      return completed.get(5, SECONDS);
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
    assertThat(response.getJson().getJsonObject("effectiveCallNumberComponents"),
      notNullValue());
  }

  private void assertHRIDRange(Response response, String minHRID, String maxHRID) {
    assertThat(response.getJson().getString("hrid"),
        is(both(greaterThanOrEqualTo(minHRID)).and(lessThanOrEqualTo(maxHRID))));
  }

  /**
   * Assert that the cql query returns items with the expected barcodes in any order.
   */
  private void assertCqlFindsBarcodes(String cql, String ... expectedBarcodes) throws Exception {
    Items items = findItems(cql);
    String [] barcodes = items.getItems().stream().map(Item::getBarcode).toArray(String []::new);
    assertThat(cql, barcodes, arrayContainingInAnyOrder(expectedBarcodes));
    assertThat(cql, items.getTotalRecords(), is(barcodes.length));
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

    vertx.runOnContext(v ->
      postgresClient.selectSingle("select setval('hrid_items_seq',"
        + sequenceNumber + ",FALSE)", r -> {
        if (r.succeeded()) {
          sequenceSet.complete(null);
        } else {
          sequenceSet.completeExceptionally(r.cause());
        }
      }));

    try {
      sequenceSet.get(2, SECONDS);
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  private List<String> getTags(JsonObject item) {
    return item.getJsonObject("tags").getJsonArray("tagList").stream()
      .map(Object::toString)
      .collect(Collectors.toList());
  }

  @SneakyThrows
  private Boolean isBefore(String dateTime, String secondDateTime) {
    ZonedDateTime firstTime = ZonedDateTime.parse(dateTime);
    ZonedDateTime secondTime = ZonedDateTime.parse(secondDateTime);

    return secondTime.isAfter(firstTime);
  }

  private List<UUID> searchByCallNumberEyeReadable(String searchTerm)
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    return itemsClient
      .getMany("fullCallNumber==\"%1$s\" OR callNumberAndSuffix==\"%1$s\" OR " +
          "effectiveCallNumberComponents.callNumber==\"%1$s\"", searchTerm)
      .stream()
      .map(IndividualResource::getId)
      .collect(Collectors.toList());
  }
}
