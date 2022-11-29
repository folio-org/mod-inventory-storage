package org.folio.rest.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.rest.api.ItemEffectiveCallNumberComponentsTest.ITEM_LEVEL_CALL_NUMBER_TYPE;
import static org.folio.rest.support.HttpResponseMatchers.errorMessageContains;
import static org.folio.rest.support.HttpResponseMatchers.errorParametersValueIs;
import static org.folio.rest.support.HttpResponseMatchers.statusCodeIs;
import static org.folio.rest.support.JsonObjectMatchers.hasSoleMessageContaining;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.ResponseHandler.text;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageSyncUnsafeUrl;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageSyncUrl;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getHoldingsEvents;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getLastHoldingEvent;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertCreateEventForHolding;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertNoUpdateEventForHolding;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertRemoveAllEventForHolding;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertRemoveEventForHolding;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertUpdateEvent;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertUpdateEventForHolding;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertUpdateEventForItem;
import static org.folio.rest.support.matchers.PostgresErrorMessageMatchers.isMaximumSequenceValueError;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.HttpStatus;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.AdditionalHttpStatusCodes;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonArrayHelper;
import org.folio.rest.support.JsonErrorResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.folio.rest.support.db.OptimisticLocking;
import org.folio.rest.support.matchers.DomainEventAssertions;
import org.folio.rest.tools.utils.OptimisticLockingUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class HoldingsStorageTest extends TestBaseWithInventoryUtil {
  private static final Logger log = LogManager.getLogger();
  private static final String TAG_VALUE = "test-tag";
  public static final String NEW_TEST_TAG = "new test tag";

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

    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(Map.of());

    removeAllEvents();
  }

  @SneakyThrows
  @After
  public void afterEach() {
    setHoldingsSequence(1);

    StorageTestSuite.checkForMismatchedIDs("holdings_record");
  }

  @Test
  public void canCreateAHolding() {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    setHoldingsSequence(1);

    UUID holdingId = UUID.randomUUID();
    String adminNote = "An admin note";

    JsonObject holdingToCreate = new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE))).create();

    holdingToCreate.put("administrativeNotes", new JsonArray().add(adminNote));

    JsonObject holding = holdingsClient.create(holdingToCreate).getJson();

    assertThat(holding.getString("id"), is(holdingId.toString()));
    assertThat(holding.getString("instanceId"), is(instanceId.toString()));
    assertThat(holding.getString("permanentLocationId"), is(mainLibraryLocationId.toString()));
    assertThat(holding.getString("hrid"), is("ho00000000001"));
    assertThat(holding.getString("effectiveLocationId"), is(mainLibraryLocationId.toString()));
    assertThat(holding.getJsonArray("administrativeNotes").contains(adminNote), is(true));

    Response getResponse = holdingsClient.getById(holdingId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject holdingFromGet = getResponse.getJson();

    assertThat(holdingFromGet.getString("id"), is(holdingId.toString()));
    assertThat(holdingFromGet.getString("instanceId"), is(instanceId.toString()));
    assertThat(holdingFromGet.getString("permanentLocationId"), is(mainLibraryLocationId.toString()));
    assertThat(holdingFromGet.getString("hrid"), is("ho00000000001"));
    assertThat(holdingFromGet.getJsonArray("administrativeNotes").contains(adminNote), is(true));

    List<String> tags = getTags(holdingFromGet);

    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
    assertCreateEventForHolding(holding);
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

    List<String> tags = getTags(holdingFromGet);

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

    getClient().post(holdingsStorageUrl(""), request, TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response response = createCompleted.get(10, TimeUnit.SECONDS);

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

    setHoldingsSequence(1);

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
    assertThat(holdingFromGet.getString("hrid"), is("ho00000000001"));

    List<String> tags = getTags(holdingFromGet);

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

    getClient().post(holdingsStorageUrl(""), request, TENANT_ID,
      ResponseHandler.jsonErrors(createCompleted));

    JsonErrorResponse response = createCompleted.get(10, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
    assertThat(response.getErrors(), hasSoleMessageContaining("Unrecognized field"));
  }

  @Test
  public void canReplaceAHoldingAtSpecificLocation() {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));
    String adminNote = "an admin note";
    setHoldingsSequence(1);

    IndividualResource holdingResource = holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))
      .withPermanentLocation(mainLibraryLocationId));

    // Clear Kafka events after create to reduce chances of
    // CREATE messages appearing after UPDATE later on.
    // This should be removed once the messaging problem is
    // properly resolved.
    removeAllEvents();

    UUID holdingId = holdingResource.getId();

    JsonObject replacement = holdingResource.copyJson()
      .put("permanentLocationId", annexLibraryLocationId.toString())
      .put("tags", new JsonObject().put("tagList", new JsonArray().add(NEW_TEST_TAG)))
      .put("administrativeNotes", new JsonArray().add(adminNote));

    holdingsClient.replace(holdingId, replacement);

    Response getResponse = holdingsClient.getById(holdingId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject holdingFromGet = getResponse.getJson();

    assertThat(holdingFromGet.getString("id"), is(holdingId.toString()));
    assertThat(holdingFromGet.getString("instanceId"), is(instanceId.toString()));
    assertThat(holdingFromGet.getString("permanentLocationId"), is(annexLibraryLocationId.toString()));
    assertThat(holdingFromGet.getString("hrid"), is("ho00000000001"));
    assertThat(holdingFromGet.getJsonArray("administrativeNotes").contains(adminNote), is(true));

    List<String> tags = getTags(holdingFromGet);

    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(NEW_TEST_TAG));
    assertUpdateEventForHolding(holdingResource.getJson(), holdingFromGet);
  }

  @Test
  public void canMoveHoldingsToNewInstance() throws ExecutionException, InterruptedException, TimeoutException {
    UUID instanceId = UUID.randomUUID();
    UUID newInstanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));
    instancesClient.create(smallAngryPlanet(newInstanceId));
    setHoldingsSequence(1);

    IndividualResource holdingResource = holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId));

    UUID holdingId = holdingResource.getId();

    JsonObject item = create(itemsStorageUrl(""), new ItemRequestBuilder()
      .forHolding(holdingId)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withMaterialType(bookMaterialTypeId)
      .create())
      .getJson();

    JsonObject replacement = holdingResource.copyJson()
      .put("instanceId", newInstanceId.toString());

    holdingsClient.replace(holdingId, replacement);

    Response getResponse = holdingsClient.getById(holdingId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject holdingFromGet = getResponse.getJson();

    assertThat(holdingFromGet.getString("instanceId"), is(newInstanceId.toString()));
    assertUpdateEventForHolding(holdingResource.getJson(), holdingFromGet);

    JsonObject newItem = item.copy()
      .put("_version", 2);
    assertUpdateEventForItem(item, newItem, instanceId.toString());
  }

  @Test
  public void canDeleteAHolding() {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    IndividualResource holdingResource = holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId));

    UUID holdingId = holdingResource.getId();

    holdingsClient.delete(holdingId);

    Response getResponse = holdingsClient.getById(holdingId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
    assertRemoveEventForHolding(holdingResource.getJson());
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

    getClient().get(holdingsStorageUrl(""), TENANT_ID,
      ResponseHandler.json(getCompleted));

    Response response = getCompleted.get(10, TimeUnit.SECONDS);

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

    getClient().get(holdingsStorageUrl("?limit=-3"), TENANT_ID,
      ResponseHandler.text(getCompleted));

    Response response = getCompleted.get(10, TimeUnit.SECONDS);

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

    getClient().get(holdingsStorageUrl("?offset=-3"), TENANT_ID,
      ResponseHandler.text(getCompleted));

    Response response = getCompleted.get(10, TimeUnit.SECONDS);

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

    getClient().delete(holdingsStorageUrl("/" + holdingId + "?lang=eng"),
      TENANT_ID, ResponseHandler.text(getCompleted));

    Response response = getCompleted.get(10, TimeUnit.SECONDS);

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

    holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(firstInstanceId)
      .withPermanentLocation(mainLibraryLocationId)).getId();

    holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(secondInstanceId)
      .withPermanentLocation(annexLibraryLocationId)).getId();

    holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(thirdInstanceId)
      .withPermanentLocation(mainLibraryLocationId)).getId();

    holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(secondInstanceId)
      .withPermanentLocation(mainLibraryLocationId)).getId();

    holdingsClient.create(new HoldingRequestBuilder()
      .forInstance(firstInstanceId)
      .withPermanentLocation(annexLibraryLocationId)).getId();

    CompletableFuture<Response> firstPageCompleted = new CompletableFuture<>();
    CompletableFuture<Response> secondPageCompleted = new CompletableFuture<>();

    getClient().get(holdingsStorageUrl("") + "?limit=3", TENANT_ID,
      ResponseHandler.json(firstPageCompleted));

    getClient().get(holdingsStorageUrl("") + "?limit=3&offset=3", TENANT_ID,
      ResponseHandler.json(secondPageCompleted));

    Response firstPageResponse = firstPageCompleted.get(10, TimeUnit.SECONDS);
    Response secondPageResponse = secondPageCompleted.get(10, TimeUnit.SECONDS);

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
  public void canDeleteAllHoldings() {
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

    assertRemoveAllEventForHolding();
  }

  @SneakyThrows
  @Test
  public void canDeleteHoldingsByCql() {
    UUID instanceId1 = UUID.randomUUID();
    UUID instanceId2 = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId1));
    instancesClient.create(nod(instanceId2));

    var h1 = holdingsClient.create(new HoldingRequestBuilder()
        .forInstance(instanceId1)
        .withPermanentLocation(mainLibraryLocationId)
        .withHrid("1234")).getJson();
    var h2 = holdingsClient.create(new HoldingRequestBuilder()
        .forInstance(instanceId1)
        .withPermanentLocation(mainLibraryLocationId)
        .withHrid("21234")).getJson();
    var h3 = holdingsClient.create(new HoldingRequestBuilder()
        .forInstance(instanceId2)
        .withPermanentLocation(mainLibraryLocationId)
        .withHrid("12")).getJson();
    var h4 = holdingsClient.create(new HoldingRequestBuilder()
        .forInstance(instanceId2)
        .withPermanentLocation(mainLibraryLocationId)
        .withHrid("3123")).getJson();
    var h5 = holdingsClient.create(new HoldingRequestBuilder()
        .forInstance(instanceId2)
        .withPermanentLocation(mainLibraryLocationId)
        .withHrid("123")).getJson();

    var response = getClient().delete(holdingsStorageUrl("?query=hrid==12*"), TENANT_ID).get(10, SECONDS);

    assertThat(response.getStatusCode(), is(204));
    assertExists(h2);
    assertExists(h4);
    assertNotExists(h1);
    assertNotExists(h3);
    assertNotExists(h5);
    assertRemoveEventForHolding(h1);
    assertRemoveEventForHolding(h3);
    assertRemoveEventForHolding(h5);
  }

  @SneakyThrows
  @Test
  public void cannotDeleteHoldingsWithEmptyCql() {

    var response = getClient().delete(holdingsStorageUrl("?query="), TENANT_ID).get(10, SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), containsString("empty"));
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

    getClient().post(holdingsStorageUrl(""), request, null, ResponseHandler.any(postCompleted));

    Response response = postCompleted.get(10, TimeUnit.SECONDS);

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

    getClient().get(getHoldingUrl, null, ResponseHandler.any(getCompleted));

    Response response = getCompleted.get(10, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @Test
  public void tenantIsRequiredForGettingAllHoldings() throws InterruptedException,
    ExecutionException, TimeoutException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(holdingsStorageUrl(""), null, ResponseHandler.any(getCompleted));

    Response response = getCompleted.get(10, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @Test
  public void optimisticLockingVersion() {
    UUID holdingId = createInstanceAndHolding(mainLibraryLocationId);
    JsonObject holding = getById(holdingId.toString()).getJson();
    holding.put(PERMANENT_LOCATION_ID_KEY, annexLibraryLocationId);
    // updating with current _version 1 succeeds and increments _version to 2
    assertThat(update(holding).getStatusCode(), is(204));
    holding.put(PERMANENT_LOCATION_ID_KEY, secondFloorLocationId);
    // updating with outdated _version 1 fails, current _version is 2
    int expected = OptimisticLocking.hasFailOnConflict("holdings_record") ? 409 : 204;
    assertThat(update(holding).getStatusCode(), is(expected));
    // updating with _version -1 should fail, single holding PUT never allows to suppress optimistic locking
    holding.put("_version", -1);
    assertThat(update(holding).getStatusCode(), is(409));
    // this allow should not apply to single holding PUT, only to batch unsafe
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
        Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));
    holding.put("_version", -1);
    assertThat(update(holding).getStatusCode(), is(409));
  }

  @Test
  public void updatingPermanentLocationChangesEffectiveLocationWhenNoTemporaryLocationSet()
    throws InterruptedException, ExecutionException, TimeoutException {
    UUID instanceId = UUID.randomUUID();
    UUID holdingId = UUID.randomUUID();
    URL holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));

    instancesClient.create(smallAngryPlanet(instanceId));
    setHoldingsSequence(1);

    JsonObject holding = holdingsClient.create(new HoldingRequestBuilder()
    .withId(holdingId)
    .forInstance(instanceId)
    .withPermanentLocation(mainLibraryLocationId)
    .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))).getJson();

    assertThat(holding.getString("effectiveLocationId"), is(mainLibraryLocationId.toString()));

    holding.put("permanentLocationId", annexLibraryLocationId.toString());
    update(holdingsUrl, holding);
    Response updatedHolding = holdingsClient.getById(holdingId);
    assertThat(updatedHolding.getJson().getString("effectiveLocationId"), is(annexLibraryLocationId.toString()));
  }

  @Test
  public void updatingPermanentLocationDoesNotChangeEffectiveLocationWhenTemporaryLocationSet()
    throws InterruptedException, ExecutionException, TimeoutException {
    UUID instanceId = UUID.randomUUID();
    UUID holdingId = UUID.randomUUID();
    URL holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));

    instancesClient.create(smallAngryPlanet(instanceId));
    setHoldingsSequence(1);

    JsonObject holding = holdingsClient.create(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withTemporaryLocation(annexLibraryLocationId)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))).getJson();

    assertThat(holding.getString("effectiveLocationId"), is(annexLibraryLocationId.toString()));

    holding.put("permanentLocationId", secondFloorLocationId.toString());
    update(holdingsUrl, holding);
    Response updatedHolding = holdingsClient.getById(holdingId);
    assertThat(updatedHolding.getJson().getString("effectiveLocationId"), is(annexLibraryLocationId.toString()));
  }

  @Test
  public void updatingOrRemovingTemporaryLocationChangesEffectiveLocation()
    throws InterruptedException, ExecutionException, TimeoutException {
    UUID instanceId = UUID.randomUUID();
    UUID holdingId = UUID.randomUUID();
    URL holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));

    instancesClient.create(smallAngryPlanet(instanceId));
    setHoldingsSequence(1);

    JsonObject holding = holdingsClient.create(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))).getJson();

    assertThat(holding.getString("effectiveLocationId"), is(mainLibraryLocationId.toString()));

    holding.put("temporaryLocationId", annexLibraryLocationId.toString());
    update(holdingsUrl, holding);
    holding = holdingsClient.getById(holdingId).getJson();
    assertThat(holding.getString("effectiveLocationId"), is(annexLibraryLocationId.toString()));

    holding.put("temporaryLocationId", secondFloorLocationId.toString());
    update(holdingsUrl, holding);
    holding = holdingsClient.getById(holdingId).getJson();
    assertThat(holding.getString("effectiveLocationId"), is(secondFloorLocationId.toString()));

    holding.remove("temporaryLocationId");
    update(holdingsUrl, holding);
    holding = holdingsClient.getById(holdingId).getJson();
    assertThat(holding.getString("effectiveLocationId"), is(mainLibraryLocationId.toString()));
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
  public void updatingHoldingsUpdatesItemShelvingOrder(
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
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();

    JsonObject holding = holdingsClient.create(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withCallNumber("testCallNumber")
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))).getJson();

    JsonObject itemToCreate = new JsonObject();

    itemToCreate.put("holdingsRecordId", holdingId.toString());
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", annexLibraryLocationId.toString());
    itemToCreate.put("materialTypeId", bookMaterialTypeID);
    itemToCreate.put("itemLevelCallNumberSuffix", suffix);
    itemToCreate.put("itemLevelCallNumberPrefix", prefix);
    itemToCreate.put("itemLevelCallNumberTypeId", ITEM_LEVEL_CALL_NUMBER_TYPE);
    itemToCreate.put("volume",volume);
    itemToCreate.put("enumeration",enumeration);
    itemToCreate.put("chronology",chronology);
    itemToCreate.put("copyNumber",copy);

    Response postFirstItemResponse = create(itemsStorageUrl(""), itemToCreate);
    Response postSecondItemResponse = create(itemsStorageUrl(""), itemToCreate);

    assertThat(postFirstItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(postSecondItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject firstItem = postFirstItemResponse.getJson();
    JsonObject secondItem = postSecondItemResponse.getJson();

    String firstItemId = firstItem.getString("id");
    String secondItemId = secondItem.getString("id");

    assertThat(firstItemId, is(notNullValue()));
    assertThat(secondItemId, is(notNullValue()));

    URL getFirstItemUrl = itemsStorageUrl(String.format("/%s", firstItemId));
    URL getSecondItemUrl = itemsStorageUrl(String.format("/%s", secondItemId));

    Response getFirstItemResponse = get(getFirstItemUrl);
    Response getSecondItemResponse = get(getSecondItemUrl);

    JsonObject firstItemFromGet = getFirstItemResponse.getJson();
    JsonObject secondItemFromGet = getSecondItemResponse.getJson();

    assertThat(firstItemFromGet.getString("id"), is(firstItemId));
    assertThat(firstItemFromGet.getString("holdingsRecordId"), is(holdingId.toString()));
    assertThat(
      firstItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("callNumber"),
      is("testCallNumber"));
    assertThat(secondItemFromGet.getString("id"), is(secondItemId));
    assertThat(secondItemFromGet.getString("holdingsRecordId"), is(holdingId.toString()));
    assertThat(
      secondItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("callNumber"),
      is("testCallNumber"));

    URL holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));

    holding.put("callNumber", callNumber);

    Response putResponse = update(holdingsUrl, holding);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    assertThat(holding.getString("callNumber"), is(callNumber));

    Response getFirstUpdatedItemResponse = get(getFirstItemUrl);
    Response getSecondUpdatedItemResponse = get(getSecondItemUrl);

    JsonObject firstUpdatedItemFromGet = getFirstUpdatedItemResponse.getJson();
    JsonObject secondUpdatedItemFromGet = getSecondUpdatedItemResponse.getJson();

    assertThat(getFirstUpdatedItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(firstUpdatedItemFromGet.getString("id"), is(firstItemId));
    assertThat(
      firstUpdatedItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("callNumber"),
      is(callNumber));
    assertThat(getSecondUpdatedItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(secondUpdatedItemFromGet.getString("id"), is(secondItemId));
    assertThat(
      secondUpdatedItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("callNumber"),
      is(callNumber));
  }

  @Test
  public void updatingHoldingsUpdatesItemEffectiveCallNumber()
      throws MalformedURLException, InterruptedException,
      ExecutionException, TimeoutException {

    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();

    JsonObject holding = holdingsClient.create(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withCallNumber("testCallNumber")
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))).getJson();

    JsonObject itemToCreate = new JsonObject();

    itemToCreate.put("holdingsRecordId", holdingId.toString());
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", annexLibraryLocationId.toString());
    itemToCreate.put("materialTypeId", bookMaterialTypeID);

    Response postFirstItemResponse = create(itemsStorageUrl(""), itemToCreate);
    Response postSecondItemResponse = create(itemsStorageUrl(""), itemToCreate);

    assertThat(postFirstItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(postSecondItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject firstItem = postFirstItemResponse.getJson();
    JsonObject secondItem = postSecondItemResponse.getJson();

    String firstItemId = firstItem.getString("id");
    String secondItemId = secondItem.getString("id");

    assertThat(firstItemId, is(notNullValue()));
    assertThat(secondItemId, is(notNullValue()));

    URL getFirstItemUrl = itemsStorageUrl(String.format("/%s", firstItemId));
    URL getSecondItemUrl = itemsStorageUrl(String.format("/%s", secondItemId));

    Response getFirstItemResponse = get(getFirstItemUrl);
    Response getSecondItemResponse = get(getSecondItemUrl);

    JsonObject firstItemFromGet = getFirstItemResponse.getJson();
    JsonObject secondItemFromGet = getSecondItemResponse.getJson();

    assertThat(firstItemFromGet.getString("id"), is(firstItemId));
    assertThat(firstItemFromGet.getString("holdingsRecordId"), is(holdingId.toString()));
    assertThat(
      firstItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("callNumber"),
      is("testCallNumber"));
    assertThat(secondItemFromGet.getString("id"), is(secondItemId));
    assertThat(secondItemFromGet.getString("holdingsRecordId"), is(holdingId.toString()));
    assertThat(
      secondItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("callNumber"),
      is("testCallNumber"));

    URL holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));

    holding.put("callNumber", "updatedCallNumber");

    Response putResponse = update(holdingsUrl, holding);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    assertThat(holding.getString("callNumber"), is("updatedCallNumber"));

    Response getFirstUpdatedItemResponse = get(getFirstItemUrl);
    Response getSecondUpdatedItemResponse = get(getSecondItemUrl);

    JsonObject firstUpdatedItemFromGet = getFirstUpdatedItemResponse.getJson();
    JsonObject secondUpdatedItemFromGet = getSecondUpdatedItemResponse.getJson();

    assertThat(getFirstUpdatedItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(firstUpdatedItemFromGet.getString("id"), is(firstItemId));
    assertThat(
      firstUpdatedItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("callNumber"),
      is("updatedCallNumber"));
    assertThat(getSecondUpdatedItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(secondUpdatedItemFromGet.getString("id"), is(secondItemId));
    assertThat(
      secondUpdatedItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("callNumber"),
      is("updatedCallNumber"));
  }

  @Test
  public void removingHoldingsCallNumberUpdatesItemEffectiveCallNumber()
      throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();

    JsonObject holding = holdingsClient.create(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withCallNumber("testCallNumber")
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))).getJson();

    JsonObject itemToCreate = new JsonObject();

    itemToCreate.put("holdingsRecordId", holdingId.toString());
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", annexLibraryLocationId.toString());
    itemToCreate.put("materialTypeId", bookMaterialTypeID);

    Response postFirstItemResponse = create(itemsStorageUrl(""), itemToCreate);

    assertThat(postFirstItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject firstItemFromPost = postFirstItemResponse.getJson();

    String firstItemId = firstItemFromPost.getString("id");

    assertThat(firstItemId, is(notNullValue()));

    URL getFirstItemUrl = itemsStorageUrl(String.format("/%s", firstItemId));

    Response getFirstItemResponse = get(getFirstItemUrl);

    JsonObject firstItemFromGet = getFirstItemResponse.getJson();

    assertThat(firstItemFromGet.getString("id"), is(firstItemId));
    assertThat(firstItemFromGet.getString("holdingsRecordId"), is(holdingId.toString()));
    assertThat(
      firstItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("callNumber"),
      is("testCallNumber"));

    URL holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));

    holding.remove("callNumber");

    Response putResponse = update(holdingsUrl, holding);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    assertThat(holding.containsKey("callNumber"), is(false));

    Response getFirstUpdatedItemResponse = get(getFirstItemUrl);

    JsonObject firstUpdatedItemFromGet = getFirstUpdatedItemResponse.getJson();

    assertThat(getFirstUpdatedItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(firstUpdatedItemFromGet.getString("id"), is(firstItemId));
    assertThat(
      firstUpdatedItemFromGet.getJsonObject("effectiveCallNumberComponents").containsKey("callNumber"),
      is(false));
  }

  @Test
  public void holdingsCallNumberDoesNotSupersedeItemLevelCallNumber()
      throws MalformedURLException, InterruptedException, TimeoutException, ExecutionException {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();

    JsonObject holding = holdingsClient.create(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withCallNumber("holdingsCallNumber")
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))).getJson();

    JsonObject itemToCreate = new JsonObject();

    itemToCreate.put("holdingsRecordId", holdingId.toString());
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", annexLibraryLocationId.toString());
    itemToCreate.put("materialTypeId", bookMaterialTypeID);
    itemToCreate.put("itemLevelCallNumber", "itemLevelCallNumber");

    Response postItemResponse = create(itemsStorageUrl(""), itemToCreate);

    assertThat(postItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject itemFromPost = postItemResponse.getJson();

    String itemId = itemFromPost.getString("id");

    assertThat(itemId, is(notNullValue()));

    URL getItemUrl = itemsStorageUrl(String.format("/%s", itemId));

    Response getItemResponse = get(getItemUrl);

    JsonObject itemFromGet = getItemResponse.getJson();

    assertThat(itemFromGet.getString("id"), is(itemId));
    assertThat(itemFromGet.getString("holdingsRecordId"), is(holdingId.toString()));
    assertThat(
      itemFromGet.getJsonObject("effectiveCallNumberComponents").getString("callNumber"),
      is("itemLevelCallNumber"));

    URL holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));

    holding.put("callNumber", "updatedHoldingCallNumber");

    Response putResponse = update(holdingsUrl, holding);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getUpdatedItemResponse = get(getItemUrl);

    JsonObject updatedItemFromGet = getUpdatedItemResponse.getJson();

    assertThat(getUpdatedItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(updatedItemFromGet.getString("id"), is(itemId));
    assertThat(
      updatedItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("callNumber"),
      is("itemLevelCallNumber"));
  }

  @Test
  public void updatingHoldingsDoesNotUpdateItemsOnAnotherHoldings()
      throws MalformedURLException, ExecutionException,
      InterruptedException, TimeoutException {

    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID firstHoldings = UUID.randomUUID();
    UUID secondHoldings = UUID.randomUUID();

    JsonObject firstHolding = holdingsClient.create(new HoldingRequestBuilder()
      .withId(firstHoldings)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withCallNumber("firstTestCallNumber")
      .withCallNumberPrefix("firstTestCallNumberPrefix")
      .withCallNumberSuffix("firstTestCallNumberSuffix")
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))).getJson();

    holdingsClient.create(new HoldingRequestBuilder()
      .withId(secondHoldings)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withCallNumber("secondTestCallNumber")
      .withCallNumberPrefix("secondTestCallNumberPrefix")
      .withCallNumberSuffix("secondTestCallNumberSuffix")
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))).getJson();

    Response firstItemResponse = create(itemsStorageUrl(""), new ItemRequestBuilder()
      .forHolding(firstHoldings)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withMaterialType(bookMaterialTypeId)
      .create());
    Response secondItemResponse = create(itemsStorageUrl(""), new ItemRequestBuilder()
      .forHolding(secondHoldings)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withMaterialType(bookMaterialTypeId)
      .create());

    assertThat(firstItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(secondItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject firstItem = firstItemResponse.getJson();
    JsonObject secondItem = secondItemResponse.getJson();

    assertThat(
      firstItem.getJsonObject("effectiveCallNumberComponents").getString("callNumber"),
      is("firstTestCallNumber"));
    assertThat(
      firstItem.getJsonObject("effectiveCallNumberComponents").getString("prefix"),
      is("firstTestCallNumberPrefix"));
    assertThat(
      firstItem.getJsonObject("effectiveCallNumberComponents").getString("suffix"),
      is("firstTestCallNumberSuffix"));
    assertThat(
      secondItem.getJsonObject("effectiveCallNumberComponents").getString("callNumber"),
      is("secondTestCallNumber"));
    assertThat(
      secondItem.getJsonObject("effectiveCallNumberComponents").getString("prefix"),
      is("secondTestCallNumberPrefix"));
    assertThat(
      secondItem.getJsonObject("effectiveCallNumberComponents").getString("suffix"),
      is("secondTestCallNumberSuffix"));

    URL firstHoldingsUrl = holdingsStorageUrl(String.format("/%s", firstHoldings));

    firstHolding.put("callNumber", "updatedFirstCallNumber");
    firstHolding.put("callNumberPrefix", "updatedFirstCallNumberPrefix");
    firstHolding.put("callNumberSuffix", "updatedFirstCallNumberSuffix");

    Response putResponse = update(firstHoldingsUrl, firstHolding);

    Response updatedFirstHoldingResponse = get(firstHoldingsUrl);

    JsonObject updatedFirstHolding = updatedFirstHoldingResponse.getJson();

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    assertThat(updatedFirstHolding.getString("callNumber"), is("updatedFirstCallNumber"));

    String firstItemId = firstItem.getString("id");
    String secondItemId = secondItem.getString("id");

    URL getFirstItemUrl = itemsStorageUrl(String.format("/%s", firstItemId));
    URL getSecondItemUrl = itemsStorageUrl(String.format("/%s", secondItemId));

    Response getFirstUpdatedItemResponse = get(getFirstItemUrl);
    Response getSecondUpdatedItemResponse = get(getSecondItemUrl);

    JsonObject firstUpdatedItemFromGet = getFirstUpdatedItemResponse.getJson();
    JsonObject secondUpdatedItemFromGet = getSecondUpdatedItemResponse.getJson();

    assertThat(getFirstUpdatedItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(firstUpdatedItemFromGet.getString("id"), is(firstItemId));
    assertThat(
      firstUpdatedItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("callNumber"),
      is("updatedFirstCallNumber"));
    assertThat(
      firstUpdatedItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("prefix"),
      is("updatedFirstCallNumberPrefix"));
    assertThat(
      firstUpdatedItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("suffix"),
      is("updatedFirstCallNumberSuffix"));
    assertThat(getSecondUpdatedItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(secondUpdatedItemFromGet.getString("id"), is(secondItemId));
    assertThat(
      secondUpdatedItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("callNumber"),
      is("secondTestCallNumber"));
    assertThat(
      secondUpdatedItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("prefix"),
      is("secondTestCallNumberPrefix"));
    assertThat(
      secondUpdatedItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("suffix"),
      is("secondTestCallNumberSuffix"));
  }

  @Test
  public void updatingHoldingsUpdatesItemEffectiveCallNumberSuffix()
      throws MalformedURLException, InterruptedException,
      ExecutionException, TimeoutException {

    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();

    JsonObject holding = holdingsClient.create(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withCallNumberSuffix("testCallNumberSuffix")
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))).getJson();

    JsonObject itemToCreate = new JsonObject();

    itemToCreate.put("holdingsRecordId", holdingId.toString());
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", annexLibraryLocationId.toString());
    itemToCreate.put("materialTypeId", bookMaterialTypeID);

    Response postFirstItemResponse = create(itemsStorageUrl(""), itemToCreate);
    Response postSecondItemResponse = create(itemsStorageUrl(""), itemToCreate);

    assertThat(postFirstItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(postSecondItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject firstItem = postFirstItemResponse.getJson();
    JsonObject secondItem = postSecondItemResponse.getJson();

    String firstItemId = firstItem.getString("id");
    String secondItemId = secondItem.getString("id");

    assertThat(firstItemId, is(notNullValue()));
    assertThat(secondItemId, is(notNullValue()));

    URL getFirstItemUrl = itemsStorageUrl(String.format("/%s", firstItemId));
    URL getSecondItemUrl = itemsStorageUrl(String.format("/%s", secondItemId));

    Response getFirstItemResponse = get(getFirstItemUrl);
    Response getSecondItemResponse = get(getSecondItemUrl);

    JsonObject firstItemFromGet = getFirstItemResponse.getJson();
    JsonObject secondItemFromGet = getSecondItemResponse.getJson();

    assertThat(firstItemFromGet.getString("id"), is(firstItemId));
    assertThat(firstItemFromGet.getString("holdingsRecordId"), is(holdingId.toString()));
    assertThat(
      firstItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("suffix"),
      is("testCallNumberSuffix"));
    assertThat(secondItemFromGet.getString("id"), is(secondItemId));
    assertThat(secondItemFromGet.getString("holdingsRecordId"), is(holdingId.toString()));
    assertThat(
      secondItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("suffix"),
      is("testCallNumberSuffix"));

    URL holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));

    holding.put("callNumberSuffix", "updatedCallNumberSuffix");

    Response putResponse = update(holdingsUrl, holding);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    assertThat(holding.getString("callNumberSuffix"), is("updatedCallNumberSuffix"));

    Response getFirstUpdatedItemResponse = get(getFirstItemUrl);
    Response getSecondUpdatedItemResponse = get(getSecondItemUrl);

    JsonObject firstUpdatedItemFromGet = getFirstUpdatedItemResponse.getJson();
    JsonObject secondUpdatedItemFromGet = getSecondUpdatedItemResponse.getJson();

    assertThat(getFirstUpdatedItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(firstUpdatedItemFromGet.getString("id"), is(firstItemId));
    assertThat(
      firstUpdatedItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("suffix"),
      is("updatedCallNumberSuffix"));
    assertThat(getSecondUpdatedItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(secondUpdatedItemFromGet.getString("id"), is(secondItemId));
    assertThat(
      secondUpdatedItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("suffix"),
      is("updatedCallNumberSuffix"));
  }

  @Test
  public void removingHoldingsCallNumberSuffixUpdatesItemEffectiveCallNumberSuffix()
      throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();

    JsonObject holding = holdingsClient.create(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withCallNumberSuffix("testCallNumberSuffix")
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))).getJson();

    JsonObject itemToCreate = new JsonObject();

    itemToCreate.put("holdingsRecordId", holdingId.toString());
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", annexLibraryLocationId.toString());
    itemToCreate.put("materialTypeId", bookMaterialTypeID);

    Response postFirstItemResponse = create(itemsStorageUrl(""), itemToCreate);

    assertThat(postFirstItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject firstItemFromPost = postFirstItemResponse.getJson();

    String firstItemId = firstItemFromPost.getString("id");

    assertThat(firstItemId, is(notNullValue()));

    URL getFirstItemUrl = itemsStorageUrl(String.format("/%s", firstItemId));

    Response getFirstItemResponse = get(getFirstItemUrl);

    JsonObject firstItemFromGet = getFirstItemResponse.getJson();

    assertThat(firstItemFromGet.getString("id"), is(firstItemId));
    assertThat(firstItemFromGet.getString("holdingsRecordId"), is(holdingId.toString()));
    assertThat(
      firstItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("suffix"),
      is("testCallNumberSuffix"));

    URL holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));

    holding.remove("callNumberSuffix");

    Response putResponse = update(holdingsUrl, holding);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    assertThat(holding.containsKey("callNumberSuffix"), is(false));

    Response getFirstUpdatedItemResponse = get(getFirstItemUrl);

    JsonObject firstUpdatedItemFromGet = getFirstUpdatedItemResponse.getJson();

    assertThat(getFirstUpdatedItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(firstUpdatedItemFromGet.getString("id"), is(firstItemId));
    assertThat(
      firstUpdatedItemFromGet.getJsonObject("effectiveCallNumberComponents").containsKey("suffix"),
      is(false));
  }

  @Test
  public void holdingsCallNumberSuffixDoesNotSupersedeItemLevelCallNumberSuffix()
      throws MalformedURLException, InterruptedException, TimeoutException, ExecutionException {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();

    JsonObject holding = holdingsClient.create(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withCallNumberSuffix("holdingsCallNumberSuffix")
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))).getJson();

    JsonObject itemToCreate = new JsonObject();

    itemToCreate.put("holdingsRecordId", holdingId.toString());
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", annexLibraryLocationId.toString());
    itemToCreate.put("materialTypeId", bookMaterialTypeID);
    itemToCreate.put("itemLevelCallNumberSuffix", "itemLevelCallNumberSuffix");

    Response postItemResponse = create(itemsStorageUrl(""), itemToCreate);

    assertThat(postItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject itemFromPost = postItemResponse.getJson();

    String itemId = itemFromPost.getString("id");

    assertThat(itemId, is(notNullValue()));

    URL getItemUrl = itemsStorageUrl(String.format("/%s", itemId));

    Response getItemResponse = get(getItemUrl);

    JsonObject itemFromGet = getItemResponse.getJson();

    assertThat(itemFromGet.getString("id"), is(itemId));
    assertThat(itemFromGet.getString("holdingsRecordId"), is(holdingId.toString()));
    assertThat(
      itemFromGet.getJsonObject("effectiveCallNumberComponents").getString("suffix"),
      is("itemLevelCallNumberSuffix"));

    URL holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));

    holding.put("callNumberSuffix", "updatedHoldingCallNumberSuffix");

    Response putResponse = update(holdingsUrl, holding);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getUpdatedItemResponse = get(getItemUrl);

    JsonObject updatedItemFromGet = getUpdatedItemResponse.getJson();

    assertThat(getUpdatedItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(updatedItemFromGet.getString("id"), is(itemId));
    assertThat(
      updatedItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("suffix"),
      is("itemLevelCallNumberSuffix"));
  }

  @Test
  public void updatingHoldingsUpdatesItemEffectiveCallNumberPrefix()
      throws MalformedURLException, InterruptedException,
      ExecutionException, TimeoutException {

    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();

    JsonObject holding = holdingsClient.create(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withCallNumberPrefix("testCallNumberPrefix")
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))).getJson();

    JsonObject itemToCreate = new JsonObject();

    itemToCreate.put("holdingsRecordId", holdingId.toString());
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", annexLibraryLocationId.toString());
    itemToCreate.put("materialTypeId", bookMaterialTypeID);

    Response postFirstItemResponse = create(itemsStorageUrl(""), itemToCreate);
    Response postSecondItemResponse = create(itemsStorageUrl(""), itemToCreate);

    assertThat(postFirstItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(postSecondItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject firstItem = postFirstItemResponse.getJson();
    JsonObject secondItem = postSecondItemResponse.getJson();

    String firstItemId = firstItem.getString("id");
    String secondItemId = secondItem.getString("id");

    assertThat(firstItemId, is(notNullValue()));
    assertThat(secondItemId, is(notNullValue()));

    URL getFirstItemUrl = itemsStorageUrl(String.format("/%s", firstItemId));
    URL getSecondItemUrl = itemsStorageUrl(String.format("/%s", secondItemId));

    Response getFirstItemResponse = get(getFirstItemUrl);
    Response getSecondItemResponse = get(getSecondItemUrl);

    JsonObject firstItemFromGet = getFirstItemResponse.getJson();
    JsonObject secondItemFromGet = getSecondItemResponse.getJson();

    assertThat(firstItemFromGet.getString("id"), is(firstItemId));
    assertThat(firstItemFromGet.getString("holdingsRecordId"), is(holdingId.toString()));
    assertThat(
      firstItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("prefix"),
      is("testCallNumberPrefix"));
    assertThat(secondItemFromGet.getString("id"), is(secondItemId));
    assertThat(secondItemFromGet.getString("holdingsRecordId"), is(holdingId.toString()));
    assertThat(
      secondItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("prefix"),
      is("testCallNumberPrefix"));

    URL holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));

    holding.remove("callNumberPrefix");
    holding.put("callNumberPrefix", "updatedCallNumberPrefix");

    Response putResponse = update(holdingsUrl, holding);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    assertThat(holding.getString("callNumberPrefix"), is("updatedCallNumberPrefix"));

    Response getFirstUpdatedItemResponse = get(getFirstItemUrl);
    Response getSecondUpdatedItemResponse = get(getSecondItemUrl);

    JsonObject firstUpdatedItemFromGet = getFirstUpdatedItemResponse.getJson();
    JsonObject secondUpdatedItemFromGet = getSecondUpdatedItemResponse.getJson();

    assertThat(getFirstUpdatedItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(firstUpdatedItemFromGet.getString("id"), is(firstItemId));
    assertThat(
      firstUpdatedItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("prefix"),
      is("updatedCallNumberPrefix"));
    assertThat(getSecondUpdatedItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(secondUpdatedItemFromGet.getString("id"), is(secondItemId));
    assertThat(
      secondUpdatedItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("prefix"),
      is("updatedCallNumberPrefix"));
  }

  @Test
  public void removingHoldingsCallNumberPrefixUpdatesItemEffectiveCallNumberPrefix()
      throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();

    JsonObject holding = holdingsClient.create(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withCallNumberPrefix("testCallNumberPrefix")
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))).getJson();

    JsonObject itemToCreate = new JsonObject();

    itemToCreate.put("holdingsRecordId", holdingId.toString());
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", annexLibraryLocationId.toString());
    itemToCreate.put("materialTypeId", bookMaterialTypeID);

    Response postFirstItemResponse = create(itemsStorageUrl(""), itemToCreate);

    assertThat(postFirstItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject firstItemFromPost = postFirstItemResponse.getJson();

    String firstItemId = firstItemFromPost.getString("id");

    assertThat(firstItemId, is(notNullValue()));

    URL getFirstItemUrl = itemsStorageUrl(String.format("/%s", firstItemId));

    Response getFirstItemResponse = get(getFirstItemUrl);

    JsonObject firstItemFromGet = getFirstItemResponse.getJson();

    assertThat(firstItemFromGet.getString("id"), is(firstItemId));
    assertThat(firstItemFromGet.getString("holdingsRecordId"), is(holdingId.toString()));
    assertThat(
      firstItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("prefix"),
      is("testCallNumberPrefix"));

    URL holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));

    holding.remove("callNumberPrefix");

    Response putResponse = update(holdingsUrl, holding);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    assertThat(holding.containsKey("callNumberPrefix"), is(false));

    Response getFirstUpdatedItemResponse = get(getFirstItemUrl);

    JsonObject firstUpdatedItemFromGet = getFirstUpdatedItemResponse.getJson();

    assertThat(getFirstUpdatedItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(firstUpdatedItemFromGet.getString("id"), is(firstItemId));
    assertThat(
      firstUpdatedItemFromGet.getJsonObject("effectiveCallNumberComponents").containsKey("prefix"),
      is(false));
  }

  @Test
  public void holdingsCallNumberPrefixDoesNotSupersedeItemLevelCallNumberPrefix()
      throws MalformedURLException, InterruptedException, TimeoutException, ExecutionException {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();

    JsonObject holding = holdingsClient.create(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withCallNumberPrefix("holdingsCallNumberPrefix")
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))).getJson();

    JsonObject itemToCreate = new JsonObject();

    itemToCreate.put("holdingsRecordId", holdingId.toString());
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", annexLibraryLocationId.toString());
    itemToCreate.put("materialTypeId", bookMaterialTypeID);
    itemToCreate.put("itemLevelCallNumberPrefix", "itemLevelCallNumberPrefix");

    Response postItemResponse = create(itemsStorageUrl(""), itemToCreate);

    assertThat(postItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject itemFromPost = postItemResponse.getJson();

    String itemId = itemFromPost.getString("id");

    assertThat(itemId, is(notNullValue()));

    URL getItemUrl = itemsStorageUrl(String.format("/%s", itemId));

    Response getItemResponse = get(getItemUrl);

    JsonObject itemFromGet = getItemResponse.getJson();

    assertThat(itemFromGet.getString("id"), is(itemId));
    assertThat(itemFromGet.getString("holdingsRecordId"), is(holdingId.toString()));
    assertThat(
      itemFromGet.getJsonObject("effectiveCallNumberComponents").getString("prefix"),
      is("itemLevelCallNumberPrefix"));

    URL holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));

    holding.remove("callNumberPrefix");
    holding.put("callNumberPrefix", "updatedHoldingCallNumberPrefix");

    Response putResponse = update(holdingsUrl, holding);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getUpdatedItemResponse = get(getItemUrl);

    JsonObject updatedItemFromGet = getUpdatedItemResponse.getJson();

    assertThat(getUpdatedItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(updatedItemFromGet.getString("id"), is(itemId));
    assertThat(
      updatedItemFromGet.getJsonObject("effectiveCallNumberComponents").getString("prefix"),
      is("itemLevelCallNumberPrefix"));
  }

  @Test
  public void cannotCreateHoldingWithoutPermanentLocation() throws Exception {
    UUID instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));

    JsonObject holdingsRecord = new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withPermanentLocation(null)
      .create();

    Response response = create(holdingsStorageUrl(""), holdingsRecord);

    assertThat(response.getStatusCode(), is(422));

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertThat(errors.size(), is(1));

    JsonObject firstError = errors.getJsonObject(0);
    assertThat(firstError.getString("message"), anyOf(is("may not be null"), is("must not be null")));
    assertThat(firstError.getJsonArray("parameters").getJsonObject(0).getString("key"),
      is("permanentLocationId"));
  }

  @Test
  public void canCreateAHoldingsWhenHRIDIsSupplied()
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    log.info("Starting canCreateAHoldingsWhenHRIDIsSupplied");

    final UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    final UUID holdingsId = UUID.randomUUID();

    final String hrid = "TEST1001";

    final JsonObject holdings = holdingsClient.create(new HoldingRequestBuilder()
      .withId(holdingsId)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withHrid(hrid)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))).getJson();

    assertThat(holdings.getString("hrid"), is(hrid));

    final Response getResponse = holdingsClient.getById(holdingsId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    final JsonObject holdingsFromGet = getResponse.getJson();

    assertThat(holdingsFromGet.getString("hrid"), is(hrid));

    log.info("Finished canCreateAHoldingsWhenHRIDIsSupplied");
  }

  @Test
  public void cannotCreateAHoldingsWhenDuplicateHRIDIsSupplied()
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    log.info("Starting cannotCreateAHoldingsWhenDuplicateHRIDIsSupplied");

    final UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    final UUID holdingsId = UUID.randomUUID();

    setHoldingsSequence(1);

    final JsonObject holdings = holdingsClient.create(new HoldingRequestBuilder()
      .withId(holdingsId)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))).getJson();

    assertThat(holdings.getString("hrid"), is("ho00000000001"));

    final Response getResponse = holdingsClient.getById(holdingsId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    final JsonObject holdingsFromGet = getResponse.getJson();

    assertThat(holdingsFromGet.getString("hrid"), is("ho00000000001"));

    final JsonObject duplicateHoldings = new HoldingRequestBuilder()
        .withId(UUID.randomUUID())
        .forInstance(instanceId)
        .withPermanentLocation(mainLibraryLocationId)
        .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))
        .withHrid("ho00000000001")
        .create();

    final Response duplicateResponse = create(holdingsStorageUrl(""), duplicateHoldings);

    assertThat(duplicateResponse.getStatusCode(), is(422));

    final Errors errors = duplicateResponse.getJson().mapTo(Errors.class);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), notNullValue());
    assertThat(errors.getErrors().size(), is(1));
    assertThat(errors.getErrors().get(0), notNullValue());
    assertThat(errors.getErrors().get(0).getMessage(),
        containsString("value already exists in table holdings_record: ho00000000001"));
    assertThat(errors.getErrors().get(0).getParameters(), notNullValue());
    assertThat(errors.getErrors().get(0).getParameters().size(), is(1));
    assertThat(errors.getErrors().get(0).getParameters().get(0), notNullValue());
    assertThat(errors.getErrors().get(0).getParameters().get(0).getKey(),
        is("lower(f_unaccent(jsonb ->> 'hrid'::text))"));
    assertThat(errors.getErrors().get(0).getParameters().get(0).getValue(),
        is("ho00000000001"));

    log.info("Finished cannotCreateAHoldingsWhenDuplicateHRIDIsSupplied");
  }

  @Test
  @SneakyThrows
  public void cannotCreateAHoldingsWithHRIDFailure() {
    final UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    setHoldingsSequence(99_999_999_999L);

    final JsonObject goodHholdings = holdingsClient.create(new HoldingRequestBuilder()
      .withId(UUID.randomUUID())
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))).getJson();

    assertThat(goodHholdings.getString("hrid"), is("ho99999999999"));

    final JsonObject badHoldings = new HoldingRequestBuilder()
      .withId(UUID.randomUUID())
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))
      .create();

    final CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(holdingsStorageUrl(""), badHoldings, TENANT_ID, text(createCompleted));

    final Response response = createCompleted.get(10, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_INTERNAL_ERROR));
    assertThat(response.getBody(), isMaximumSequenceValueError("hrid_holdings_seq"));
  }

  @Test
  @SneakyThrows
  public void cannotCreateAHoldingsWhenAlreadyAllocatedHRIDIsAllocated() {
    final UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    setHoldingsSequence(1000L);

    final var holdingsRequest = new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .create();

    // Allocate the HRID
    final var firstAllocation = holdingsClient.create(holdingsRequest).getJson();

    assertThat(firstAllocation.getString("hrid"), is("ho00000001000"));

    // Reset the sequence
    setHoldingsSequence(1000L);

    // Attempt second allocation
    final CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(holdingsStorageUrl(""), holdingsRequest, TENANT_ID,
      json(createCompleted));

    final Response response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(422));

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertThat(errors.size(), is(1));

    JsonObject firstError = errors.getJsonObject(0);
    assertThat(firstError.getString("message"), is(
      "lower(f_unaccent(jsonb ->> 'hrid'::text)) value already exists in table holdings_record: ho00000001000"));
  }

  @Test
  public void cannotChangeHRIDAfterCreation()
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    log.info("Starting cannotChangeHRIDAfterCreation");

    final UUID instanceId = UUID.randomUUID();
    final UUID holdingsId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    setHoldingsSequence(1);

    final JsonObject holdings = holdingsClient.create(new HoldingRequestBuilder()
      .withId(holdingsId)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))).getJson();

    assertThat(holdings.getString("hrid"), is("ho00000000001"));

    holdings.put("hrid", "ABC123");

    final CompletableFuture<Response> updateCompleted = new CompletableFuture<>();

    getClient().put(holdingsStorageUrl(String.format("/%s", holdingsId)), holdings, TENANT_ID,
        text(updateCompleted));

    final Response response = updateCompleted.get(10, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    assertThat(response.getBody(),
        is("The hrid field cannot be changed: new=ABC123, old=ho00000000001"));

    log.info("Finished cannotChangeHRIDAfterCreation");
  }

  @Test
  public void cannotRemoveHRIDAfterCreation()
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    log.info("Starting cannotRemoveHRIDAfterCreation");

    final UUID instanceId = UUID.randomUUID();
    final UUID holdingsId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    setHoldingsSequence(1);

    final JsonObject holdings = holdingsClient.create(new HoldingRequestBuilder()
      .withId(holdingsId)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))).getJson();

    assertThat(holdings.getString("hrid"), is("ho00000000001"));

    holdings.remove("hrid");

    final CompletableFuture<Response> updateCompleted = new CompletableFuture<>();

    getClient().put(holdingsStorageUrl(String.format("/%s", holdingsId)), holdings, TENANT_ID,
        text(updateCompleted));

    final Response response = updateCompleted.get(10, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    assertThat(response.getBody(),
        is("The hrid field cannot be changed: new=null, old=ho00000000001"));

    log.info("Finished cannotRemoveHRIDAfterCreation");
  }

  @Test
  public void canUsePutToCreateAHoldingsWhenHRIDIsSupplied() {
    log.info("Starting canUsePutToCreateAHoldingsWhenHRIDIsSupplied");

    final UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    final UUID holdingsId = UUID.randomUUID();

    final String hrid = "TEST1001";

    holdingsClient.replace(holdingsId, new HoldingRequestBuilder()
      .withId(holdingsId)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withHrid(hrid)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE))));

    final Response getResponse = holdingsClient.getById(holdingsId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    final JsonObject holdingsFromGet = getResponse.getJson();

    assertThat(holdingsFromGet.getString("hrid"), is(hrid));
    // Make sure a create event published vs update event
    assertCreateEventForHolding(holdingsFromGet);
    assertNoUpdateEventForHolding(instanceId.toString(), holdingsId.toString());

    log.info("Finished canUsePutToCreateAHoldingsWhenHRIDIsSupplied");
  }

  @Test
  public void canPostSynchronousBatchWithGeneratedHRID() {
    log.info("Starting canPostSynchronousBatchWithGeneratedHRID");

    setHoldingsSequence(1);

    final JsonArray holdingsArray = threeHoldings();

    assertThat(postSynchronousBatch(holdingsArray), statusCodeIs(HTTP_CREATED));

    holdingsArray.stream()
        .map(o -> (JsonObject) o)
        .forEach(holdings -> {
          final Response response = getById(holdings.getString("id"));
          assertExists(response, holdings);
          assertHRIDRange(response, "ho00000000001", "ho00000000003");
          JsonObject body = response.getJson();
          assertEquals(body.getString("effectiveLocationId"), body.getString("permanentLocationId"));

        });

    log.info("Finished canPostSynchronousBatchWithGeneratedHRID");
  }

  @Test
  public void canPostSynchronousBatchWithSuppliedAndGeneratedHRID() {
    log.info("Starting canPostSynchronousBatchWithSuppliedAndGeneratedHRID");

    setHoldingsSequence(1);

    final String hrid = "ABC123";
    final JsonArray holdingsArray = threeHoldings();

    holdingsArray.getJsonObject(1).put("hrid", hrid);

    assertThat(postSynchronousBatch(holdingsArray), statusCodeIs(HTTP_CREATED));

    Response response = getById(holdingsArray.getJsonObject(0).getString("id"));
    assertExists(response, holdingsArray.getJsonObject(0));
    assertHRIDRange(response, "ho00000000001", "ho00000000002");

    response = getById(holdingsArray.getJsonObject(1).getString("id"));
    assertExists(response, holdingsArray.getJsonObject(1));
    assertThat(response.getJson().getString("hrid"), is(hrid));

    response = getById(holdingsArray.getJsonObject(2).getString("id"));
    assertExists(response, holdingsArray.getJsonObject(2));
    assertHRIDRange(response, "ho00000000001", "ho00000000002");

    log.info("Finished canPostSynchronousBatchWithSuppliedAndGeneratedHRID");
  }

  @Test
  public void cannotPostSynchronousBatchWithDuplicateHRIDs() {
    log.info("Starting cannotPostSynchronousBatchWithDuplicateHRIDs");

    setHoldingsSequence(1);

    final JsonArray holdingsArray = threeHoldings();
    final String duplicateHRID = "ho00000000001";
    holdingsArray.getJsonObject(1).put("hrid", duplicateHRID);

    assertThat(postSynchronousBatch(holdingsArray), allOf(
        statusCodeIs(HTTP_UNPROCESSABLE_ENTITY),
        anyOf(errorMessageContains("value already exists"), errorMessageContains("duplicate key")),
        errorParametersValueIs(duplicateHRID)));

    for (int i = 0; i < holdingsArray.size(); i++) {
      assertGetNotFound(holdingsStorageUrl("/" + holdingsArray.getJsonObject(i).getString("id")));
    }

    log.info("Finished cannotPostSynchronousBatchWithDuplicateHRIDs");
  }

  @Test
  public void cannotPostSynchronousBatchWithHRIDFailure() {
    log.info("Starting cannotPostSynchronousBatchWithHRIDFailure");

    setHoldingsSequence(99999999999L);

    final JsonArray holdingsArray = threeHoldings();

    final Response response = postSynchronousBatch(holdingsArray);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_INTERNAL_ERROR));
    // vertx-pg-client only returns message in message (not code)
    assertThat(response.getBody(), isMaximumSequenceValueError("hrid_holdings_seq"));

    for (int i = 0; i < holdingsArray.size(); i++) {
      assertGetNotFound(holdingsStorageUrl("/" + holdingsArray.getJsonObject(i).getString("id")));
    }

    log.info("Finished cannotPostSynchronousBatchWithHRIDFailure");
  }

  @Test
  public void canFilterByFullCallNumber() throws Exception {
    IndividualResource instance = instancesClient
      .create(smallAngryPlanet(UUID.randomUUID()));

    IndividualResource wholeCallNumberHolding = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instance.getId())
        .withPermanentLocation(mainLibraryLocationId)
        .withCallNumberPrefix("prefix")
        .withCallNumber("callNumber")
        .withCallNumberSuffix("suffix"));

    holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instance.getId())
        .withPermanentLocation(mainLibraryLocationId)
        .withCallNumberPrefix("prefix")
        .withCallNumber("callNumber"));

    holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instance.getId())
        .withPermanentLocation(mainLibraryLocationId)
        .withCallNumberPrefix("prefix")
        .withCallNumber("differentCallNumber")
        .withCallNumberSuffix("suffix"));

    final List<IndividualResource> foundHoldings = holdingsClient
      .getMany("fullCallNumber == \"%s\"", "prefix callNumber suffix");

    assertThat(foundHoldings.size(), is(1));
    assertThat(foundHoldings.get(0).getId(), is(wholeCallNumberHolding.getId()));
  }

  @Test
  public void canFilterByCallNumberAndSuffix() throws Exception {
    IndividualResource instance = instancesClient
      .create(smallAngryPlanet(UUID.randomUUID()));

    IndividualResource wholeCallNumberHolding = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instance.getId())
        .withPermanentLocation(mainLibraryLocationId)
        .withCallNumberPrefix("prefix")
        .withCallNumber("callNumber")
        .withCallNumberSuffix("suffix"));

    holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instance.getId())
        .withPermanentLocation(mainLibraryLocationId)
        .withCallNumberPrefix("prefix")
        .withCallNumber("callNumber"));

    IndividualResource noPrefixHolding = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instance.getId())
        .withPermanentLocation(mainLibraryLocationId)
        .withCallNumber("callNumber")
        .withCallNumberSuffix("suffix"));

    final List<IndividualResource> foundHoldings = holdingsClient
      .getMany("callNumberAndSuffix == \"%s\"", "callNumber suffix");

    assertThat(foundHoldings.size(), is(2));

    final Set<UUID> allFoundIds = foundHoldings.stream()
      .map(IndividualResource::getId)
      .collect(Collectors.toSet());
    assertThat(allFoundIds, hasItems(wholeCallNumberHolding.getId(), noPrefixHolding.getId()));
  }

  @Test
  public void canFilterByInstanceProperty() {
    IndividualResource instancePlanet = instancesClient
        .create(smallAngryPlanet(UUID.randomUUID()));
    IndividualResource instanceUprooted = instancesClient
        .create(uprooted(UUID.randomUUID()));
    UUID holdingPlanet = createHolding(instancePlanet.getId(), mainLibraryLocationId, null);
    UUID holdingUprooted = createHolding(instanceUprooted.getId(), mainLibraryLocationId, null);

    var foundPlanet = holdingsClient.getMany("instance.title = planet");
    assertThat(foundPlanet, hasSize(1));
    assertThat(foundPlanet.get(0).getId(), is(holdingPlanet));

    var foundUprooted = holdingsClient.getMany("instance.title = uprooted");
    assertThat(foundUprooted, hasSize(1));
    assertThat(foundUprooted.get(0).getId(), is(holdingUprooted));
  }

  private void setHoldingsSequence(long sequenceNumber) {
    final PostgresClient postgresClient =
        PostgresClient.getInstance(getVertx(), TENANT_ID);
    final CompletableFuture<Void> sequenceSet = new CompletableFuture<>();

    getVertx().runOnContext(v ->
      postgresClient.selectSingle("select setval('hrid_holdings_seq',"
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

  /**
   * Create three instances, and for each of them return a JsonObject of a new holding belonging to it.
   * The holdings are not created.
   */
  private JsonArray threeHoldings() {
    JsonArray holdingsArray = new JsonArray();
    for (int i=0; i<3; i++) {
      UUID instanceId = UUID.randomUUID();
      instancesClient.create(smallAngryPlanet(instanceId));
      holdingsArray.add(new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("instanceId", instanceId.toString())
          .put("_version", 1)
          .put("permanentLocationId", mainLibraryLocationId.toString()));
    }
    return holdingsArray;
  }

  private Response postSynchronousBatch(JsonArray holdingsArray) {
    return postSynchronousBatch(holdingsStorageSyncUrl(""), holdingsArray);
  }

  private Response postSynchronousBatchUnsafe(JsonArray holdingsArray) {
    return postSynchronousBatch(holdingsStorageSyncUnsafeUrl(""), holdingsArray);
  }

  private Response postSynchronousBatch(String subPath, JsonArray holdingsArray) {
    return postSynchronousBatch(holdingsStorageSyncUrl(subPath), holdingsArray);
  }

  private Response postSynchronousBatch(URL url, JsonArray holdingsArray) {
    JsonObject holdingsCollection = new JsonObject().put("holdingsRecords", holdingsArray);
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    getClient().post(url, holdingsCollection, TENANT_ID, ResponseHandler.any(createCompleted));
    try {
      return createCompleted.get(10, SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void cannotPostSynchronousBatchUnsafeIfNotAllowed() {
    // not allowed because env var DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING is not set
    JsonArray holdings = threeHoldings();
    assertThat(postSynchronousBatchUnsafe(holdings), statusCodeIs(413));
  }

  @Test
  public void canPostSynchronousBatchUnsafe() {
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
        Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));

    // insert
    JsonArray holdings = threeHoldings();
    assertThat(postSynchronousBatchUnsafe(holdings), statusCodeIs(HttpStatus.HTTP_CREATED));
    // unsafe update
    holdings.getJsonObject(1).put("copyNumber", "456");
    assertThat(postSynchronousBatchUnsafe(holdings), statusCodeIs(HttpStatus.HTTP_CREATED));
    // safe update, env var should not influence the regular API
    holdings.getJsonObject(1).put("copyNumber", "789");
    assertThat(postSynchronousBatch("?upsert=true", holdings), statusCodeIs(409));
  }

  @Test
  public void canPostSynchronousBatch() {
    JsonArray holdingsArray = threeHoldings();
    assertThat(postSynchronousBatch(holdingsArray), statusCodeIs(HTTP_CREATED));
    for (Object hrObj : holdingsArray) {
      final JsonObject holding = (JsonObject) hrObj;

      assertExists(holding);
      assertCreateEventForHolding(getById(holding.getString("id")).getJson());
    }
  }

  @Test
  public void cannotPostSynchronousBatchWithDuplicateId() {
    JsonArray holdingsArray = threeHoldings();
    String duplicateId = holdingsArray.getJsonObject(0).getString("id");
    holdingsArray.getJsonObject(1).put("id", duplicateId);
    assertThat(postSynchronousBatch(holdingsArray), allOf(
        statusCodeIs(HTTP_UNPROCESSABLE_ENTITY),
        anyOf(errorMessageContains("value already exists"), errorMessageContains("duplicate key")),
        errorParametersValueIs(duplicateId)));

    for (int i=0; i<holdingsArray.size(); i++) {
      assertGetNotFound(holdingsStorageUrl("/" + holdingsArray.getJsonObject(i).getString("id")));
    }
  }

  public Response postSynchronousBatchWithExistingId(String subPath) {
    JsonArray holdingsArray1 = threeHoldings();
    JsonArray holdingsArray2 = threeHoldings();
    String existingId = holdingsArray1.getJsonObject(1).getString("id");
    holdingsArray2.getJsonObject(1).put("id", existingId);
    assertThat(postSynchronousBatch(subPath, holdingsArray1), statusCodeIs(HTTP_CREATED));
    return postSynchronousBatch(subPath, holdingsArray2);
  }

  @Test
  public void cannotPostSynchronousBatchWithExistingIdWithoutUpsertParameter() {
    assertThat(postSynchronousBatchWithExistingId(""), statusCodeIs(HTTP_UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotPostSynchronousBatchWithExistingIdWithUpsertFalse() {
    assertThat(postSynchronousBatchWithExistingId("?upsert=false"), statusCodeIs(HTTP_UNPROCESSABLE_ENTITY));
  }

  @Test
  public void canPostSynchronousBatchWithExistingIdWithUpsertTrue() {
    final String existingHrId = UUID.randomUUID().toString();
    final JsonArray holdingsArray1 = threeHoldings();
    final JsonArray holdingsArray2 = threeHoldings();

    holdingsArray1.getJsonObject(1).put("id", existingHrId);
    holdingsArray2.getJsonObject(1).put("id", existingHrId);

    final Response firstResponse = postSynchronousBatch("?upsert=true", holdingsArray1);
    final JsonObject existingHrBeforeUpdate = getById(existingHrId).getJson();
    final Response secondResponse = postSynchronousBatch("?upsert=true", holdingsArray2);

    assertThat(firstResponse, statusCodeIs(HTTP_CREATED));
    assertThat(secondResponse, statusCodeIs(HTTP_CREATED));

    Stream.concat(holdingsArray1.stream(), holdingsArray2.stream())
      .map(json -> ((JsonObject) json).getString("id"))
      .filter(id -> !id.equals(existingHrId))
      .map(this::getById)
      .map(Response::getJson)
      .forEach(DomainEventAssertions::assertCreateEventForHolding);

    await().untilAsserted(() -> {
      var newHr = getById(existingHrId).getJson();
      final String id = newHr.getString("id");
      final String instanceId = newHr.getString("instanceId");

      assertThat(getHoldingsEvents(instanceId, id).size(), greaterThan(0));
      assertUpdateEvent(getLastHoldingEvent(instanceId, id),
        existingHrBeforeUpdate, newHr);
    });
  }

  @Test
  public void canSearchByDiscoverySuppressProperty() throws Exception {
    final IndividualResource instance = instancesClient
      .create(smallAngryPlanet(UUID.randomUUID()));

    final IndividualResource suppressedHolding = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instance.getId())
        .withPermanentLocation(mainLibraryLocationId)
        .withDiscoverySuppress(true));

    final IndividualResource notSuppressedHolding = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instance.getId())
        .withPermanentLocation(mainLibraryLocationId)
        .withDiscoverySuppress(false));

    final IndividualResource notSuppressedHoldingDefault = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instance.getId())
        .withPermanentLocation(mainLibraryLocationId));

    final List<IndividualResource> suppressedHoldings = holdingsClient
      .getMany("discoverySuppress==true");
    final List<IndividualResource> notSuppressedHoldings = holdingsClient
      .getMany("cql.allRecords=1 not discoverySuppress==true");

    assertThat(suppressedHoldings.size(), is(1));
    assertThat(suppressedHoldings.get(0).getId(), is(suppressedHolding.getId()));

    assertThat(notSuppressedHoldings.size(), is(2));
    assertThat(notSuppressedHoldings.stream()
        .map(IndividualResource::getId)
        .collect(Collectors.toList()),
      containsInAnyOrder(notSuppressedHolding.getId(), notSuppressedHoldingDefault.getId()));
  }

  @Test
  public void shouldFindHoldingByCallNumberWhenThereIsSuffix() throws Exception {
    final IndividualResource instance = instancesClient
      .create(smallAngryPlanet(UUID.randomUUID()));

    final IndividualResource firstHoldingsToMatch = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instance.getId())
        .withCallNumber("GE77 .F73 2014")
        .withPermanentLocation(mainLibraryLocationId));

    final IndividualResource secondHoldingsToMatch = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instance.getId())
        .withCallNumber("GE77 .F73 2014")
        .withCallNumberSuffix("Curriculum Materials Collection")
        .withPermanentLocation(mainLibraryLocationId));

    holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instance.getId())
        .withCallNumber("GE77 .F73 ")
        .withCallNumberSuffix("2014 Curriculum Materials Collection")
        .withPermanentLocation(mainLibraryLocationId));

    final List<UUID> foundHoldings = searchByCallNumberEyeReadable("GE77 .F73 2014");

    assertThat(foundHoldings.size(), is(2));
    assertThat(foundHoldings, hasItems(firstHoldingsToMatch.getId(),
      secondHoldingsToMatch.getId()));
  }

  @Test
  public void explicitRightTruncationCanBeApplied() throws Exception {
    final IndividualResource instance = instancesClient
      .create(smallAngryPlanet(UUID.randomUUID()));

    final IndividualResource firstHoldingsToMatch = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instance.getId())
        .withCallNumber("GE77 .F73 2014")
        .withPermanentLocation(mainLibraryLocationId));

    final IndividualResource secondHoldingsToMatch = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instance.getId())
        .withCallNumber("GE77 .F73 2014")
        .withCallNumberSuffix("Curriculum Materials Collection")
        .withPermanentLocation(mainLibraryLocationId));

    final IndividualResource thirdHoldingsToMatch = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instance.getId())
        .withCallNumber("GE77 .F73 ")
        .withCallNumberSuffix("2014 Curriculum Materials Collection")
        .withPermanentLocation(mainLibraryLocationId));

    holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instance.getId())
        .withCallNumber("GE77 .F74 ")
        .withCallNumberSuffix("2014 Curriculum Materials Collection")
        .withPermanentLocation(mainLibraryLocationId));

    final List<UUID> foundHoldings = searchByCallNumberEyeReadable("GE77 .F73*");

    assertThat(foundHoldings.size(), is(3));
    assertThat(foundHoldings, hasItems(firstHoldingsToMatch.getId(),
      secondHoldingsToMatch.getId(), thirdHoldingsToMatch.getId()));
  }

  @Test
  public void canSetHoldingStatementWithNotes() throws Exception {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();

    JsonObject holdingsStatement = new JsonObject();
    holdingsStatement.put("statement", "Test statement");
    holdingsStatement.put("note", "Test note");
    holdingsStatement.put("staffNote", "Test staff note");

    JsonArray holdingsStatements = new JsonArray().add(holdingsStatement);

    IndividualResource holdingResponse = holdingsClient.create(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))
      .withHoldingsStatements(holdingsStatements));

    JsonObject holding = holdingResponse.getJson();

    assertThat(holding.getString("instanceId"), is(instanceId.toString()));
    assertThat(holding.getString("permanentLocationId"), is(mainLibraryLocationId.toString()));

    JsonObject responseStatement = holding.getJsonArray("holdingsStatements").getJsonObject(0);

    assertThat(responseStatement.getString("statement"), is("Test statement"));
    assertThat(responseStatement.getString("note"), is("Test note"));
    assertThat(responseStatement.getString("staffNote"), is("Test staff note"));
  }

  @Test
  public void canSetHoldingStatementForIndexesWithNotes() throws Exception {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();

    JsonObject holdingsStatement = new JsonObject();
    holdingsStatement.put("statement", "Test statement");
    holdingsStatement.put("note", "Test note");
    holdingsStatement.put("staffNote", "Test staff note");

    JsonArray holdingsStatements = new JsonArray().add(holdingsStatement);

    IndividualResource holdingResponse = holdingsClient.create(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))
      .withHoldingsStatementsForIndexes(holdingsStatements));

    JsonObject holding = holdingResponse.getJson();

    assertThat(holding.getString("instanceId"), is(instanceId.toString()));
    assertThat(holding.getString("permanentLocationId"), is(mainLibraryLocationId.toString()));

    JsonObject responseStatement = holding.getJsonArray("holdingsStatementsForIndexes")
      .getJsonObject(0);

    assertThat(responseStatement.getString("statement"), is("Test statement"));
    assertThat(responseStatement.getString("note"), is("Test note"));
    assertThat(responseStatement.getString("staffNote"), is("Test staff note"));
  }

  @Test
  public void canSetHoldingStatementForSupplementsWithNotes() throws Exception {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();

    JsonObject holdingsStatement = new JsonObject();
    holdingsStatement.put("statement", "Test statement");
    holdingsStatement.put("note", "Test note");
    holdingsStatement.put("staffNote", "Test staff note");

    JsonArray holdingsStatements = new JsonArray().add(holdingsStatement);

    IndividualResource holdingResponse = holdingsClient.create(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))
      .withHoldingsStatementsForSupplements(holdingsStatements));

    JsonObject holding = holdingResponse.getJson();

    assertThat(holding.getString("instanceId"), is(instanceId.toString()));
    assertThat(holding.getString("permanentLocationId"), is(mainLibraryLocationId.toString()));

    JsonObject responseStatement = holding.getJsonArray("holdingsStatementsForSupplements")
      .getJsonObject(0);

    assertThat(responseStatement.getString("statement"), is("Test statement"));
    assertThat(responseStatement.getString("note"), is("Test note"));
    assertThat(responseStatement.getString("staffNote"), is("Test staff note"));
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
    getClient().get(holdingsStorageUrl("/" + id), TENANT_ID, json(getCompleted));
    try {
      return getCompleted.get(10, SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private void assertExists(JsonObject expectedHolding) {
    Response response = getById(expectedHolding.getString("id"));
    assertThat(response, statusCodeIs(HttpStatus.HTTP_OK));
    assertThat(response.getBody(), containsString(expectedHolding.getString("instanceId")));
  }

  private void assertExists(Response response, JsonObject expectedHolding) {
    assertThat(response, statusCodeIs(HttpStatus.HTTP_OK));
    assertThat(response.getBody(), containsString(expectedHolding.getString("instanceId")));
  }

  private void assertNotExists(JsonObject holding) {
    try {
      Response response = getClient().get(holdingsStorageUrl("/" + holding.getString("id")), TENANT_ID)
          .get(10, SECONDS);
      assertThat(response, statusCodeIs(HttpStatus.HTTP_NOT_FOUND));
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private void assertHRIDRange(Response response, String minHRID, String maxHRID) {
    assertThat(response.getJson().getString("hrid"),
        is(both(greaterThanOrEqualTo(minHRID)).and(lessThanOrEqualTo(maxHRID))));
  }

  private Response create(URL url, Object entity) throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(url, entity, TENANT_ID,
      ResponseHandler.json(createCompleted));

    return createCompleted.get(10, TimeUnit.SECONDS);
  }

  private Response get(URL url) throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(url, TENANT_ID,
      ResponseHandler.json(getCompleted));

    return getCompleted.get(10, TimeUnit.SECONDS);
  }

  private List<UUID> searchByCallNumberEyeReadable(String searchTerm)
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    return holdingsClient
      .getMany("fullCallNumber==\"%1$s\" OR callNumberAndSuffix==\"%1$s\" OR callNumber==\"%1$s\"",
      searchTerm)
      .stream()
      .map(IndividualResource::getId)
      .collect(Collectors.toList());
  }

  private Response update(URL url, Object entity) throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    getClient().put(url, entity, TENANT_ID,
      ResponseHandler.empty(putCompleted));

    return putCompleted.get(10, TimeUnit.SECONDS);
  }

  private Response update(JsonObject holding) {
    URL holdingsUrl = holdingsStorageUrl("/" + holding.getString("id"));
    try {
      return update(holdingsUrl, holding);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private List<String> getTags(JsonObject json) {
    return json.getJsonObject("tags").getJsonArray("tagList")
      .stream()
      .map(obj -> (String) obj)
      .collect(Collectors.toList());
  }
}
