package org.folio.rest.api;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.rest.support.HttpResponseMatchers.errorMessageContains;
import static org.folio.rest.support.HttpResponseMatchers.errorParametersValueIs;
import static org.folio.rest.support.HttpResponseMatchers.statusCodeIs;
import static org.folio.rest.support.JsonArrayHelper.toList;
import static org.folio.rest.support.JsonObjectMatchers.hasSoleMessageContaining;
import static org.folio.rest.support.JsonObjectMatchers.validationErrorMatches;
import static org.folio.rest.support.ResponseHandler.empty;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.ResponseHandler.text;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageSyncUnsafeUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageSyncUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.matchers.PostgresErrorMessageMatchers.isMaximumSequenceValueError;
import static org.folio.rest.support.matchers.ResponseMatcher.hasValidationError;
import static org.folio.services.CallNumberConstants.LC_CN_TYPE_ID;
import static org.folio.util.StringUtil.urlEncode;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.folio.validator.NotesValidators.MAX_NOTE_LENGTH;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.containsStringIgnoringCase;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.HttpStatus;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Items;
import org.folio.rest.jaxrs.model.LastCheckIn;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.RetrieveDto;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonArrayHelper;
import org.folio.rest.support.JsonErrorResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.folio.rest.support.builders.StatisticalCodeBuilder;
import org.folio.rest.support.db.OptimisticLocking;
import org.folio.rest.support.messages.ItemEventMessageChecks;
import org.folio.rest.tools.utils.OptimisticLockingUtil;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class ItemStorageTest extends TestBaseWithInventoryUtil {
  private static final Logger log = LogManager.getLogger();
  private static final String TAG_VALUE = "test-tag";
  private static final String DISCOVERY_SUPPRESS = "discoverySuppress";

  private final ItemEventMessageChecks itemMessageChecks
    = new ItemEventMessageChecks(KAFKA_CONSUMER);

  @SneakyThrows
  @Before
  public void beforeEach() {
    clearData();
    setupMaterialTypes();
    setupLoanTypes();
    setupLocations();

    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(Map.of());

    removeAllEvents();
  }

  @SneakyThrows
  @After
  public void afterEach() {
    setItemSequence(1);

    StorageTestSuite.checkForMismatchedIds("item");

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

    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    UUID id = UUID.randomUUID();
    final String inTransitServicePointId = UUID.randomUUID().toString();

    JsonObject itemToCreate = new JsonObject();

    itemToCreate.put("id", id.toString());
    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("barcode", "565578437802");
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("materialTypeId", journalMaterialTypeID);
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", ANNEX_LIBRARY_LOCATION_ID.toString());
    itemToCreate.put("tags", new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)));
    itemToCreate.put("copyNumber", "copy1");

    itemToCreate.put("itemLevelCallNumber", callNumber);
    itemToCreate.put("itemLevelCallNumberSuffix", suffix);
    itemToCreate.put("itemLevelCallNumberPrefix", prefix);
    itemToCreate.put("itemLevelCallNumberTypeId", LC_CN_TYPE_ID);
    itemToCreate.put("volume", volume);
    itemToCreate.put("enumeration", enumeration);
    itemToCreate.put("chronology", chronology);
    itemToCreate.put("copyNumber", copy);

    itemToCreate.put("inTransitDestinationServicePointId", inTransitServicePointId);

    setItemSequence(1);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(itemsStorageUrl(""), itemToCreate, TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response postResponse = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject itemFromPost = postResponse.getJson();

    assertThat(itemFromPost.getString("effectiveShelvingOrder"), is(desiredShelvingOrder));
  }

  @Test
  public void canCreateAnItemViaCollectionResource() throws InterruptedException, ExecutionException, TimeoutException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    UUID id = UUID.randomUUID();
    final String inTransitServicePointId = UUID.randomUUID().toString();
    String adminNote = "an admin note";
    String displaySummary = "Important item";

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
    itemToCreate.put("displaySummary", displaySummary);
    itemToCreate.put("materialTypeId", journalMaterialTypeID);
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", ANNEX_LIBRARY_LOCATION_ID.toString());
    itemToCreate.put("tags", new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)));
    itemToCreate.put("copyNumber", "copy1");

    itemToCreate.put("itemLevelCallNumber", "PS3623.R534 P37 2005");
    itemToCreate.put("itemLevelCallNumberSuffix", "allOwnComponentsCNS");
    itemToCreate.put("itemLevelCallNumberPrefix", "allOwnComponentsCNP");
    itemToCreate.put("itemLevelCallNumberTypeId", LC_CN_TYPE_ID);

    itemToCreate.put("statisticalCodeIds", List.of(statisticalCodeId));

    itemToCreate.put("inTransitDestinationServicePointId", inTransitServicePointId);

    setItemSequence(1);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(itemsStorageUrl(""), itemToCreate, TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response postResponse = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject itemFromPost = postResponse.getJson();

    assertThat(itemFromPost.getString("id"), is(id.toString()));
    assertThat(itemFromPost.getJsonArray("administrativeNotes").contains(adminNote), is(true));
    assertThat(itemFromPost.getString("holdingsRecordId"), is(holdingsRecordId.toString()));
    assertThat(itemFromPost.getString("barcode"), is("565578437802"));
    assertThat(itemFromPost.getJsonObject("status").getString("name"),
      is("Available"));
    assertThat(itemFromPost.getString("displaySummary"), is(displaySummary));
    assertThat(itemFromPost.getString("materialTypeId"),
      is(journalMaterialTypeID));
    assertThat(itemFromPost.getString("permanentLoanTypeId"),
      is(canCirculateLoanTypeID));
    assertThat(itemFromPost.getString("temporaryLocationId"),
      is(ANNEX_LIBRARY_LOCATION_ID.toString()));
    assertThat(itemFromPost.getString("inTransitDestinationServicePointId"),
      is(inTransitServicePointId));
    assertThat(itemFromPost.getString("hrid"), is("it00000000001"));
    assertThat(itemFromPost.getString("copyNumber"), is("copy1"));
    assertThat(itemFromPost.getString("effectiveShelvingOrder"),
      is("PS 43623 R534 P37 42005 COP Y1 allOwnComponentsCNS"));

    Response getResponse = getById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject itemFromGet = getResponse.getJson();

    assertThat(itemFromGet.getString("id"), is(id.toString()));
    assertThat(itemFromGet.getJsonArray("administrativeNotes").contains(adminNote), is(true));
    assertThat(itemFromGet.getString("holdingsRecordId"), is(holdingsRecordId.toString()));
    assertThat(itemFromGet.getString("barcode"), is("565578437802"));
    assertThat(itemFromGet.getJsonObject("status").getString("name"),
      is("Available"));
    assertThat(itemFromPost.getString("displaySummary"), is(displaySummary));
    assertThat(itemFromGet.getString("materialTypeId"),
      is(journalMaterialTypeID));
    assertThat(itemFromGet.getString("permanentLoanTypeId"),
      is(canCirculateLoanTypeID));
    assertThat(itemFromGet.getString("temporaryLocationId"),
      is(ANNEX_LIBRARY_LOCATION_ID.toString()));
    assertThat(itemFromGet.getString("inTransitDestinationServicePointId"),
      is(inTransitServicePointId));
    assertThat(itemFromGet.getString("hrid"), is("it00000000001"));

    List<String> tags = getTags(itemFromGet);

    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
    assertThat(itemFromGet.getString("copyNumber"), is("copy1"));
    assertThat(itemFromGet.getString("effectiveShelvingOrder"),
      is("PS 43623 R534 P37 42005 COP Y1 allOwnComponentsCNS"));
    assertThat(itemFromGet.getJsonArray("statisticalCodeIds"), hasItem(statisticalCodeId.toString()));

    itemMessageChecks.createdMessagePublished(itemFromGet);
  }

  @SneakyThrows
  @Test
  public void canCreateAnItemWithMinimalProperties() {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    UUID id = UUID.randomUUID();

    JsonObject itemToCreate = new JsonObject()
      .put("id", id.toString())
      .put("status", new JsonObject().put("name", "Available"))
      .put("holdingsRecordId", holdingsRecordId.toString())
      .put("materialTypeId", journalMaterialTypeID)
      .put("permanentLoanTypeId", canCirculateLoanTypeID)
      .put("tags", new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)));

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(itemsStorageUrl(""), itemToCreate, TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response postResponse = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

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

  @SneakyThrows
  @Test
  public void shouldCreateAnItemWithCirculationNoteIdsPopulated() {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    UUID itemId = UUID.randomUUID();

    JsonObject checkInNoteWithoutId = new JsonObject()
      .put("noteType", "Check in")
      .put("note", "Check in note")
      .put("staffOnly", false);

    UUID circulationNoteId = UUID.randomUUID();
    JsonObject checkOutNoteWithId = new JsonObject()
      .put("id", circulationNoteId)
      .put("noteType", "Check out")
      .put("note", "Check out note")
      .put("staffOnly", false);

    JsonObject itemToCreate = new JsonObject()
      .put("id", itemId.toString())
      .put("status", new JsonObject().put("name", "Available"))
      .put("holdingsRecordId", holdingsRecordId.toString())
      .put("materialTypeId", journalMaterialTypeID)
      .put("permanentLoanTypeId", canCirculateLoanTypeID)
      .put("tags", new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))
      .put("circulationNotes", new JsonArray().add(checkInNoteWithoutId).add(checkOutNoteWithId));

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(itemsStorageUrl(""), itemToCreate, TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response postResponse = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create item: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject itemFromPost = postResponse.getJson();

    assertThat(itemFromPost.getString("id"), is(itemId.toString()));

    Response getResponse = getById(itemId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject itemFromGet = getResponse.getJson();

    assertThat(itemFromGet.getString("id"), is(itemId.toString()));

    JsonArray savedCirculationNotes = itemFromGet.getJsonArray("circulationNotes");

    assertThat(savedCirculationNotes.size(), is(2));

    JsonObject firstCirculationNote = savedCirculationNotes.getJsonObject(0);
    assertNotNull(firstCirculationNote.getString("id"));
    if (Objects.equals(firstCirculationNote.getString("noteType"), "Check out")) {
      assertThat(firstCirculationNote.getString("id"), is(circulationNoteId.toString()));
    }

    JsonObject secondCirculationNote = savedCirculationNotes.getJsonObject(1);
    assertNotNull(secondCirculationNote.getString("id"));
    if (Objects.equals(secondCirculationNote.getString("noteType"), "Check out")) {
      assertThat(secondCirculationNote.getString("id"), is(circulationNoteId.toString()));
    }
  }

  @Test
  public void canReplaceItemWithNewProperties() {
    final UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    final UUID id = UUID.randomUUID();
    final String expectedCopyNumber = "copy1";
    final String adminNote = "an admin note";
    final String displaySummary = "Important item";

    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId);
    createItem(itemToCreate);

    JsonObject createdItem = getById(id).getJson();
    assertThat(createdItem.getString("copyNumber"), nullValue());

    JsonObject updatedItem = createdItem.copy()
      .put("copyNumber", expectedCopyNumber)
      .put("displaySummary", displaySummary)
      .put("administrativeNotes", new JsonArray().add(adminNote));

    itemsClient.replace(id, updatedItem);

    JsonObject updatedItemResponse = itemsClient.getById(id).getJson();

    assertThat(updatedItemResponse.getString("copyNumber"), is(expectedCopyNumber));
    assertThat(updatedItemResponse.getJsonArray("administrativeNotes").contains(adminNote), is(true));
    assertThat(updatedItemResponse.getString("displaySummary"), is(displaySummary));

    itemMessageChecks.updatedMessagePublished(createdItem, getById(id).getJson());
  }

  @Test
  public void canMoveItemToNewInstance() {
    final UUID oldHoldingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    final UUID newHoldingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    final UUID id = UUID.randomUUID();

    JsonObject itemToCreate = smallAngryPlanet(id, oldHoldingsRecordId);
    createItem(itemToCreate);

    JsonObject createdItem = getById(id).getJson();

    assertThat(createdItem.getString("copyNumber"), nullValue());

    JsonObject updatedItem = createdItem.copy()
      .put("holdingsRecordId", newHoldingsRecordId.toString());

    itemsClient.replace(id, updatedItem);

    itemMessageChecks.updatedMessagePublished(createdItem, getById(id).getJson());
  }

  @Test
  public void optimisticLockingVersion() {
    UUID itemId = UUID.randomUUID();
    UUID holdingId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    JsonObject item = createItem(nod(itemId, holdingId));
    item.put(PERMANENT_LOCATION_ID_KEY, ANNEX_LIBRARY_LOCATION_ID);

    // Clear Kafka events after create to reduce chances of
    // CREATE messages appearing after UPDATE later on.
    // This should be removed once the messaging problem is
    // properly resolved.
    removeAllEvents();

    // updating with current _version 1 succeeds and increments _version to 2
    assertThat(update(item).getStatusCode(), is(204));
    item.put(PERMANENT_LOCATION_ID_KEY, SECOND_FLOOR_LOCATION_ID);

    // Clear Kafka events, see first removeAllEvents() comments above.
    removeAllEvents();

    // updating with outdated _version 1 fails, current _version is 2
    int expected = OptimisticLocking.hasFailOnConflict("item") ? 409 : 204;

    assertThat(update(item).getStatusCode(), is(expected));

    // Clear Kafka events, see first removeAllEvents() comments above.
    removeAllEvents();

    // updating with _version -1 should fail, single item PUT never allows to suppress optimistic locking
    item.put("_version", -1);
    assertThat(update(item).getStatusCode(), is(409));

    // Clear Kafka events, see first removeAllEvents() comments above.
    removeAllEvents();

    // this allow should not apply to single holding PUT, only to batch unsafe
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
      Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));
    item.put("_version", -1);
    assertThat(update(item).getStatusCode(), is(409));
  }

  @Test
  public void shouldNotUpdateItemIfNoChanges() {
    var itemId = UUID.randomUUID();
    var holdingId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    var item = createItem(nod(itemId, holdingId));
    itemMessageChecks.createdMessagePublished(itemId.toString());

    assertThat(update(item).getStatusCode(), is(204));

    var updatedItem = getById(itemId).getJson();
    //assert that there was no update in database
    assertThat(updatedItem.getString("_version"), is("1"));
    var kafkaEvents = KAFKA_CONSUMER.getMessagesForItem(itemId.toString());
    //assert that there's only CREATE kafka message, no updates
    assertThat(kafkaEvents.size(), is(1));
  }

  @Test
  public void canCreateAnItemWithoutProvidingId() throws InterruptedException, ExecutionException, TimeoutException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    JsonObject itemToCreate = nod(null, holdingsRecordId);

    itemToCreate.put("tags", new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)));

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(itemsStorageUrl(""), itemToCreate, TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response postResponse = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

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
      is(ANNEX_LIBRARY_LOCATION_ID.toString()));

    List<String> tags = getTags(itemFromGet);

    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
  }

  @Test
  public void canCreateAnItemWithHridSupplied() throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting canCreateAnItemWithHRIDSupplied");

    final UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    final UUID id = UUID.randomUUID();

    final JsonObject itemToCreate = new JsonObject()
      .put("id", id.toString())
      .put("status", new JsonObject().put("name", "Available"))
      .put("holdingsRecordId", holdingsRecordId.toString())
      .put("materialTypeId", journalMaterialTypeID)
      .put("permanentLoanTypeId", canCirculateLoanTypeID)
      .put("hrid", "ITEM12345")
      .put("tags", new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)));

    final CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(itemsStorageUrl(""), itemToCreate, TENANT_ID,
      ResponseHandler.json(createCompleted));

    final Response postResponse = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create item: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(postResponse.getJson().getString("hrid"), is("ITEM12345"));

    final Response getResponse = getById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getResponse.getJson().getString("hrid"), is("ITEM12345"));

    log.info("Finished canCreateAnItemWithHRIDSupplied");
  }

  @Test
  public void canUpdateAnItemWhenHridHasNotChanged() {
    log.info("Starting canUpdateAnItemHRIDDoesNotChange");

    final UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
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

    getClient().put(itemsStorageUrl("/" + itemId), itemToCreate, TENANT_ID,
      ResponseHandler.text(completed));

    final Response getResponse = getById(UUID.fromString(itemId));

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getResponse.getJson().getString("hrid"), is("it00000000001"));

    log.info("Finished canUpdateAnItemHRIDDoesNotChange");
  }

  @SneakyThrows
  @Test
  public void shouldUpdateItemWithCirculationNoteIdsPopulated() {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    UUID itemId = UUID.randomUUID();

    JsonObject itemToUpdate = new JsonObject()
      .put("id", itemId.toString())
      .put("status", new JsonObject().put("name", "Available"))
      .put("holdingsRecordId", holdingsRecordId.toString())
      .put("permanentLoanTypeId", canCirculateLoanTypeID)
      .put("materialTypeId", bookMaterialTypeID);

    setItemSequence(1);

    // create item
    createItem(itemToUpdate);

    // populate circulationNotes and other necessary fields
    JsonObject checkInNoteWithoutId = new JsonObject()
      .put("noteType", "Check in")
      .put("note", "Check in note")
      .put("staffOnly", false);

    UUID circulationNoteId = UUID.randomUUID();
    JsonObject checkOutNoteWithId = new JsonObject()
      .put("id", circulationNoteId)
      .put("noteType", "Check out")
      .put("note", "Check out note")
      .put("staffOnly", false);

    itemToUpdate.put("circulationNotes", new JsonArray().add(checkInNoteWithoutId).add(checkOutNoteWithId));
    itemToUpdate.put("hrid", "it00000000001");
    itemToUpdate.put("_version", "1");

    // update item
    update(itemToUpdate);

    Response getResponse = getById(itemId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject itemFromGet = getResponse.getJson();

    assertThat(itemFromGet.getString("id"), is(itemId.toString()));

    JsonArray updatedCirculationNotes = itemFromGet.getJsonArray("circulationNotes");

    assertThat(updatedCirculationNotes.size(), is(2));

    JsonObject firstCirculationNote = updatedCirculationNotes.getJsonObject(0);
    assertNotNull(firstCirculationNote.getString("id"));
    if (Objects.equals(firstCirculationNote.getString("noteType"), "Check out")) {
      assertThat(firstCirculationNote.getString("id"), is(circulationNoteId.toString()));
    }

    JsonObject secondCirculationNote = updatedCirculationNotes.getJsonObject(1);
    assertNotNull(secondCirculationNote.getString("id"));
    if (Objects.equals(secondCirculationNote.getString("noteType"), "Check out")) {
      assertThat(secondCirculationNote.getString("id"), is(circulationNoteId.toString()));
    }
  }

  @Test
  public void cannotAddNonExistentPermanentLocation()
    throws InterruptedException, ExecutionException, TimeoutException {
    String badLocation = UUID.randomUUID().toString();
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    String id = UUID.randomUUID().toString();

    JsonObject itemToCreate = new JsonObject()
      .put("id", id)
      .put("status", new JsonObject().put("name", "Available"))
      .put("holdingsRecordId", holdingsRecordId.toString())
      .put("materialTypeId", journalMaterialTypeID)
      .put("permanentLoanTypeId", canCirculateLoanTypeID)
      .put("permanentLocationId", badLocation);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(itemsStorageUrl(""), itemToCreate, TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response postResponse = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(HTTP_UNPROCESSABLE_ENTITY.toInt()));

    assertThat(postResponse.getBody(),
      containsString("Cannot set item.permanentlocationid"));
  }

  @Test
  public void cannotAddNonExistentTemporaryLocation()
    throws InterruptedException, ExecutionException, TimeoutException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

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

    getClient().post(itemsStorageUrl(""), itemToCreate, TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response postResponse = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(HTTP_UNPROCESSABLE_ENTITY.toInt()));

    assertThat(postResponse.getBody(),
      containsString("Cannot set item.temporarylocationid"));
  }

  @Test
  public void cannotCreateAnItemWithIdThatIsNotUuid()
    throws InterruptedException, ExecutionException, TimeoutException {
    String id = "1234";
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    JsonObject itemToCreate = new JsonObject();

    itemToCreate.put("id", id);
    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("barcode", "565578437802");
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("materialTypeId", journalMaterialTypeID);
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", ANNEX_LIBRARY_LOCATION_ID.toString());

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(itemsStorageUrl(""), itemToCreate, TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response postResponse = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(HTTP_UNPROCESSABLE_ENTITY.toInt()));

    assertThat(postResponse.getBody(), containsString("UUID"));
  }

  @Test
  public void cannotCreateAnItemWithoutMaterialType()
    throws InterruptedException, ExecutionException, TimeoutException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    JsonObject itemToCreate = new JsonObject();

    UUID id = UUID.randomUUID();
    itemToCreate.put("id", id.toString());
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(itemsStorageUrl(""), itemToCreate, TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response postResponse = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(HTTP_UNPROCESSABLE_ENTITY.toInt()));

    List<JsonObject> errors = toList(
      postResponse.getJson().getJsonArray("errors"));

    assertThat(errors.size(), is(1));
    assertThat(errors, anyOf(
      hasItem(validationErrorMatches("may not be null", "materialTypeId")),
      hasItem(validationErrorMatches("must not be null", "materialTypeId"))));
  }

  @Test
  public void cannotCreateAnItemWithNonexistingMaterialType()
    throws InterruptedException, ExecutionException, TimeoutException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    JsonObject itemToCreate = new JsonObject();
    itemToCreate.put("id", UUID.randomUUID().toString());
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("materialTypeId", UUID.randomUUID().toString());

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(itemsStorageUrl(""), itemToCreate, TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response postResponse = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(HTTP_UNPROCESSABLE_ENTITY.toInt()));
    assertThat(postResponse.getBody(),
      containsString("Cannot set item.materialtypeid"));
  }

  @Test
  public void creatingItemLimitNoteMaximumLength() throws InterruptedException, ExecutionException, TimeoutException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    JsonObject itemToCreate = new JsonObject();
    itemToCreate.put("id", UUID.randomUUID().toString());
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("materialTypeId", UUID.randomUUID().toString());
    itemToCreate.put("notes", new JsonArray().add(new Note().withNote("x".repeat(MAX_NOTE_LENGTH + 1))));

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(itemsStorageUrl(""), itemToCreate, TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response postResponse = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(422));
  }

  @Test
  public void creatingItemLimitAdministrativeNoteMaximumLength()
    throws InterruptedException, ExecutionException, TimeoutException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    JsonObject itemToCreate = new JsonObject();
    itemToCreate.put("id", UUID.randomUUID().toString());
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("materialTypeId", UUID.randomUUID().toString());
    itemToCreate.put("administrativeNotes", new JsonArray().add("x".repeat(MAX_NOTE_LENGTH + 1)));

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(itemsStorageUrl(""), itemToCreate, TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response postResponse = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(422));
  }

  @Test
  public void updatingItemLimitAdministrativeNoteMaximumLength()
    throws InterruptedException, ExecutionException, TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
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
    itemToCreate.put("administrativeNotes", new JsonArray().add("x".repeat(MAX_NOTE_LENGTH + 1)));

    CompletableFuture<Response> completed = new CompletableFuture<>();
    getClient().put(itemsStorageUrl("/" + itemId), itemToCreate, TENANT_ID,
      ResponseHandler.json(completed));
    Response response = completed.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(422));
  }

  @Test
  public void updatingItemLimitNoteMaximumLength() {
    final UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    final UUID id = UUID.randomUUID();

    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId);

    createItem(itemToCreate);
    JsonObject item = getById(id).getJson();
    item.put("notes", new JsonArray().add(new Note().withNote("x".repeat(MAX_NOTE_LENGTH + 1))));
    assertThat(update(item).getStatusCode(), is(422));
  }

  @Test
  public void cannotCreateAnItemWithDuplicateHrid() throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting cannotCreateAnItemWithDuplicateHRID");

    final UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

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

    getClient().post(itemsStorageUrl(""), itemToCreate, TENANT_ID,
      json(createCompleted));

    final Response postResponse = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(HTTP_UNPROCESSABLE_ENTITY.toInt()));

    final Errors errors = postResponse.getJson().mapTo(Errors.class);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), notNullValue());
    var error = errors.getErrors().getFirst();
    assertThat(error, notNullValue());
    assertThat(error.getMessage(),
      is("HRID value already exists in table item: it00000000001"));
    assertThat(error.getParameters(), notNullValue());
    var parameter = error.getParameters().getFirst();
    assertThat(parameter, notNullValue());
    assertThat(parameter.getKey(),
      is("lower(f_unaccent(jsonb ->> 'hrid'::text))"));
    assertThat(parameter.getValue(),
      is("it00000000001"));

    log.info("Finished cannotCreateAnItemWithDuplicateHRID");
  }

  @Test
  public void cannotCreateAnItemWithHridFailure() throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting cannotCreateAnItemWithHRIDFailure");

    final UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

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

    getClient().post(itemsStorageUrl(""), itemToCreate, TENANT_ID,
      text(createCompleted));

    final Response postResponse = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(HTTP_INTERNAL_ERROR));
    assertThat(postResponse.getBody(), isMaximumSequenceValueError("hrid_items_seq"));

    log.info("Finished cannotCreateAnItemWithHRIDFailure");
  }

  @Test
  public void cannotUpdateAnItemWithNonexistingMaterialType()
    throws InterruptedException, ExecutionException, TimeoutException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
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
    getClient().put(itemsStorageUrl("/" + itemId), itemToCreate, TENANT_ID,
      ResponseHandler.text(completed));
    Response response = completed.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    assertThat(response.getBody(), allOf(
      containsString("Cannot set item"), containsString("materialtypeid")));
  }

  @Test
  public void cannotUpdateAnItemWithChangedHrid() throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting cannotUpdateAnItemWithChangedHRID");

    final UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
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

    getClient().put(itemsStorageUrl("/" + itemId), itemToCreate, TENANT_ID,
      ResponseHandler.text(completed));

    final Response response = completed.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    assertThat(response.getBody(),
      is("The hrid field cannot be changed: new=ABC123, old=it00000000001"));

    log.info("Finished cannotUpdateAnItemWithChangedHRID");
  }

  @Test
  public void cannotUpdateAnItemWithRemovedHrid() throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting cannotUpdateAnItemWithRemovedHRID");

    final UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
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

    getClient().put(itemsStorageUrl("/" + itemId), itemToCreate, TENANT_ID,
      ResponseHandler.text(completed));

    final Response response = completed.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    assertThat(response.getBody(),
      is("The hrid field cannot be changed: new=null, old=it00000000001"));

    log.info("Finished cannotUpdateAnItemWithRemovedHRID");
  }

  @Test
  public void cannotCreateItemWithNonUuidStatisticalCodes() {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    JsonObject nod = nod(holdingsRecordId).put("statisticalCodeIds", new JsonArray().add("07"));
    assertThat(itemsClient.attemptToCreate(nod), hasValidationError(
      "elements in list must match pattern", "statisticalCodeIds", "[07]"));
  }

  @Test
  public void cannotCreateItemSyncWithNonUuidStatisticalCodes() {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    JsonObject nod = nod(holdingsRecordId).put("statisticalCodeIds",
      new JsonArray().add("00000000-0000-4444-8888-000000000000").add("12345678"));
    JsonObject items = new JsonObject().put("items", new JsonArray().add(nod));
    assertThat(itemsStorageSyncClient.attemptToCreate(items), hasValidationError(
      "elements in list must match pattern", "items[0].statisticalCodeIds",
      "[00000000-0000-4444-8888-000000000000, 12345678]"));
  }

  @Test
  public void cannotUpdateItemWithNonUuidStatisticalCodes() {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    JsonObject nod = createItem(nod(holdingsRecordId));
    nod.put("statisticalCodeIds", new JsonArray().add("1234567890123456789012345678901234567890"));
    assertThat(itemsClient.attemptToReplace(nod.getString("id"), nod), hasValidationError(
      "elements in list must match pattern", "statisticalCodeIds",
      "[1234567890123456789012345678901234567890]"));
  }

  @Test
  public void canCreateAnItemWithManyProperties() throws InterruptedException, ExecutionException, TimeoutException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    UUID id = UUID.randomUUID();

    final String inTransitServicePointId = UUID.randomUUID().toString();

    JsonObject itemToCreate = new JsonObject();

    itemToCreate.put("id", id.toString());
    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("barcode", "565578437802");
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("materialTypeId", journalMaterialTypeID);
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", ANNEX_LIBRARY_LOCATION_ID.toString());
    itemToCreate.put("tags", new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)));

    itemToCreate.put("inTransitDestinationServicePointId", inTransitServicePointId);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    getClient().post(itemsStorageUrl(""), itemToCreate,
      TENANT_ID, ResponseHandler.empty(createCompleted));

    Response postResponse = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    Response getResponse = getById(id);

    //PUT currently cannot return a response
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject item = getResponse.getJson();

    final List<String> tags = getTags(item);

    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("holdingsRecordId"), is(holdingsRecordId.toString()));
    assertThat(item.getString("barcode"), is("565578437802"));
    assertThat(item.getJsonObject("status").getString("name"), is("Available"));
    assertThat(item.getString("materialTypeId"), is(journalMaterialTypeID));
    assertThat(item.getString("permanentLoanTypeId"), is(canCirculateLoanTypeID));
    assertThat(item.getString("temporaryLocationId"), is(ANNEX_LIBRARY_LOCATION_ID.toString()));
    assertThat(item.getString("inTransitDestinationServicePointId"), is(inTransitServicePointId));
    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
  }

  @Test
  public void cannotProvideAdditionalPropertiesInItem()
    throws InterruptedException, TimeoutException, ExecutionException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    JsonObject requestWithAdditionalProperty = nod(UUID.randomUUID(), holdingsRecordId);

    requestWithAdditionalProperty.put("somethingAdditional", "foo");

    CompletableFuture<JsonErrorResponse> createCompleted = new CompletableFuture<>();

    getClient().post(itemsStorageUrl(""), requestWithAdditionalProperty,
      TENANT_ID, ResponseHandler.jsonErrors(createCompleted));

    JsonErrorResponse response = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HTTP_UNPROCESSABLE_ENTITY.toInt()));
    assertThat(response.getErrors(), hasSoleMessageContaining("Unrecognized field"));
  }

  @Test
  public void cannotProvideAdditionalPropertiesInItemStatus()
    throws InterruptedException, TimeoutException, ExecutionException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    JsonObject requestWithAdditionalProperty = nod(UUID.randomUUID(), holdingsRecordId);

    requestWithAdditionalProperty
      .put("status", new JsonObject().put("somethingAdditional", "foo"));

    CompletableFuture<JsonErrorResponse> createCompleted = new CompletableFuture<>();

    getClient().post(itemsStorageUrl(""), requestWithAdditionalProperty,
      TENANT_ID, ResponseHandler.jsonErrors(createCompleted));

    JsonErrorResponse response = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HTTP_UNPROCESSABLE_ENTITY.toInt()));
    assertThat(response.getErrors(), hasSoleMessageContaining("Unrecognized field"));
  }

  @Test
  public void cannotProvideAdditionalPropertiesInItemLocation()
    throws InterruptedException, TimeoutException, ExecutionException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    JsonObject requestWithAdditionalProperty = nod(UUID.randomUUID(), holdingsRecordId);

    requestWithAdditionalProperty
      .put("location", new JsonObject().put("somethingAdditional", "foo"));

    CompletableFuture<JsonErrorResponse> createCompleted = new CompletableFuture<>();

    getClient().post(itemsStorageUrl(""), requestWithAdditionalProperty,
      TENANT_ID, ResponseHandler.jsonErrors(createCompleted));

    JsonErrorResponse response = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HTTP_UNPROCESSABLE_ENTITY.toInt()));
    assertThat(response.getErrors(), hasSoleMessageContaining("Unrecognized field"));
  }

  @Test
  public void canPostSynchronousBatch() {
    JsonArray itemsArray = threeItems();
    assertThat(postSynchronousBatch(itemsArray), statusCodeIs(HttpStatus.HTTP_CREATED));
    for (Object item : itemsArray) {
      assertExists((JsonObject) item);
    }

    itemsArray.stream()
      .map(JsonObject.class::cast)
      .map(obj -> getById(obj.getString("id")).getJson())
      .forEach(itemMessageChecks::createdMessagePublished);
  }

  @Test
  public void cannotPostSynchronousBatchUnsafeIfNotAllowed() {
    // not allowed because env var DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING is not set
    JsonArray itemsArray = threeItems();
    assertThat(postSynchronousBatchUnsafe(itemsArray), statusCodeIs(413));
  }

  @Test
  public void canPostSynchronousBatchUnsafe() {
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
      Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));

    // insert
    JsonArray itemsArray = threeItems();
    assertThat(postSynchronousBatchUnsafe(itemsArray), statusCodeIs(HttpStatus.HTTP_CREATED));
    // unsafe update
    itemsArray.getJsonObject(1).put("barcode", "123");
    assertThat(postSynchronousBatchUnsafe(itemsArray), statusCodeIs(HttpStatus.HTTP_CREATED));
    // safe update, env var should not influence the regular API
    itemsArray.getJsonObject(1).put("barcode", "456");
    assertThat(postSynchronousBatch("?upsert=true", itemsArray), statusCodeIs(409));
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
    for (int i = 0; i < itemsArray.size(); i++) {
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
  public void canPostSynchronousBatchWithExistingIdUpsertTrue() {
    final UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
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
      .forEach(itemMessageChecks::createdMessagePublished);

    itemMessageChecks.updatedMessagePublished(existingItemBeforeUpdate,
      getById(existingItemId).getJson());
  }

  @Test
  public void canPostSynchronousBatchWithGeneratedHrid() {
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
        assertHridRange(response, "it00000000001", "it00000000003");
      });

    log.info("Finished canPostSynchronousBatchWithGeneratedHRID");
  }

  @Test
  public void canPostSynchronousBatchWithSuppliedAndGeneratedHrid() {
    log.info("Starting canPostSynchronousBatchWithSuppliedAndGeneratedHRID");

    setItemSequence(1);

    final String hrid = "ABC123";
    final JsonArray itemsArray = threeItems();

    itemsArray.getJsonObject(1).put("hrid", hrid);

    assertThat(postSynchronousBatch(itemsArray), statusCodeIs(HttpStatus.HTTP_CREATED));

    Response response = getById(itemsArray.getJsonObject(0).getString("id"));
    assertExists(response, itemsArray.getJsonObject(0));
    assertHridRange(response, "it00000000001", "it00000000002");

    response = getById(itemsArray.getJsonObject(1).getString("id"));
    assertExists(response, itemsArray.getJsonObject(1));
    assertThat(response.getJson().getString("hrid"), is(hrid));

    response = getById(itemsArray.getJsonObject(2).getString("id"));
    assertExists(response, itemsArray.getJsonObject(2));
    assertHridRange(response, "it00000000001", "it00000000002");

    log.info("Finished canPostSynchronousBatchWithSuppliedAndGeneratedHRID");
  }

  @Test
  public void cannotPostSynchronousBatchWithDuplicateHrids() {
    log.info("Starting cannotPostSynchronousBatchWithDuplicateHRIDs");

    setItemSequence(1);

    final JsonArray itemsArray = threeItems();
    final String duplicateHrid = "it00000000001";
    itemsArray.getJsonObject(0).put("hrid", duplicateHrid);
    itemsArray.getJsonObject(1).put("hrid", duplicateHrid);

    Response response = postSynchronousBatch(itemsArray);
    assertThat(response.getStatusCode(), is(422));

    final Errors errors = response.getJson().mapTo(Errors.class);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), notNullValue());
    var error = errors.getErrors().getFirst();
    assertThat(error, notNullValue());
    assertThat(error.getMessage(),
      is("HRID value already exists in table item: it00000000001"));
    assertThat(error.getParameters(), notNullValue());
    var parameter = error.getParameters().getFirst();
    assertThat(parameter, notNullValue());
    assertThat(parameter.getKey(),
      is("lower(f_unaccent(jsonb ->> 'hrid'::text))"));
    assertThat(parameter.getValue(),
      is("it00000000001"));

    for (int i = 0; i < itemsArray.size(); i++) {
      assertGetNotFound(itemsStorageUrl("/" + itemsArray.getJsonObject(i).getString("id")));
    }

    log.info("Finished cannotPostSynchronousBatchWithDuplicateHRIDs");
  }

  @Test
  public void cannotPostSynchronousBatchWithHridFailure() {
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
  public void canReplaceAnItemAtSpecificLocation() throws InterruptedException, ExecutionException, TimeoutException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    UUID id = UUID.randomUUID();
    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId)
      .put("hrid", "testHRID");

    createItem(itemToCreate);

    JsonObject replacement = itemToCreate.copy();
    replacement.put("barcode", "125845734657")
      .put("temporaryLocationId", MAIN_LIBRARY_LOCATION_ID.toString())
      .put("tags", new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)));

    CompletableFuture<Response> replaceCompleted = new CompletableFuture<>();

    getClient().put(itemsStorageUrl(String.format("/%s", id)), replacement,
      TENANT_ID, ResponseHandler.empty(replaceCompleted));

    Response putResponse = replaceCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse = getById(id);

    //PUT currently cannot return a response
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject item = getResponse.getJson();

    final List<String> tags = getTags(item);

    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("holdingsRecordId"), is(holdingsRecordId.toString()));
    assertThat(item.getString("barcode"), is("125845734657"));
    assertThat(item.getJsonObject("status").getString("name"),
      is("Available"));
    assertThat(item.getString("materialTypeId"),
      is(journalMaterialTypeID));
    assertThat(item.getString("temporaryLocationId"),
      is(MAIN_LIBRARY_LOCATION_ID.toString()));
    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
  }

  @Test
  public void canPlaceAnItemInTransit() throws InterruptedException, ExecutionException, TimeoutException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

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

    getClient().put(itemsStorageUrl(String.format("/%s", id)), replacement,
      TENANT_ID, ResponseHandler.empty(replaceCompleted));

    Response putResponse = replaceCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse = getById(id);

    //PUT currently cannot return a response
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject item = getResponse.getJson();

    final List<String> tags = getTags(item);

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
    throws InterruptedException, ExecutionException, TimeoutException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    UUID id = UUID.randomUUID();
    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId)
      .put("hrid", "testHRID");

    Item initialItem = createItem(itemToCreate).mapTo(Item.class);
    final Instant initialStatusDate = initialItem.getStatus().getDate().toInstant();

    JsonObject replacement = itemToCreate.copy();

    replacement
      .put("status", new JsonObject().put("name", "Checked out"));

    CompletableFuture<Response> replaceCompleted = new CompletableFuture<>();

    getClient().put(itemsStorageUrl(String.format("/%s", id)), replacement,
      TENANT_ID, ResponseHandler.empty(replaceCompleted));

    Response putResponse = replaceCompleted.get(TIMEOUT, TimeUnit.SECONDS);

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
    throws InterruptedException, ExecutionException, TimeoutException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    UUID id = UUID.randomUUID();
    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId)
      .put("hrid", "testHRID");

    createItem(itemToCreate);

    JsonObject replacement = itemToCreate.copy();

    replacement
      .put("status", new JsonObject().put("name", "Checked out"));

    CompletableFuture<Response> replaceCompleted = new CompletableFuture<>();

    getClient().put(itemsStorageUrl(String.format("/%s", id)), replacement,
      TENANT_ID, ResponseHandler.empty(replaceCompleted));

    Response putResponse = replaceCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse = getById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    Item item = getResponse.getJson().mapTo(Item.class);

    assertThat(item.getId(), is(id.toString()));

    assertThat(item.getStatus().getName().value(), is("Checked out"));

    assertThat(item.getStatus().getDate(),
      notNullValue());

    final Instant changedStatusDate = item.getStatus().getDate().toInstant();

    JsonObject secondReplacement = getResponse.getJson().copy();

    secondReplacement
      .put("status", new JsonObject().put("name", "Available"));

    CompletableFuture<Response> secondReplaceCompleted = new CompletableFuture<>();

    getClient().put(itemsStorageUrl(String.format("/%s", id)), secondReplacement,
      TENANT_ID, ResponseHandler.empty(secondReplaceCompleted));

    Response secondPutResponse = secondReplaceCompleted.get(TIMEOUT, TimeUnit.SECONDS);

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
  public void cannotUpdateStatusDate() {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
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
  public void cannotChangeStatusDate() {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
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
  public void statusUpdatedDateRemainsAfterUpdate() {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
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
  public void statusUpdatedDateIsUnchangedAfterUpdatesThatDoNotChangeStatus() {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
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
      .put("temporaryLocationId", ONLINE_LOCATION_ID.toString());

    itemsClient.replace(id, secondUpdateItem);

    JsonObject secondUpdatedItemResponse = itemsClient.getById(id).getJson();

    assertThat(secondUpdatedItemResponse.getString("temporaryLocationId"),
      is(ONLINE_LOCATION_ID.toString()));
    assertThat(secondUpdatedItemResponse.getJsonObject("status").getString("name"),
      is("Available"));
    assertThat(secondUpdatedItemResponse.getJsonObject("status").getString("date"),
      is(createdDate));
  }

  @Test
  public void canDeleteAnItem() throws InterruptedException, TimeoutException, ExecutionException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    UUID id = UUID.randomUUID();
    JsonObject itemToCreate = smallAngryPlanet(id, holdingsRecordId);

    final JsonObject createdItem = createItem(itemToCreate);

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    getClient().delete(itemsStorageUrl(String.format("/%s", id)),
      TENANT_ID, ResponseHandler.empty(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(itemsStorageUrl(String.format("/%s", id)),
      TENANT_ID, ResponseHandler.empty(getCompleted));

    Response getResponse = getCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));

    itemMessageChecks.deletedMessagePublished(createdItem);
  }

  @Test
  public void canPageAllItems() throws InterruptedException, ExecutionException, TimeoutException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    createItem(smallAngryPlanet(UUID.randomUUID(), holdingsRecordId));
    createItem(nod(UUID.randomUUID(), holdingsRecordId));
    createItem(uprooted(UUID.randomUUID(), holdingsRecordId));
    createItem(temeraire(UUID.randomUUID(), holdingsRecordId));
    createItem(interestingTimes(UUID.randomUUID(), holdingsRecordId));

    CompletableFuture<Response> firstPageCompleted = new CompletableFuture<>();
    CompletableFuture<Response> secondPageCompleted = new CompletableFuture<>();

    getClient().get(itemsStorageUrl("") + "?limit=3", TENANT_ID,
      ResponseHandler.json(firstPageCompleted));

    getClient().get(itemsStorageUrl("") + "?limit=3&offset=3", TENANT_ID,
      ResponseHandler.json(secondPageCompleted));

    Response firstPageResponse = firstPageCompleted.get(TIMEOUT, TimeUnit.SECONDS);
    Response secondPageResponse = secondPageCompleted.get(TIMEOUT, TimeUnit.SECONDS);

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
  public void canFetchItemsViaPostAPI() throws InterruptedException, ExecutionException, TimeoutException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    List<String> itemIdz = new ArrayList<>();
    int numOfItemsToCreate = 200;
    for (int i = 1; i <= numOfItemsToCreate; i++) {
      JsonObject itemCreateRequest = createItemRequest(UUID.randomUUID(), holdingsRecordId,
              RandomStringUtils.insecure().next(10));
      String itemId = createItem(itemCreateRequest).getString("id");
      itemIdz.add(itemId);
    }

    String idzWithOrDelimiter = "id==(" + String.join(" or ", itemIdz) + ")";
    RetrieveDto retrieveDto = new RetrieveDto();
    retrieveDto.setQuery(idzWithOrDelimiter);
    retrieveDto.setLimit(2000);

    CompletableFuture<Response> responseHandler = new CompletableFuture<>();
    getClient().post(itemsStorageUrl("/retrieve"), retrieveDto, TENANT_ID,
            ResponseHandler.json(responseHandler));

    Response response = responseHandler.get(TIMEOUT, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(200));

    JsonObject responseJson = response.getJson();
    JsonArray responseItems = responseJson.getJsonArray("items");

    assertThat(responseItems.size(), is(numOfItemsToCreate));
    assertThat(responseJson.getInteger("totalRecords"), is(numOfItemsToCreate));
  }

  @Test
  public void canCreateMultipleItemsWithoutBarcode() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    createItem(removeBarcode(nod(holdingsRecordId)));
    createItem(removeBarcode(uprooted(holdingsRecordId)));
    createItem(temeraire(holdingsRecordId).put("barcode", null));
    createItem(interestingTimes(holdingsRecordId).put("barcode", null));
    assertCqlFindsBarcodes("id==*", null, null, null, null);
  }

  @Test
  public void cannotCreateItemWithDuplicateBarcode() {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    createItem(nod(holdingsRecordId).put("barcode", "9876a"));
    assertThat(itemsClient.attemptToCreate(uprooted(holdingsRecordId).put("barcode", "9876a")),
      hasValidationError("9876a"));
    assertThat(itemsClient.attemptToCreate(uprooted(holdingsRecordId).put("barcode", "9876A")),
      hasValidationError("9876a"));
  }

  @Test
  public void cannotUpdateItemWithDuplicateBarcode() {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    createItem(uprooted(holdingsRecordId).put("barcode", "9876a"));
    UUID nodId = UUID.randomUUID();
    JsonObject nod = createItem(nod(nodId, holdingsRecordId).put("barcode", "123"));

    Response response = itemsClient.attemptToReplace(nodId, nod.put("barcode", "9876A"));
    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), containsString("already exists in table item: 9876a"));
  }

  public void canSearchForItemsByBarcodeWithLeadingZero() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
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
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
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
  public void canSearchForItemsByTags() throws InterruptedException, ExecutionException, TimeoutException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    createItem(addTags(holdingsRecordId));
    createItem(nod(holdingsRecordId));

    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

    String url = itemsStorageUrl("") + "?query=" + urlEncode("tags.tagList=" + TAG_VALUE);

    getClient().get(url,
      TENANT_ID, ResponseHandler.json(searchCompleted));

    Response searchResponse = searchCompleted.get(TIMEOUT, TimeUnit.SECONDS);

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
  public void canSearchForItemsByStatus() throws InterruptedException, ExecutionException, TimeoutException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    createItem(smallAngryPlanet(UUID.randomUUID(), holdingsRecordId));
    createItem(nod(UUID.randomUUID(), holdingsRecordId));
    createItem(uprooted(UUID.randomUUID(), holdingsRecordId));
    createItem(temeraire(UUID.randomUUID(), holdingsRecordId));
    createItem(interestingTimes(UUID.randomUUID(), holdingsRecordId));

    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

    String url = itemsStorageUrl("") + "?query=" + urlEncode("status.name==\"Available\"");

    getClient().get(url,
      TENANT_ID, ResponseHandler.json(searchCompleted));

    Response searchResponse = searchCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(searchResponse.getStatusCode(), is(200));

    JsonObject searchBody = searchResponse.getJson();

    JsonArray foundItems = searchBody.getJsonArray("items");

    assertThat(searchBody.getInteger("totalRecords"), is(5));

    assertThat(foundItems.size(), is(5));
  }

  @Test
  public void cannotSearchForItemsByBarcodeAndNotMatchingId()
    throws InterruptedException, ExecutionException, TimeoutException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    createItem(nod(holdingsRecordId));
    createItem(uprooted(UUID.randomUUID(), holdingsRecordId));
    createItem(smallAngryPlanet(holdingsRecordId).put("barcode", "673274826203"));
    createItem(temeraire(UUID.randomUUID(), holdingsRecordId));
    createItem(interestingTimes(UUID.randomUUID(), holdingsRecordId));

    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

    String url = itemsStorageUrl("") + "?query=" + urlEncode(String.format(
      "barcode==\"673274826203\" and id<>%s'", UUID.randomUUID()));

    getClient().get(url,
      TENANT_ID, ResponseHandler.json(searchCompleted));

    Response searchResponse = searchCompleted.get(TIMEOUT, TimeUnit.SECONDS);

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

    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    createItem(smallAngryPlanet(holdingsRecordId)
      .put("barcode", "673274826203")
      .put("tags", new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE))));

    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

    // StackOverflowError in java.util.regex.Pattern https://issues.folio.org/browse/CIRC-119
    String url = itemsStorageUrl("") + "?query="
                 + urlEncode("barcode==("
                             + "a or b or c or d or e or f or g or h or j or k or l or m or n or o or p or q or s "
                             + "or t or u or v or w or x or y or z or 673274826203)");

    getClient().get(url,
      TENANT_ID, ResponseHandler.json(searchCompleted));

    Response searchResponse = searchCompleted.get(TIMEOUT, TimeUnit.SECONDS);
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
    UUID holdingsWithPermLocation = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    UUID holdingsWithTempLocation = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID, ANNEX_LIBRARY_LOCATION_ID);

    Item itemWithHoldingPermLocation = buildItem(holdingsWithPermLocation, null, null);
    Item itemWithHoldingTempLocation = buildItem(holdingsWithTempLocation, null, null);
    Item itemWithTempLocation = buildItem(holdingsWithPermLocation, ONLINE_LOCATION_ID, null);
    Item itemWithPermLocation = buildItem(holdingsWithTempLocation, null, SECOND_FLOOR_LOCATION_ID);
    Item itemWithAllLocation = buildItem(holdingsWithTempLocation, SECOND_FLOOR_LOCATION_ID, ONLINE_LOCATION_ID);

    Item[] itemsToCreate = {itemWithHoldingPermLocation, itemWithHoldingTempLocation,
                            itemWithTempLocation, itemWithPermLocation, itemWithAllLocation};

    for (Item item : itemsToCreate) {
      IndividualResource createdItem = createItem(item);
      assertTrue(createdItem.getJson().containsKey("effectiveLocationId"));
    }

    final Items mainLibraryItems = findItems("effectiveLocationId=" + MAIN_LIBRARY_LOCATION_ID);
    final Items annexLibraryItems = findItems("effectiveLocationId=" + ANNEX_LIBRARY_LOCATION_ID);
    final Items onlineLibraryItems = findItems("effectiveLocationId=" + ONLINE_LOCATION_ID);
    final Items secondFloorLibraryItems = findItems("effectiveLocationId=" + SECOND_FLOOR_LOCATION_ID);

    assertEquals(1, mainLibraryItems.getTotalRecords().intValue());
    assertThat(mainLibraryItems.getItems().getFirst().getId(), is(itemWithHoldingPermLocation.getId()));

    assertEquals(1, annexLibraryItems.getTotalRecords().intValue());
    assertThat(annexLibraryItems.getItems().getFirst().getId(), is(itemWithHoldingTempLocation.getId()));

    assertEquals(2, onlineLibraryItems.getTotalRecords().intValue());

    assertThat(onlineLibraryItems.getItems()
        .stream()
        .map(Item::getId)
        .toList(),
      hasItems(itemWithTempLocation.getId(), itemWithAllLocation.getId()));

    assertEquals(1, secondFloorLibraryItems.getTotalRecords().intValue());
    assertThat(secondFloorLibraryItems.getItems().getFirst().getId(), is(itemWithPermLocation.getId()));
  }

  @Test
  public void cannotSearchForItemsUsingDefaultField()
    throws InterruptedException, ExecutionException, TimeoutException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    createItem(smallAngryPlanet(holdingsRecordId));
    createItem(nod(holdingsRecordId));
    createItem(uprooted(UUID.randomUUID(), holdingsRecordId));
    createItem(temeraire(UUID.randomUUID(), holdingsRecordId));
    createItem(interestingTimes(UUID.randomUUID(), holdingsRecordId));

    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

    String url = itemsStorageUrl("") + "?query=t";

    getClient().get(url,
      TENANT_ID, ResponseHandler.text(searchCompleted));

    Response searchResponse = searchCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(searchResponse.getStatusCode(), is(400));

    String error = searchResponse.getBody();

    assertThat(error, containsString(
      "QueryValidationException: cql.serverChoice requested, but no serverChoiceIndexes defined."));
  }

  @Test
  public void canDeleteAllItems() throws InterruptedException, ExecutionException, TimeoutException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    createItem(smallAngryPlanet(holdingsRecordId));
    createItem(nod(holdingsRecordId));
    createItem(uprooted(UUID.randomUUID(), holdingsRecordId));
    createItem(temeraire(UUID.randomUUID(), holdingsRecordId));
    createItem(interestingTimes(UUID.randomUUID(), holdingsRecordId));

    CompletableFuture<Response> deleteAllFinished = new CompletableFuture<>();

    getClient().delete(itemsStorageUrl("?query=cql.allRecords=1"), TENANT_ID,
      ResponseHandler.empty(deleteAllFinished));

    Response deleteResponse = deleteAllFinished.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(itemsStorageUrl(""), TENANT_ID,
      ResponseHandler.json(getCompleted));

    Response response = getCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    JsonObject responseBody = response.getJson();

    JsonArray allItems = responseBody.getJsonArray("items");

    assertThat(allItems.size(), is(0));
    assertThat(responseBody.getInteger("totalRecords"), is(0));

    itemMessageChecks.allItemsDeletedMessagePublished();
  }

  @SneakyThrows
  @Test
  public void canDeleteItemsByCql() {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    final var item1 = createItem(smallAngryPlanet(holdingsRecordId).put("barcode", "1234"));
    final var item2 = createItem(nod(holdingsRecordId).put("barcode", "23"));
    final var item3 = createItem(uprooted(UUID.randomUUID(), holdingsRecordId).put("barcode", "12"));
    final var item4 = createItem(temeraire(UUID.randomUUID(), holdingsRecordId).put("barcode", "234"));
    final var item5 = createItem(interestingTimes(UUID.randomUUID(), holdingsRecordId).put("barcode", "123"));

    var response = getClient().delete(itemsStorageUrl("?query=barcode==12*"), TENANT_ID).get(10, SECONDS);

    assertThat(response.getStatusCode(), is(204));
    assertExists(item2);
    assertExists(item4);
    assertNotExists(item1);
    assertNotExists(item3);
    assertNotExists(item5);

    itemMessageChecks.deletedMessagePublished(item1);
    itemMessageChecks.deletedMessagePublished(item3);
    itemMessageChecks.deletedMessagePublished(item5);
  }

  @SneakyThrows
  @Parameters({
    "",
    "?query=",
    "?query=%20%20",
  })
  @Test
  public void cannotDeleteItemsWithoutCql(String query) {
    var response = getClient().delete(itemsStorageUrl(query), TENANT_ID).get(10, SECONDS);

    assertThat(response.getBody(), is("Expected CQL but query parameter is empty"));
    assertThat(response.getStatusCode(), is(400));
  }

  @SneakyThrows
  @Test
  public void cannotDeleteItemsWithInvalidCql() {
    var response = getClient().delete(itemsStorageUrl("?query=\""), TENANT_ID).get(10, SECONDS);

    assertThat(response.getBody(), containsStringIgnoringCase("parse"));
    assertThat(response.getStatusCode(), is(400));
  }

  @Test
  public void tenantIsRequiredForCreatingNewItem() throws InterruptedException, ExecutionException, TimeoutException {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    getClient().post(itemsStorageUrl(""), smallAngryPlanet(holdingsRecordId), null, ResponseHandler.any(postCompleted));

    Response response = postCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @Test
  public void tenantIsRequiredForGettingAnItem()
    throws InterruptedException, ExecutionException, TimeoutException {

    URL getInstanceUrl = itemsStorageUrl(String.format("/%s",
      UUID.randomUUID()));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(getInstanceUrl, null, ResponseHandler.any(getCompleted));

    Response response = getCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @Test
  public void tenantIsRequiredForGettingAllItems()
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(itemsStorageUrl(""), null, ResponseHandler.any(getCompleted));

    Response response = getCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @Test
  @SneakyThrows
  public void testItemHasLastCheckInProperties() {
    UUID itemId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID servicePointId = UUID.randomUUID();
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    JsonObject itemData = smallAngryPlanet(itemId, holdingsRecordId)
      .put("hrid", "testHRID");
    createItem(itemData);

    LastCheckIn expected = new LastCheckIn();
    expected.setStaffMemberId(userId.toString());
    expected.setServicePointId(servicePointId.toString());
    expected.setDateTime(new Date());
    JsonObject lastCheckInData = pojo2JsonObject(expected);
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
    getClient().post(itemsStorageUrl(""), itemToCreate, TENANT_ID,
      ResponseHandler.text(createCompleted));

    Response postResponse = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);
    assertThat(postResponse.getStatusCode(), is(400));
    assertThat(postResponse.getBody(),
      containsString("problem: Wrong status name")
    );
  }

  @Test
  public void cannotRemoveItemStatus() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
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
    getClient().put(itemsStorageUrl("/" + id), replacement,
      TENANT_ID, ResponseHandler.jsonErrors(updateCompleted));

    JsonErrorResponse updateResponse = updateCompleted.get(TIMEOUT, TimeUnit.SECONDS);
    assertThat(updateResponse.getStatusCode(), is(422));
    assertThat(updateResponse.getErrors().size(), is(1));

    JsonObject error = updateResponse.getErrors().getFirst();
    assertThat(error.getString("message"), anyOf(is("may not be null"), is("must not be null")));
    assertThat(error.getJsonArray("parameters").getJsonObject(0).getString("key"),
      is("status"));
  }

  @Test
  public void cannotRemoveItemStatusName() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
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
    getClient().put(itemsStorageUrl("/" + id), replacement,
      TENANT_ID, ResponseHandler.jsonErrors(updateCompleted));

    JsonErrorResponse updateResponse = updateCompleted.get(TIMEOUT, TimeUnit.SECONDS);
    assertThat(updateResponse.getStatusCode(), is(422));
    assertThat(updateResponse.getErrors().size(), is(1));

    JsonObject error = updateResponse.getErrors().getFirst();
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
  public void canCreateItemWithAllAllowedStatuses(String status) {
    final UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

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
  public void canFilterByFullCallNumber() {
    final UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

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
    assertThat(foundItems.getFirst().getId(), is(itemWithWholeCallNumber.getId()));
  }

  @Test
  public void canFilterByCallNumberAndSuffix() {
    final UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

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
  public void cannotCreateItemWithNonExistentHoldingsRecordId() {
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
  public void cannotBatchCreateItemsWithNonExistentHoldingsRecordId() {
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
  public void canSearchByDiscoverySuppressProperty() {
    final UUID holdingsId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

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
    assertThat(suppressedItems.getFirst().getId(), is(suppressedItem.getId()));

    assertThat(notSuppressedItems.size(), is(2));
    assertThat(notSuppressedItems.stream()
        .map(IndividualResource::getId)
        .toList(),
      containsInAnyOrder(notSuppressedItem.getId(), notSuppressedItemDefault.getId()));
  }

  @Test
  public void shouldFindItemByCallNumberWhenThereIsSuffix() {
    final UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

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
  public void explicitRightTruncationCanBeApplied() {
    final UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

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
  public void canSearchByPurchaseOrderLineIdentifierProperty() {
    final UUID holdingsId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    final IndividualResource firstItem = itemsClient.create(
      smallAngryPlanet(holdingsId).put("purchaseOrderLineIdentifier", "poli-1"));

    itemsClient.create(nod(holdingsId)
      .put("purchaseOrderLineIdentifier", "poli-2"));

    final List<IndividualResource> poli1Items = itemsClient
      .getMany("purchaseOrderLineIdentifier==\"poli-1\"");

    assertThat(poli1Items.size(), is(1));
    assertThat(poli1Items.getFirst().getId(), is(firstItem.getId()));
  }

  @Test
  public void cannotCreateItemWithNonExistentStatisticalCodeId() {
    final UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    final UUID nonExistentStatisticalCodeId = UUID.randomUUID();
    final String status = "Available";

    final JsonObject itemToCreate = new ItemRequestBuilder()
      .forHolding(holdingsRecordId)
      .withMaterialType(journalMaterialTypeId)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withStatus(status)
      .withStatisticalCodeIds(List.of(nonExistentStatisticalCodeId))
      .create();

    final Response createdItem = itemsClient.attemptToCreate(itemToCreate);

    String expectedMessage = String.format(
      "Cannot set item.statistical_code_id = %s because it does not exist in statistical_code.id.",
      nonExistentStatisticalCodeId
    );

    assertThat(createdItem, hasValidationError(
      expectedMessage,
      "item.statistical_code_id",
      nonExistentStatisticalCodeId.toString()
    ));
  }

  @Test
  public void canCreateItemWithMultipleStatisticalCodeIds() {
    final var firstStatisticalCode = statisticalCodeFixture
      .createSerialManagementCode(new StatisticalCodeBuilder()
        .withCode("stcone")
        .withName("Statistical code 1"));

    final var secondStatisticalCode = statisticalCodeFixture
      .attemptCreateSerialManagementCode(new StatisticalCodeBuilder()
        .withCode("stctwo")
        .withName("Statistical code 2"));

    final UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

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
  public void cannotCreateItemWithAtLeastOneNonExistentStatisticalCodeId() {
    final var statisticalCode = statisticalCodeFixture
      .createSerialManagementCode(new StatisticalCodeBuilder()
        .withCode("stcone")
        .withName("Statistical code 1"));

    final UUID nonExistentStatisticalCodeId = UUID.randomUUID();

    final UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

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

    final Response createdItem = itemsClient.attemptToCreate(itemToCreate);

    String expectedMessage = String.format(
      "Cannot set item.statistical_code_id = %s because it does not exist in statistical_code.id.",
      nonExistentStatisticalCodeId
    );

    assertThat(createdItem, hasValidationError(
      expectedMessage,
      "item.statistical_code_id",
      nonExistentStatisticalCodeId.toString()
    ));
  }

  @Test
  public void canUpdateItemWithStatisticalCodeId() throws Exception {
    final var statisticalCode = statisticalCodeFixture
      .createSerialManagementCode(new StatisticalCodeBuilder()
        .withCode("stcone")
        .withName("Statistical code 1"));

    final UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

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

    item.put("statisticalCodeIds", List.of(statisticalCodeId));

    CompletableFuture<Response> completed = new CompletableFuture<>();
    getClient().put(itemsStorageUrl("/" + itemId), item, TENANT_ID,
      ResponseHandler.empty(completed));
    Response response = completed.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  @Test
  public void cannotUpdateItemWithNonExistentStatisticalCodeId() throws Exception {
    final UUID nonExistentStatisticalCodeId = UUID.randomUUID();
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
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

    item.put("statisticalCodeIds", Collections.singletonList(nonExistentStatisticalCodeId.toString()));

    CompletableFuture<Response> completed = new CompletableFuture<>();
    getClient().put(itemsStorageUrl("/" + itemId), item, TENANT_ID,
      ResponseHandler.text(completed));
    Response response = completed.get(TIMEOUT, TimeUnit.SECONDS);

    String expectedMessage = String.format(
      "Cannot set item statistical_code_id = %s because it does not exist in statistical_code.id.",
      nonExistentStatisticalCodeId
    );

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    assertThat(response.getBody(), equalTo(expectedMessage));
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

  static JsonObject removeBarcode(JsonObject item) {
    item.remove("barcode");
    return item;
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

    if (id != null) {
      itemToCreate.put("id", id.toString());
    }

    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("barcode", barcode);
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("materialTypeId", materialType);
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", ANNEX_LIBRARY_LOCATION_ID.toString());
    itemToCreate.put("_version", 1);

    return itemToCreate;
  }

  private static JsonObject smallAngryPlanet(UUID itemId, UUID holdingsRecordId) {
    return createItemRequest(itemId, holdingsRecordId, "036000291452");
  }

  private static JsonObject smallAngryPlanet(UUID holdingsRecordId) {
    return smallAngryPlanet(UUID.randomUUID(), holdingsRecordId);
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

  private JsonArray threeItems() {
    UUID holdingsRecordId = createInstanceAndHoldingWithBuilder(MAIN_LIBRARY_LOCATION_ID,
      holdingRequestBuilder -> holdingRequestBuilder.withCallNumber("hrCallNumber"));

    return new JsonArray()
      .add(nod(holdingsRecordId).put("barcode", UUID.randomUUID().toString()))
      .add(smallAngryPlanet(holdingsRecordId).put("barcode", UUID.randomUUID().toString()))
      .add(interestingTimes(holdingsRecordId).put("barcode", UUID.randomUUID().toString()));
  }

  private Response postSynchronousBatch(JsonArray itemsArray) {
    return postSynchronousBatch(itemsStorageSyncUrl(""), itemsArray);
  }

  private Response postSynchronousBatch(String subPath, JsonArray itemsArray) {
    return postSynchronousBatch(itemsStorageSyncUrl(subPath), itemsArray);
  }

  private Response postSynchronousBatch(URL url, JsonArray itemsArray) {
    JsonObject itemsCollection = new JsonObject().put("items", itemsArray);
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    getClient().post(url, itemsCollection, TENANT_ID, ResponseHandler.any(createCompleted));
    try {
      return createCompleted.get(10, SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private Response postSynchronousBatchUnsafe(JsonArray itemsArray) {
    return postSynchronousBatch(itemsStorageSyncUnsafeUrl(""), itemsArray);
  }

  private Items findItems(String searchQuery) throws Exception {
    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

    getClient().get(itemsStorageUrl("?query=") + urlEncode(searchQuery),
      TENANT_ID, ResponseHandler.json(searchCompleted));

    Response response = searchCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(200));

    return response.getJson().mapTo(Items.class);
  }

  private Response getById(String id) {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    getClient().get(itemsStorageUrl("/" + id), TENANT_ID, json(getCompleted));
    try {
      return getCompleted.get(10, SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private Response getById(UUID id) {
    return getById(id.toString());
  }

  private Response update(JsonObject item) {
    CompletableFuture<Response> completed = new CompletableFuture<>();
    getClient().put(itemsStorageUrl("/" + item.getString("id")), item, TENANT_ID, empty(completed));
    try {
      return completed.get(10, SECONDS);
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

  private void assertNotExists(JsonObject item) {
    assertGetNotFound(itemsStorageUrl("/" + item.getString("id")));
  }

  private void assertHridRange(Response response, String minHrid, String maxHrid) {
    assertThat(response.getJson().getString("hrid"),
      is(both(greaterThanOrEqualTo(minHrid)).and(lessThanOrEqualTo(maxHrid))));
  }

  /**
   * Assert that the cql query returns items with the expected barcodes in any order.
   */
  private void assertCqlFindsBarcodes(String cql, String... expectedBarcodes) throws Exception {
    Items items = findItems(cql);
    String[] barcodes = items.getItems().stream().map(Item::getBarcode).toArray(String[]::new);
    assertThat(cql, barcodes, arrayContainingInAnyOrder(expectedBarcodes));
    assertThat(cql, items.getTotalRecords(), is(barcodes.length));
  }

  private JsonObject addTags(UUID holdingsRecordId) {
    return smallAngryPlanet(holdingsRecordId)
      .put("tags", new JsonObject()
        .put("tagList", new JsonArray()
          .add(ItemStorageTest.TAG_VALUE)));
  }

  private void setItemSequence(long sequenceNumber) {
    final PostgresClient postgresClient =
      PostgresClient.getInstance(getVertx(), TENANT_ID);
    final CompletableFuture<Void> sequenceSet = new CompletableFuture<>();

    getVertx().runOnContext(v ->
      postgresClient.selectSingle("select setval('hrid_items_seq'," + sequenceNumber + ",FALSE)",
        r -> {
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
      .toList();
  }

  @SneakyThrows
  private Boolean isBefore(String dateTime, String secondDateTime) {
    ZonedDateTime firstTime = ZonedDateTime.parse(dateTime);
    ZonedDateTime secondTime = ZonedDateTime.parse(secondDateTime);

    return secondTime.isAfter(firstTime);
  }

  private List<UUID> searchByCallNumberEyeReadable(String searchTerm) {
    return itemsClient
      .getMany("fullCallNumber==\"%1$s\" OR callNumberAndSuffix==\"%1$s\" OR "
               + "effectiveCallNumberComponents.callNumber==\"%1$s\"", searchTerm)
      .stream()
      .map(IndividualResource::getId)
      .toList();
  }
}
