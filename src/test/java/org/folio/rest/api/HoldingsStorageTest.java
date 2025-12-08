package org.folio.rest.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToIgnoreCase;
import static com.github.tomakehurst.wiremock.http.Response.Builder.like;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_OK;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.rest.api.ItemEffectiveCallNumberComponentsTest.LC_CALL_NUMBER_TYPE;
import static org.folio.rest.support.HttpResponseMatchers.errorMessageContains;
import static org.folio.rest.support.HttpResponseMatchers.errorParametersValueIs;
import static org.folio.rest.support.HttpResponseMatchers.statusCodeIs;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.ResponseHandler.jsonErrors;
import static org.folio.rest.support.ResponseHandler.text;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageSyncUnsafeUrl;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageSyncUrl;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.matchers.PostgresErrorMessageMatchers.isMaximumSequenceValueError;
import static org.folio.services.CallNumberConstants.LC_CN_TYPE_ID;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.ModuleUtility.prepareTenant;
import static org.folio.utility.ModuleUtility.removeTenant;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.folio.validator.NotesValidators.MAX_NOTE_LENGTH;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import kotlin.jvm.functions.Function4;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.HttpStatus;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonArrayHelper;
import org.folio.rest.support.JsonErrorResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.folio.rest.support.db.OptimisticLocking;
import org.folio.rest.support.messages.HoldingsEventMessageChecks;
import org.folio.rest.support.messages.ItemEventMessageChecks;
import org.folio.rest.tools.utils.OptimisticLockingUtil;
import org.folio.services.consortium.entities.SharingInstance;
import org.folio.services.consortium.entities.SharingStatus;
import org.folio.util.PercentCodec;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

@RunWith(JUnitParamsRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class HoldingsStorageTest extends TestBaseWithInventoryUtil {
  private static final Logger log = LogManager.getLogger();
  private static final String TAG_VALUE = "test-tag";
  private static final String X_OKAPI_URL = "X-Okapi-Url";
  private static final String X_OKAPI_TENANT = "X-Okapi-Tenant";
  private static final String CONSORTIUM_MEMBER_TENANT = "consortium";
  private static final String TENANT_WITHOUT_USER_TENANTS_PERMISSIONS = "nopermissions";
  private static final String USER_TENANTS_PATH = "/user-tenants?limit=1";
  private static final String NEW_TEST_TAG = "new test tag";
  private static final UUID[] HOLDING_SOURCE_IDS = {
    UUID.fromString("7fbd5d84-abcd-1978-8899-6cb173998b00"),
    UUID.fromString("7fbd5d84-abcd-1978-8899-6cb173998b01"),
    UUID.fromString("7fbd5d84-abcd-1978-8899-6cb173998b02")
  };
  private static final String INVALID_VALUE = "invalid value";
  private static final String INVALID_TYPE_ERROR_MESSAGE = String.format("invalid input syntax for type uuid: \"%s\"",
    INVALID_VALUE);

  private final HoldingsEventMessageChecks holdingsMessageChecks
    = new HoldingsEventMessageChecks(KAFKA_CONSUMER, mockServer.baseUrl());

  private final ItemEventMessageChecks itemMessageChecks
    = new ItemEventMessageChecks(KAFKA_CONSUMER, mockServer.baseUrl());

  @SneakyThrows
  @BeforeClass
  public static void beforeClass() {
    prepareTenant(CONSORTIUM_MEMBER_TENANT, false);

    StorageTestSuite.deleteAll(CONSORTIUM_MEMBER_TENANT, "preceding_succeeding_title");
    StorageTestSuite.deleteAll(CONSORTIUM_MEMBER_TENANT, "instance_relationship");
    StorageTestSuite.deleteAll(CONSORTIUM_MEMBER_TENANT, "bound_with_part");
    clearData(CONSORTIUM_MEMBER_TENANT);

    setupMaterialTypes(CONSORTIUM_MEMBER_TENANT);
    setupLoanTypes(CONSORTIUM_MEMBER_TENANT);
    setupLocations(CONSORTIUM_MEMBER_TENANT);

    prepareTenant(TENANT_WITHOUT_USER_TENANTS_PERMISSIONS, false);
    prepareThreeHoldingSource();
  }

  @SneakyThrows
  @AfterClass
  public static void afterClass() {
    removeTenant(CONSORTIUM_MEMBER_TENANT);
    removeTenant(TENANT_WITHOUT_USER_TENANTS_PERMISSIONS);
  }

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

    WireMock.reset();
    mockUserTenantsForNonConsortiumMember();
    mockUserTenantsForConsortiumMember(CONSORTIUM_MEMBER_TENANT);
    mockUserTenantsForTenantWithoutPermissions();
  }

  @SneakyThrows
  @After
  public void afterEach() {
    setHoldingsSequence(1);

    StorageTestSuite.checkForMismatchedIds("holdings_record");
  }

  @Test
  public void canCreateHolding() {
    var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));
    setHoldingsSequence(1);

    var holdingId = UUID.randomUUID();
    var adminNote = "An admin note";

    var holdingToCreate = buildHoldingWithAdminNote(holdingId, instanceId, adminNote);
    var holding = createHoldingRecord(holdingToCreate).getJson();

    assertCreatedHolding(holding, holdingId, instanceId, adminNote);

    var getResponse = holdingsClient.getById(holdingId);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    assertRetrievedHolding(getResponse.getJson(), holdingId, instanceId, adminNote);
  }

  private JsonObject buildHoldingWithAdminNote(UUID holdingId, UUID instanceId, String adminNote) {
    var holdingToCreate = new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE))).create();
    holdingToCreate.put("administrativeNotes", new JsonArray().add(adminNote));
    return holdingToCreate;
  }

  private void assertCreatedHolding(JsonObject holding, UUID holdingId, UUID instanceId, String adminNote) {
    assertThat(holding.getString("id"), is(holdingId.toString()));
    assertThat(holding.getString("instanceId"), is(instanceId.toString()));
    assertThat(holding.getString("permanentLocationId"), is(MAIN_LIBRARY_LOCATION_ID.toString()));
    assertThat(holding.getString("hrid"), is("ho00000000001"));
    assertThat(holding.getString("effectiveLocationId"), is(MAIN_LIBRARY_LOCATION_ID.toString()));
    assertThat(holding.getJsonArray("administrativeNotes").contains(adminNote), is(true));
  }

  private void assertRetrievedHolding(JsonObject holding, UUID holdingId, UUID instanceId, String adminNote) {
    assertThat(holding.getString("id"), is(holdingId.toString()));
    assertThat(holding.getString("instanceId"), is(instanceId.toString()));
    assertThat(holding.getString("permanentLocationId"), is(MAIN_LIBRARY_LOCATION_ID.toString()));
    assertThat(holding.getString("hrid"), is("ho00000000001"));
    assertThat(holding.getJsonArray("administrativeNotes").contains(adminNote), is(true));

    var tags = getTags(holding);
    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
  }

  @Test
  public void canCreateHoldingWithoutProvidingAnId() {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    HoldingRequestBuilder holdingBuilder = new HoldingRequestBuilder()
      .withId(null)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)));
    IndividualResource holdingResponse = createHoldingRecord(holdingBuilder.create());

    JsonObject holding = holdingResponse.getJson();

    assertThat(holding.getString("id"), is(notNullValue()));
    assertThat(holding.getString("instanceId"), is(instanceId.toString()));
    assertThat(holding.getString("permanentLocationId"), is(MAIN_LIBRARY_LOCATION_ID.toString()));

    UUID holdingId = holdingResponse.getId();

    Response getResponse = holdingsClient.getById(holdingId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject holdingFromGet = getResponse.getJson();

    assertThat(holdingFromGet.getString("id"), is(holdingId.toString()));
    assertThat(holdingFromGet.getString("instanceId"), is(instanceId.toString()));
    assertThat(holdingFromGet.getString("permanentLocationId"), is(MAIN_LIBRARY_LOCATION_ID.toString()));

    List<String> tags = getTags(holdingFromGet);

    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
  }

  @Test
  public void cannotCreateHoldingWithIdThatIsNotUuid()
    throws InterruptedException, ExecutionException, TimeoutException {

    String nonUuidId = "6556456";

    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    JsonObject request = new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID).create();

    request.put("id", nonUuidId);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(holdingsStorageUrl(""), request, TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response response = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(422));
    JsonArray errors = response.getJson().getJsonArray("errors");
    assertThat(errors.size(), is(1));

    JsonObject firstError = errors.getJsonObject(0);
    assertThat(firstError.getString("message"), containsString("must match"));
    assertThat(firstError.getJsonArray("parameters").getJsonObject(0).getString("key"),
      is("id"));
  }

  @Test
  public void canCreateHoldingAtSpecificLocation() {

    UUID instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));

    setHoldingsSequence(1);

    UUID holdingId = UUID.randomUUID();

    updateHoldingRecord(holdingId, new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID));

    Response getResponse = holdingsClient.getById(holdingId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject holdingFromGet = getResponse.getJson();

    assertThat(holdingFromGet.getString("id"), is(holdingId.toString()));
    assertThat(holdingFromGet.getString("instanceId"), is(instanceId.toString()));
    assertThat(holdingFromGet.getString("permanentLocationId"), is(MAIN_LIBRARY_LOCATION_ID.toString()));
    assertThat(holdingFromGet.getString("hrid"), is("ho00000000001"));

    List<String> tags = getTags(holdingFromGet);

    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
  }

  @Test
  public void canReplaceHoldingAtSpecificLocation() {
    var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));
    String adminNote = "an admin note";
    setHoldingsSequence(1);

    var sourceId = getPreparedHoldingSourceId();
    var holdingResource = createHoldingRecord(new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withSource(sourceId)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID).create());

    // Clear Kafka events after create to reduce chances of
    // CREATE messages appearing after UPDATE later on.
    // This should be removed once the messaging problem is
    // properly resolved.
    removeAllEvents();

    var holdingId = holdingResource.getId();
    var replacement = buildReplacementHolding(holdingResource, sourceId, adminNote);
    updateHoldingRecord(holdingId, replacement);

    var getResponse = holdingsClient.getById(holdingId);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    assertReplacedHolding(getResponse.getJson(), holdingId, instanceId, sourceId, adminNote);
    holdingsMessageChecks.updatedMessagePublished(holdingResource.getJson(), getResponse.getJson());
  }

  private JsonObject buildReplacementHolding(IndividualResource holdingResource, UUID sourceId, String adminNote) {
    return holdingResource.copyJson()
      .put("permanentLocationId", ANNEX_LIBRARY_LOCATION_ID.toString())
      .put("sourceId", sourceId.toString())
      .put("tags", new JsonObject().put("tagList", new JsonArray().add(NEW_TEST_TAG)))
      .put("administrativeNotes", new JsonArray().add(adminNote));
  }

  private void assertReplacedHolding(JsonObject holding, UUID holdingId, UUID instanceId,
                                      UUID sourceId, String adminNote) {
    assertThat(holding.getString("id"), is(holdingId.toString()));
    assertThat(holding.getString("instanceId"), is(instanceId.toString()));
    assertThat(holding.getString("sourceId"), is(sourceId.toString()));
    assertThat(holding.getString("permanentLocationId"), is(ANNEX_LIBRARY_LOCATION_ID.toString()));
    assertThat(holding.getString("hrid"), is("ho00000000001"));
    assertThat(holding.getJsonArray("administrativeNotes").contains(adminNote), is(true));

    var tags = getTags(holding);
    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(NEW_TEST_TAG));
  }

  @Test
  public void canMoveHoldingsToNewInstance() throws ExecutionException, InterruptedException, TimeoutException {
    var instanceId = UUID.randomUUID();
    var newInstanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));
    instancesClient.create(smallAngryPlanet(newInstanceId));
    setHoldingsSequence(1);

    var holdingResource = createHoldingRecord(new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .create());

    var holdingId = holdingResource.getId();
    final var item = createItemForHolding(holdingId);

    var replacement = holdingResource.copyJson()
      .put("instanceId", newInstanceId.toString());
    updateHoldingRecord(holdingId, replacement);

    var getResponse = holdingsClient.getById(holdingId);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    var holdingFromGet = getResponse.getJson();
    assertThat(holdingFromGet.getString("instanceId"), is(newInstanceId.toString()));

    holdingsMessageChecks.updatedMessagePublished(holdingResource.getJson(), holdingFromGet);

    var newItem = item.copy()
      .put("_version", 2);

    itemMessageChecks.updatedMessagePublished(item, newItem, instanceId.toString());
  }

  @Test
  public void cannotCreateHoldingWithInvalidStatisticalCodeIds() {
    var instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    var holdingToCreate = new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .create();
    holdingToCreate.put(STATISTICAL_CODE_IDS_KEY, Set.of(INVALID_VALUE));

    var response = holdingsClient.attemptToCreate("", holdingToCreate, TENANT_ID,
      Map.of(XOkapiHeaders.URL, mockServer.baseUrl()));
    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), containsString(INVALID_TYPE_ERROR_MESSAGE));
  }

  @Test
  public void cannotUpdateHoldingWithInvalidStatisticalCodeIds() {
    UUID instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));

    JsonObject holdingToCreate = new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .create();

    var holdingResource = createHoldingRecord(holdingToCreate);

    var holdingId = holdingResource.getId();
    var holdingToUpdate = holdingsClient.getById(holdingId);
    var holding = holdingToUpdate.getJson();
    holding.put(STATISTICAL_CODE_IDS_KEY, Set.of(INVALID_VALUE));

    var response = holdingsClient.attemptToReplace(holdingId.toString(), holding, TENANT_ID,
      Map.of(XOkapiHeaders.URL, mockServer.baseUrl()));

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), containsString(INVALID_TYPE_ERROR_MESSAGE));
  }

  @Test
  public void cannotCreateHoldingWithInvalidInstanceId() {
    var instanceId = UUID.randomUUID();

    var holdingToCreate = new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .create();

    var response = holdingsClient.attemptToCreate("", holdingToCreate, TENANT_ID,
      Map.of(XOkapiHeaders.URL, mockServer.baseUrl()));
    assertThat(response.getStatusCode(), is(422));
    assertTrue(response.getBody().contains(String.format(
      "Cannot set holdings_record.instanceid = %s because it does not exist in instance.id.", instanceId)));
  }

  @Test
  public void canDeleteHolding() {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    IndividualResource holdingResource = createHoldingRecord(new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID).create());

    UUID holdingId = holdingResource.getId();

    holdingsClient.delete(holdingId, Map.of(XOkapiHeaders.URL, mockServer.baseUrl()));

    Response getResponse = holdingsClient.getById(holdingId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));

    holdingsMessageChecks.deletedMessagePublished(holdingResource.getJson());
  }

  @SneakyThrows
  @Test
  public void canGetAllHoldings() {
    var holdingIds = createThreeHoldingsForInstances();

    var getCompleted = new CompletableFuture<Response>();
    getClient().get(holdingsStorageUrl(""), TENANT_ID, ResponseHandler.json(getCompleted));
    var response = getCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertAllHoldingsResponse(response, holdingIds);
  }

  private UUID[] createThreeHoldingsForInstances() {
    UUID firstInstanceId = UUID.randomUUID();
    UUID secondInstanceId = UUID.randomUUID();
    UUID thirdInstanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(firstInstanceId));
    instancesClient.create(nod(secondInstanceId));
    instancesClient.create(uprooted(thirdInstanceId));

    final UUID firstHoldingId = createHoldingRecord(new HoldingRequestBuilder()
      .forInstance(firstInstanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID).create()).getId();

    final UUID secondHoldingId = createHoldingRecord(new HoldingRequestBuilder()
      .forInstance(secondInstanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(ANNEX_LIBRARY_LOCATION_ID).create()).getId();

    final UUID thirdHoldingId = createHoldingRecord(new HoldingRequestBuilder()
      .forInstance(thirdInstanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE))).create()).getId();

    return new UUID[] { firstHoldingId, secondHoldingId, thirdHoldingId };
  }

  private void assertAllHoldingsResponse(Response response, UUID[] holdingIds) {
    var responseBody = response.getJson();
    var allHoldings = JsonArrayHelper.toList(responseBody.getJsonArray("holdingsRecords"));

    assertThat(allHoldings.size(), is(3));
    assertThat(responseBody.getInteger("totalRecords"), is(3));

    assertThat(allHoldings.stream().anyMatch(filterById(holdingIds[0])), is(true));
    assertThat(allHoldings.stream().anyMatch(filterById(holdingIds[1])), is(true));
    assertThat(allHoldings.stream().anyMatch(filterById(holdingIds[2])), is(true));
  }

  @SneakyThrows
  @Test
  public void canRetrieveAllHoldings() {
    var holdingIds = createThreeHoldingsForInstances();

    var getCompleted = new CompletableFuture<Response>();
    getClient().post(holdingsStorageUrl("/retrieve"), new JsonObject(), TENANT_ID,
      ResponseHandler.json(getCompleted));

    var response = getCompleted.get(TIMEOUT, TimeUnit.SECONDS);
    assertAllHoldingsResponse(response, holdingIds);
  }

  @Test
  public void cannotPageWithNegativeLimit() throws Exception {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    createHoldingRecord(new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .create());

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(holdingsStorageUrl("?limit=-3"), TENANT_ID,
      ResponseHandler.text(getCompleted));

    Response response = getCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody().trim(),
      is("'limit' parameter is incorrect. parameter value {-3} is not valid: must be greater than or equal to 0"));
  }

  @Test
  public void cannotPageWithNegativeOffset() throws Exception {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    createHoldingRecord(new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID).create()).getId();

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(holdingsStorageUrl("?offset=-3"), TENANT_ID,
      ResponseHandler.text(getCompleted));

    Response response = getCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody().trim(),
      is("'offset' parameter is incorrect. parameter value {-3} is not valid: must be greater than or equal to 0"));
  }

  @Test
  public void canPageAllHoldings()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    createFiveHoldingsForPagingTest();

    var pages = fetchTwoPages();

    assertThat(pages[0].getStatusCode(), is(200));
    assertThat(pages[1].getStatusCode(), is(200));

    assertPageContents(pages[0].getJson(), 3, 5);
    assertPageContents(pages[1].getJson(), 2, 5);
  }

  private void createFiveHoldingsForPagingTest() {
    UUID firstInstanceId = UUID.randomUUID();
    UUID secondInstanceId = UUID.randomUUID();
    UUID thirdInstanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(firstInstanceId));
    instancesClient.create(nod(secondInstanceId));
    instancesClient.create(uprooted(thirdInstanceId));

    createSimpleHolding(firstInstanceId, MAIN_LIBRARY_LOCATION_ID);
    createSimpleHolding(secondInstanceId, ANNEX_LIBRARY_LOCATION_ID);
    createSimpleHolding(thirdInstanceId, MAIN_LIBRARY_LOCATION_ID);
    createSimpleHolding(secondInstanceId, MAIN_LIBRARY_LOCATION_ID);
    createSimpleHolding(firstInstanceId, ANNEX_LIBRARY_LOCATION_ID);
  }

  private void createSimpleHolding(UUID instanceId, UUID locationId) {
    createHoldingRecord(new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(locationId).create());
  }

  private Response[] fetchTwoPages()
      throws InterruptedException, ExecutionException, TimeoutException {
    var firstPageCompleted = new CompletableFuture<Response>();
    var secondPageCompleted = new CompletableFuture<Response>();

    getClient().get(holdingsStorageUrl("") + "?limit=3", TENANT_ID,
      ResponseHandler.json(firstPageCompleted));
    getClient().get(holdingsStorageUrl("") + "?limit=3&offset=3", TENANT_ID,
      ResponseHandler.json(secondPageCompleted));

    return new Response[] {
      firstPageCompleted.get(TIMEOUT, TimeUnit.SECONDS),
      secondPageCompleted.get(TIMEOUT, TimeUnit.SECONDS)
    };
  }

  private void assertPageContents(JsonObject page, int expectedSize, int expectedTotal) {
    var holdings = JsonArrayHelper.toList(page.getJsonArray("holdingsRecords"));
    assertThat(holdings.size(), is(expectedSize));
    assertThat(page.getInteger("totalRecords"), is(expectedTotal));
  }

  @Test
  @SneakyThrows
  public void canGetByInstanceId() {
    var instanceId = UUID.randomUUID();
    instancesClient.create(nod(instanceId));

    var holdingIds = createEightHoldingsForSorting(instanceId);
    Function<String, List<String>> getHoldingsIds = query -> holdingsClient.getByQuery(query).stream()
      .map(holding -> holding.getString("id")).toList();

    var query = buildSortQuery(instanceId, "effectiveLocation.name callNumberPrefix callNumber callNumberSuffix");
    var ids = getHoldingsIds.apply(query);
    assertThat(ids, is(List.of(holdingIds[0], holdingIds[1], holdingIds[2], holdingIds[3],
                                holdingIds[4], holdingIds[5], holdingIds[6], holdingIds[7])));

    ids = getHoldingsIds.apply(query + "&offset=2&limit=4");
    assertThat(ids, is(List.of(holdingIds[2], holdingIds[3], holdingIds[4], holdingIds[5])));

    query = buildSortQuery(instanceId, "callNumberSuffix callNumber callNumberPrefix effectiveLocation.name");
    ids = getHoldingsIds.apply(query);
    assertThat(ids, is(List.of(holdingIds[3], holdingIds[1], holdingIds[7], holdingIds[2],
                                holdingIds[6], holdingIds[0], holdingIds[5], holdingIds[4])));
  }

  private String[] createEightHoldingsForSorting(UUID instanceId) {
    Function4<UUID, String, String, String, String> createHolding =
      (location, prefix, callNumber, suffix) -> createHoldingRecord(new HoldingRequestBuilder()
        .forInstance(instanceId)
        .withSource(getPreparedHoldingSourceId())
        .withPermanentLocation(location)
        .withCallNumberPrefix(prefix)
        .withCallNumber(callNumber)
        .withCallNumberSuffix(suffix).create())
        .getId().toString();

    return new String[] {
      createHolding.invoke(ANNEX_LIBRARY_LOCATION_ID, "b", "k", "q"),  // h0
      createHolding.invoke(MAIN_LIBRARY_LOCATION_ID, "a", "j", "p"),   // h1
      createHolding.invoke(MAIN_LIBRARY_LOCATION_ID, "a", "j", "q"),   // h2
      createHolding.invoke(MAIN_LIBRARY_LOCATION_ID, "a", "k", "o"),   // h3
      createHolding.invoke(MAIN_LIBRARY_LOCATION_ID, "a", "l", "r"),   // h4
      createHolding.invoke(MAIN_LIBRARY_LOCATION_ID, "b", "k", "q"),   // h5
      createHolding.invoke(MAIN_LIBRARY_LOCATION_ID, "c", "j", "q"),   // h6
      createHolding.invoke(SECOND_FLOOR_LOCATION_ID, "b", "k", "p")    // h7
    };
  }

  private String buildSortQuery(UUID instanceId, String sortFields) {
    var query = "instanceId==" + instanceId + " sortBy " + sortFields;
    return "?query=" + PercentCodec.encode(query);
  }

  @Test
  public void canDeleteAllHoldings() {
    UUID firstInstanceId = UUID.randomUUID();
    UUID secondInstanceId = UUID.randomUUID();
    UUID thirdInstanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(firstInstanceId));
    instancesClient.create(nod(secondInstanceId));
    instancesClient.create(uprooted(thirdInstanceId));

    createHoldingRecord(new HoldingRequestBuilder()
      .forInstance(firstInstanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID).create());

    createHoldingRecord(new HoldingRequestBuilder()
      .forInstance(secondInstanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(ANNEX_LIBRARY_LOCATION_ID).create());

    createHoldingRecord(new HoldingRequestBuilder()
      .forInstance(thirdInstanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID).create());

    holdingsClient.deleteAll(Map.of(XOkapiHeaders.URL, mockServer.baseUrl()));

    List<JsonObject> allHoldings = holdingsClient.getAll();

    assertThat(allHoldings.size(), is(0));

    holdingsMessageChecks.allHoldingsDeletedMessagePublished();
  }

  @SneakyThrows
  @Test
  public void canDeleteHoldingsByCql() {
    var instanceId1 = UUID.randomUUID();
    var instanceId2 = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId1));
    instancesClient.create(nod(instanceId2));

    var holdings = createFiveHoldingsWithHrids(instanceId1, instanceId2);

    holdingsClient.deleteByQuery("hrid==12*", Map.of(XOkapiHeaders.URL, mockServer.baseUrl()));

    assertHoldingsExistence(holdings);
    assertDeletedMessagesPublished(holdings[0], holdings[2], holdings[4]);
  }

  private JsonObject[] createFiveHoldingsWithHrids(UUID instanceId1, UUID instanceId2) {
    return new JsonObject[] {
      createHoldingWithHrid(instanceId1, "1234"),
      createHoldingWithHrid(instanceId1, "21234"),
      createHoldingWithHrid(instanceId2, "12"),
      createHoldingWithHrid(instanceId2, "3123"),
      createHoldingWithHrid(instanceId2, "123")
    };
  }

  private void assertHoldingsExistence(JsonObject[] holdings) {
    assertExists(holdings[1]);  // h2
    assertExists(holdings[3]);  // h4
    assertNotExists(holdings[0]); // h1
    assertNotExists(holdings[2]); // h3
    assertNotExists(holdings[4]); // h5
  }

  private void assertDeletedMessagesPublished(JsonObject... deletedHoldings) {
    for (var holding : deletedHoldings) {
      holdingsMessageChecks.deletedMessagePublished(holding);
    }
  }

  @SneakyThrows
  @Test
  public void cannotDeleteHoldingsWithEmptyCql() {
    var response = getClient().delete(holdingsStorageUrl("?query="), TENANT_ID).get(10, SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), containsString("empty"));
  }

  @SneakyThrows
  @Test
  public void tenantIsRequiredForCreatingNewHolding() {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    JsonObject request = new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID).create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    getClient().post(holdingsStorageUrl(""), request, null, ResponseHandler.any(postCompleted));

    Response response = postCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @Test
  public void tenantIsRequiredForGettingHolding()
    throws InterruptedException,
    ExecutionException, TimeoutException {

    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID id = createHoldingRecord(new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID).create()).getId();

    URL getHoldingUrl = holdingsStorageUrl(String.format("/%s", id.toString()));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(getHoldingUrl, null, ResponseHandler.any(getCompleted));

    Response response = getCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @Test
  public void tenantIsRequiredForGettingAllHoldings() throws InterruptedException,
    ExecutionException, TimeoutException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(holdingsStorageUrl(""), null, ResponseHandler.any(getCompleted));

    Response response = getCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @Test
  public void optimisticLockingVersion() {
    UUID holdingId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    JsonObject holding = getById(holdingId.toString()).getJson();
    holding.put(PERMANENT_LOCATION_ID_KEY, ANNEX_LIBRARY_LOCATION_ID);
    holding.put("sourceId", getPreparedHoldingSourceId().toString());
    // updating with current _version 1 succeeds and increments _version to 2
    assertThat(update(holding).getStatusCode(), is(204));
    holding.put(PERMANENT_LOCATION_ID_KEY, SECOND_FLOOR_LOCATION_ID);
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
  public void shouldNotUpdateHoldingsIfNoChanges() {
    var holdingId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID).toString();
    var holding = getById(holdingId).getJson();
    holdingsMessageChecks.createdMessagePublished(holdingId);

    assertThat(update(holding).getStatusCode(), is(204));

    var updatedHolding = getById(holdingId).getJson();
    //assert that there was no update in database
    assertThat(updatedHolding.getString("_version"), is("1"));
    var kafkaEvents = KAFKA_CONSUMER.getMessagesForHoldings(holdingId);
    //assert that there's only CREATE kafka message, no updates
    assertThat(kafkaEvents.size(), is(1));
  }

  @Test
  public void updatingHoldingsWithSourceIdShouldUpdate()
    throws InterruptedException, ExecutionException, TimeoutException {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();
    JsonObject holding = createHoldingRecord(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withCallNumber("testCallNumber").create()).getJson();

    assertThat(holding.getString("callNumber"), is("testCallNumber"));

    UUID newSourceId = getPreparedHoldingSourceId();
    holding.put("sourceId", newSourceId.toString());
    holding.put("callNumber", "updatedTestCallNumber");
    URL holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));
    Response updateResponse = update(holdingsUrl, holding);
    Response updatedHolding = holdingsClient.getById(holdingId);
    assertThat(updatedHolding.getJson().getString("callNumber"), is("updatedTestCallNumber"));
    assertThat(updateResponse.getStatusCode(), is(204));
  }

  @Test
  public void updatingHoldingsWithoutSourceIdShouldNotUpdate()
    throws InterruptedException, ExecutionException, TimeoutException {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();
    JsonObject holding = createHoldingRecord(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withCallNumber("testCallNumber").create()).getJson();

    assertThat(holding.getString("callNumber"), is("testCallNumber"));

    holding.put("sourceId", null);
    holding.put("callNumber", "updatedTestCallNumber");
    URL holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));
    Response updateResponse = update(holdingsUrl, holding);
    Response updatedHolding = holdingsClient.getById(holdingId);
    assertThat(updatedHolding.getJson().getString("callNumber"), is("testCallNumber"));
    assertThat(updateResponse.getStatusCode(), is(422));
  }

  @Test
  public void updatingPermanentLocationChangesEffectiveLocationWhenNoTemporaryLocationSet()
    throws InterruptedException, ExecutionException, TimeoutException {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));
    setHoldingsSequence(1);

    UUID holdingId = UUID.randomUUID();
    JsonObject holding = createHoldingRecord(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE))).create()).getJson();

    assertThat(holding.getString("effectiveLocationId"), is(MAIN_LIBRARY_LOCATION_ID.toString()));

    holding.put("permanentLocationId", ANNEX_LIBRARY_LOCATION_ID.toString());
    URL holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));
    update(holdingsUrl, holding);
    Response updatedHolding = holdingsClient.getById(holdingId);
    assertThat(updatedHolding.getJson().getString("effectiveLocationId"), is(ANNEX_LIBRARY_LOCATION_ID.toString()));
  }

  @Test
  public void updatingPermanentLocationDoesNotChangeEffectiveLocationWhenTemporaryLocationSet()
    throws InterruptedException, ExecutionException, TimeoutException {
    UUID instanceId = UUID.randomUUID();
    UUID holdingId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));
    setHoldingsSequence(1);

    JsonObject holding = createHoldingRecord(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withTemporaryLocation(ANNEX_LIBRARY_LOCATION_ID)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE))).create()).getJson();

    assertThat(holding.getString("effectiveLocationId"), is(ANNEX_LIBRARY_LOCATION_ID.toString()));

    holding.put("permanentLocationId", SECOND_FLOOR_LOCATION_ID.toString());
    URL holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));
    update(holdingsUrl, holding);
    Response updatedHolding = holdingsClient.getById(holdingId);
    assertThat(updatedHolding.getJson().getString("effectiveLocationId"), is(ANNEX_LIBRARY_LOCATION_ID.toString()));
  }

  @Test
  public void updatingOrRemovingTemporaryLocationChangesEffectiveLocation()
    throws InterruptedException, ExecutionException, TimeoutException {
    var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));
    setHoldingsSequence(1);

    var holdingId = UUID.randomUUID();
    var holding = createHoldingRecord(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))
      .create()).getJson();

    assertThat(holding.getString("effectiveLocationId"), is(MAIN_LIBRARY_LOCATION_ID.toString()));

    holding = updateAndVerifyEffectiveLocation(holdingId, holding, "temporaryLocationId",
      ANNEX_LIBRARY_LOCATION_ID, ANNEX_LIBRARY_LOCATION_ID);
    holding = updateAndVerifyEffectiveLocation(holdingId, holding, "temporaryLocationId",
      SECOND_FLOOR_LOCATION_ID, SECOND_FLOOR_LOCATION_ID);
    updateRemoveAndVerifyEffectiveLocation(holdingId, holding, "temporaryLocationId", MAIN_LIBRARY_LOCATION_ID);
  }

  private JsonObject updateAndVerifyEffectiveLocation(UUID holdingId, JsonObject holding,
      String fieldKey, UUID fieldValue, UUID expectedEffectiveLocation)
      throws InterruptedException, ExecutionException, TimeoutException {
    var holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));
    holding.put(fieldKey, fieldValue.toString());
    update(holdingsUrl, holding);
    var updatedHolding = holdingsClient.getById(holdingId).getJson();
    assertThat(updatedHolding.getString("effectiveLocationId"), is(expectedEffectiveLocation.toString()));
    return updatedHolding;
  }

  private void updateRemoveAndVerifyEffectiveLocation(UUID holdingId, JsonObject holding,
      String fieldKey, UUID expectedEffectiveLocation)
      throws InterruptedException, ExecutionException, TimeoutException {
    var holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));
    holding.remove(fieldKey);
    update(holdingsUrl, holding);
    var updatedHolding = holdingsClient.getById(holdingId).getJson();
    assertThat(updatedHolding.getString("effectiveLocationId"), is(expectedEffectiveLocation.toString()));
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
    var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));
    var holdingId = UUID.randomUUID();

    final var holding = createHoldingWithCallNumber(instanceId, holdingId, "testCallNumber");
    var itemIds = createTwoComplexItems(holdingId, prefix, suffix, volume, enumeration, chronology, copy);

    assertItemsHaveCallNumber(itemIds, holdingId, "testCallNumber");

    updateHoldingCallNumberComponent(holding, holdingId, "callNumber", callNumber);

    assertItemsHaveCallNumber(itemIds, holdingId, callNumber);
  }

  private String[] createTwoComplexItems(UUID holdingId, String prefix, String suffix,
      String volume, String enumeration, String chronology, String copy)
      throws InterruptedException, ExecutionException, TimeoutException {
    var itemToCreate = buildComplexItemRequest(holdingId, prefix, suffix, volume,
      enumeration, chronology, copy);

    var first = create(itemsStorageUrl(""), itemToCreate);
    var second = create(itemsStorageUrl(""), itemToCreate);

    assertThat(first.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(second.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    return new String[] { first.getJson().getString("id"), second.getJson().getString("id") };
  }

  private JsonObject buildComplexItemRequest(UUID holdingId, String prefix, String suffix,
      String volume, String enumeration, String chronology, String copy) {
    var item = buildItemRequest(holdingId);
    item.put("itemLevelCallNumberSuffix", suffix);
    item.put("itemLevelCallNumberPrefix", prefix);
    item.put("itemLevelCallNumberTypeId", LC_CALL_NUMBER_TYPE);
    item.put("volume", volume);
    item.put("enumeration", enumeration);
    item.put("chronology", chronology);
    item.put("copyNumber", copy);
    return item;
  }

  private void assertItemsHaveCallNumber(String[] itemIds, UUID holdingId, String expectedCallNumber)
      throws InterruptedException, ExecutionException, TimeoutException {
    for (String itemId : itemIds) {
      URL itemUrl = itemsStorageUrl(String.format("/%s", itemId));
      Response response = get(itemUrl);
      JsonObject item = response.getJson();

      assertThat(item.getString("id"), is(itemId));
      assertThat(item.getString("holdingsRecordId"), is(holdingId.toString()));
      assertThat(
        item.getJsonObject("effectiveCallNumberComponents").getString("callNumber"),
        is(expectedCallNumber));
    }
  }

  @Test
  public void updatingHoldingsUpdatesItemEffectiveCallNumber()
    throws InterruptedException, ExecutionException, TimeoutException {

    var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));
    var holdingId = UUID.randomUUID();

    final var holding = createHoldingWithCallNumber(instanceId, holdingId, "testCallNumber");
    var itemIds = createTwoItemsForHolding(holdingId);

    assertItemsHaveCallNumber(itemIds, holdingId, "testCallNumber");

    updateHoldingCallNumberComponent(holding, holdingId, "callNumber", "updatedCallNumber");
    assertItemsHaveCallNumber(itemIds, holdingId, "updatedCallNumber");
  }

  @Test
  public void removingHoldingsCallNumberUpdatesItemEffectiveCallNumber()
    throws InterruptedException, ExecutionException, TimeoutException {
    var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));
    var holdingId = UUID.randomUUID();

    final JsonObject holding = createHoldingWithCallNumber(instanceId, holdingId, "testCallNumber");
    String itemId = createSingleItemForHolding(holdingId);

    assertItemHasCallNumberAndShelvingOrder(itemId, holdingId, "testCallNumber");

    removeHoldingCallNumberComponent(holding, holdingId, "callNumber");

    assertItemMissingCallNumberAndShelvingOrder(itemId);
  }

  private void assertItemHasCallNumberAndShelvingOrder(String itemId, UUID holdingId, String expectedCallNumber)
      throws InterruptedException, ExecutionException, TimeoutException {
    var itemUrl = itemsStorageUrl(String.format("/%s", itemId));
    var response = get(itemUrl);
    var item = response.getJson();

    assertThat(item.getString("id"), is(itemId));
    assertThat(item.getString("holdingsRecordId"), is(holdingId.toString()));
    assertThat(
      item.getJsonObject("effectiveCallNumberComponents").getString("callNumber"),
      is(expectedCallNumber));
    assertThat(item.getString("effectiveShelvingOrder"), is(expectedCallNumber));
  }

  private void assertItemMissingCallNumberAndShelvingOrder(String itemId)
      throws InterruptedException, ExecutionException, TimeoutException {
    var itemUrl = itemsStorageUrl(String.format("/%s", itemId));
    var response = get(itemUrl);
    var item = response.getJson();

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(item.getString("id"), is(itemId));
    assertThat(
      item.getJsonObject("effectiveCallNumberComponents").containsKey("callNumber"),
      is(false));
    assertThat(item.containsKey("effectiveShelvingOrder"), is(false));
  }

  @Test
  public void holdingsCallNumberDoesNotSupersedeItemLevelCallNumber()
    throws InterruptedException, TimeoutException, ExecutionException {
    var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));
    var holdingId = UUID.randomUUID();

    final var holding = createHoldingWithCallNumber(instanceId, holdingId, "holdingsCallNumber");
    var itemId = createItemWithCallNumberComponent(holdingId, "itemLevelCallNumber", "itemLevelCallNumber");

    assertItemHasCallNumberComponent(itemId, holdingId, "callNumber", "itemLevelCallNumber");

    updateHoldingCallNumberComponent(holding, holdingId, "callNumber", "updatedHoldingCallNumber");

    assertItemHasCallNumberComponent(itemId, holdingId, "callNumber", "itemLevelCallNumber");
  }

  @Test
  public void updateHoldingsCallNumberUpdatesItemLevelMetadata()
    throws InterruptedException, TimeoutException, ExecutionException {
    var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));
    var holdingId = UUID.randomUUID();

    final var holding = createHoldingWithoutCallNumber(instanceId, holdingId);
    var itemId = createSingleItemForHolding(holdingId);

    var originalUpdatedDate = getItemMetadataUpdatedDate(itemId);

    updateHoldingCallNumberComponent(holding, holdingId, "callNumber", "updatedHoldingCallNumber");

    assertItemMetadataChanged(itemId, originalUpdatedDate);
  }

  private JsonObject createHoldingWithoutCallNumber(UUID instanceId, UUID holdingId) {
    return createHoldingRecord(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE))).create()).getJson();
  }

  private String getItemMetadataUpdatedDate(String itemId)
      throws InterruptedException, ExecutionException, TimeoutException {
    URL itemUrl = itemsStorageUrl(String.format("/%s", itemId));
    Response response = get(itemUrl);
    JsonObject item = response.getJson();

    assertThat(item.getString("id"), is(itemId));
    return item.getJsonObject("metadata").getString("updatedDate");
  }

  private void assertItemMetadataChanged(String itemId, String originalUpdatedDate)
      throws InterruptedException, ExecutionException, TimeoutException {
    var itemUrl = itemsStorageUrl(String.format("/%s", itemId));
    var response = get(itemUrl);
    var item = response.getJson();

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(item.getString("id"), is(itemId));
    assertNotEquals(
      item.getJsonObject("metadata").getString("updatedDate"),
      originalUpdatedDate);
  }

  @Test
  public void updateHoldingsLocationUpdatesItemLevelMetadata()
    throws InterruptedException, TimeoutException, ExecutionException {
    var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));
    var holdingId = UUID.randomUUID();

    final var holding = createHoldingWithCallNumber(instanceId, holdingId, "holdingsCallNumber");
    var itemId = createItemWithCallNumberComponent(holdingId, "itemLevelCallNumber", "itemLevelCallNumber");

    var originalUpdatedDate = getItemMetadataUpdatedDate(itemId);

    updateHoldingLocation(holding, holdingId);
    assertItemMetadataChanged(itemId, originalUpdatedDate);
  }

  private void updateHoldingLocation(JsonObject holding, UUID holdingId)
      throws InterruptedException, ExecutionException, TimeoutException {
    var holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));
    holding.put("temporaryLocationId", ONLINE_LOCATION_ID.toString());
    var putResponse = update(holdingsUrl, holding);
    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  @Test
  public void updateHoldingsFieldsNotRelatedToItemShouldNotChangeItemMetadata()
    throws InterruptedException, TimeoutException, ExecutionException {
    var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));
    var holdingId = UUID.randomUUID();

    final var holding = createHoldingWithCallNumber(instanceId, holdingId, "holdingsCallNumber");
    var itemId = createItemWithPermanentLocation(holdingId);

    var originalUpdatedDate = getItemMetadataUpdatedDate(itemId);

    updateHoldingTags(holding, holdingId);

    assertItemMetadataUnchanged(itemId, originalUpdatedDate);
  }

  private String createItemWithPermanentLocation(UUID holdingId)
      throws InterruptedException, ExecutionException, TimeoutException {
    var itemToCreate = buildItemRequest(holdingId);
    itemToCreate.put("permanentLocationId", ANNEX_LIBRARY_LOCATION_ID.toString());
    itemToCreate.put("itemLevelCallNumber", "itemLevelCallNumber");

    var response = create(itemsStorageUrl(""), itemToCreate);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    return response.getJson().getString("id");
  }

  private void updateHoldingTags(JsonObject holding, UUID holdingId)
      throws InterruptedException, ExecutionException, TimeoutException {
    var holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));
    holding.put("tags", new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)));
    var putResponse = update(holdingsUrl, holding);
    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  private void assertItemMetadataUnchanged(String itemId, String originalUpdatedDate)
      throws InterruptedException, ExecutionException, TimeoutException {
    var itemUrl = itemsStorageUrl(String.format("/%s", itemId));
    var response = get(itemUrl);
    var item = response.getJson();

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(item.getString("id"), is(itemId));
    assertEquals(
      item.getJsonObject("metadata").getString("updatedDate"),
      originalUpdatedDate);
  }

  @Test
  public void creatingHoldingsLimitAdministrativeNoteMaximumLength()
    throws ExecutionException, InterruptedException, TimeoutException {

    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();

    final JsonObject invalidHolding = new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withAdministrativeNotes(List.of("x".repeat(MAX_NOTE_LENGTH + 1)))
      .create();

    final CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(holdingsStorageUrl(""), invalidHolding, TENANT_ID, ResponseHandler.json(createCompleted));

    final Response response = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(422));
  }

  @Test
  public void creatingHoldingsLimitNoteMaximumLength()
    throws ExecutionException, InterruptedException, TimeoutException {

    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();

    final JsonObject invalidHolding = new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .create();

    invalidHolding.put("notes", new JsonArray().add(new Note().withNote("x".repeat(MAX_NOTE_LENGTH + 1))));

    final CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(holdingsStorageUrl(""), invalidHolding, TENANT_ID, ResponseHandler.json(createCompleted));

    final Response response = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(422));
  }

  @Test
  public void updatingHoldingsLimitAdministrativeNoteMaximumLength()
    throws ExecutionException, InterruptedException, TimeoutException {

    UUID instanceId = UUID.randomUUID();
    UUID holdingId = UUID.randomUUID();
    final URL holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));

    instancesClient.create(smallAngryPlanet(instanceId));
    setHoldingsSequence(1);

    JsonObject holding = createHoldingRecord(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID).create()).getJson();

    holding.put("administrativeNotes", new JsonArray().add("x".repeat(MAX_NOTE_LENGTH + 1)));
    Response response = update(holdingsUrl, holding);

    assertThat(response.getStatusCode(), is(422));
  }

  @Test
  public void updatingHoldingsLimitNoteMaximumLength()
    throws ExecutionException, InterruptedException, TimeoutException {

    UUID instanceId = UUID.randomUUID();
    UUID holdingId = UUID.randomUUID();
    final URL holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));

    instancesClient.create(smallAngryPlanet(instanceId));
    setHoldingsSequence(1);

    JsonObject holding = createHoldingRecord(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID).create()).getJson();

    holding.put("notes", new JsonArray().add(new Note().withNote("x".repeat(MAX_NOTE_LENGTH + 1))));
    Response response = update(holdingsUrl, holding);

    assertThat(response.getStatusCode(), is(422));
  }

  @Test
  public void updatingHoldingsDoesNotUpdateItemsOnAnotherHoldings()
    throws ExecutionException,
    InterruptedException, TimeoutException {

    var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));

    var firstHoldings = UUID.randomUUID();
    var secondHoldings = UUID.randomUUID();

    final var firstHolding = createHoldingWithAllCallNumberComponents(instanceId, firstHoldings,
      "firstTestCallNumber", "firstTestCallNumberPrefix", "firstTestCallNumberSuffix");
    createHoldingWithAllCallNumberComponents(instanceId, secondHoldings,
      "secondTestCallNumber", "secondTestCallNumberPrefix", "secondTestCallNumberSuffix");

    var itemIds = createItemsForTwoHoldings(firstHoldings, secondHoldings);

    assertItemsHaveExpectedCallNumbers(itemIds[0],
      "firstTestCallNumber", "firstTestCallNumberPrefix", "firstTestCallNumberSuffix");
    assertItemsHaveExpectedCallNumbers(itemIds[1],
      "secondTestCallNumber", "secondTestCallNumberPrefix", "secondTestCallNumberSuffix");

    updateFirstHoldingCallNumbers(firstHolding, firstHoldings);

    assertItemsHaveExpectedCallNumbers(itemIds[0],
      "updatedFirstCallNumber", "updatedFirstCallNumberPrefix", "updatedFirstCallNumberSuffix");
    assertItemsHaveExpectedCallNumbers(itemIds[1],
      "secondTestCallNumber", "secondTestCallNumberPrefix", "secondTestCallNumberSuffix");
  }

  private JsonObject createHoldingWithAllCallNumberComponents(UUID instanceId, UUID holdingId,
                                                               String callNumber, String prefix, String suffix) {
    return createHoldingRecord(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withCallNumber(callNumber)
      .withCallNumberPrefix(prefix)
      .withCallNumberSuffix(suffix)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE))).create()).getJson();
  }

  private String[] createItemsForTwoHoldings(UUID firstHoldings, UUID secondHoldings)
      throws InterruptedException, ExecutionException, TimeoutException {
    var firstItemResponse = create(itemsStorageUrl(""), new ItemRequestBuilder()
      .forHolding(firstHoldings)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withMaterialType(bookMaterialTypeId)
      .create());
    var secondItemResponse = create(itemsStorageUrl(""), new ItemRequestBuilder()
      .forHolding(secondHoldings)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withMaterialType(bookMaterialTypeId)
      .create());

    assertThat(firstItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(secondItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    return new String[] {
      firstItemResponse.getJson().getString("id"),
      secondItemResponse.getJson().getString("id")
    };
  }

  private void assertItemsHaveExpectedCallNumbers(String itemId, String callNumber, String prefix, String suffix)
      throws InterruptedException, ExecutionException, TimeoutException {
    var itemUrl = itemsStorageUrl(String.format("/%s", itemId));
    var response = get(itemUrl);
    var item = response.getJson();

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(item.getString("id"), is(itemId));
    assertThat(
      item.getJsonObject("effectiveCallNumberComponents").getString("callNumber"),
      is(callNumber));
    assertThat(
      item.getJsonObject("effectiveCallNumberComponents").getString("prefix"),
      is(prefix));
    assertThat(
      item.getJsonObject("effectiveCallNumberComponents").getString("suffix"),
      is(suffix));
  }

  private void updateFirstHoldingCallNumbers(JsonObject firstHolding, UUID firstHoldings)
      throws InterruptedException, ExecutionException, TimeoutException {
    var firstHoldingsUrl = holdingsStorageUrl(String.format("/%s", firstHoldings));
    firstHolding.put("callNumber", "updatedFirstCallNumber");
    firstHolding.put("callNumberPrefix", "updatedFirstCallNumberPrefix");
    firstHolding.put("callNumberSuffix", "updatedFirstCallNumberSuffix");

    var putResponse = update(firstHoldingsUrl, firstHolding);
    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    var updatedFirstHoldingResponse = get(firstHoldingsUrl);
    var updatedFirstHolding = updatedFirstHoldingResponse.getJson();
    assertThat(updatedFirstHolding.getString("callNumber"), is("updatedFirstCallNumber"));
  }

  @Test
  public void updatingHoldingsUpdatesItemEffectiveCallNumberSuffix()
    throws InterruptedException,
    ExecutionException, TimeoutException {

    var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));
    var holdingId = UUID.randomUUID();

    final var holding = createHoldingWithCallNumberComponent(instanceId, holdingId,
      "callNumberSuffix", "testCallNumberSuffix");

    var itemIds = createTwoItemsForHolding(holdingId);

    assertItemsHaveCallNumberComponent(itemIds, holdingId, "suffix", "testCallNumberSuffix");

    updateHoldingCallNumberComponent(holding, holdingId, "callNumberSuffix", "updatedCallNumberSuffix");

    assertItemsHaveCallNumberComponent(itemIds, holdingId, "suffix", "updatedCallNumberSuffix");
  }

  private JsonObject createHoldingWithCallNumberComponent(UUID instanceId, UUID holdingId,
                                                           String componentKey, String componentValue) {
    var builder = new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)));

    if ("callNumberSuffix".equals(componentKey)) {
      builder.withCallNumberSuffix(componentValue);
    } else if ("callNumberPrefix".equals(componentKey)) {
      builder.withCallNumberPrefix(componentValue);
    }

    return createHoldingRecord(builder.create()).getJson();
  }

  private String[] createTwoItemsForHolding(UUID holdingId)
      throws InterruptedException, ExecutionException, TimeoutException {
    var itemToCreate = buildItemRequest(holdingId);
    var firstResponse = create(itemsStorageUrl(""), itemToCreate);
    var secondResponse = create(itemsStorageUrl(""), itemToCreate);

    assertThat(firstResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(secondResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    return new String[] {
      firstResponse.getJson().getString("id"),
      secondResponse.getJson().getString("id")
    };
  }

  private JsonObject buildItemRequest(UUID holdingId) {
    var itemToCreate = new JsonObject();
    itemToCreate.put("holdingsRecordId", holdingId.toString());
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", ANNEX_LIBRARY_LOCATION_ID.toString());
    itemToCreate.put("materialTypeId", bookMaterialTypeID);
    return itemToCreate;
  }

  private void assertItemsHaveCallNumberComponent(String[] itemIds, UUID holdingId,
                                                   String componentName, String expectedValue)
      throws InterruptedException, ExecutionException, TimeoutException {
    for (String itemId : itemIds) {
      var itemUrl = itemsStorageUrl(String.format("/%s", itemId));
      var response = get(itemUrl);
      var item = response.getJson();

      assertThat(item.getString("id"), is(itemId));
      assertThat(item.getString("holdingsRecordId"), is(holdingId.toString()));
      assertThat(
        item.getJsonObject("effectiveCallNumberComponents").getString(componentName),
        is(expectedValue));
    }
  }

  private void updateHoldingCallNumberComponent(JsonObject holding, UUID holdingId,
                                                 String componentKey, String newValue)
      throws InterruptedException, ExecutionException, TimeoutException {
    var holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));
    holding.put(componentKey, newValue);
    var putResponse = update(holdingsUrl, holding);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    assertThat(holding.getString(componentKey), is(newValue));
  }

  @Test
  public void removingHoldingsCallNumberSuffixUpdatesItemEffectiveCallNumberSuffix()
    throws InterruptedException, ExecutionException, TimeoutException {
    var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));
    var holdingId = UUID.randomUUID();

    final var holding = createHoldingWithCallNumberComponent(instanceId, holdingId,
      "callNumberSuffix", "testCallNumberSuffix");

    var itemId = createSingleItemForHolding(holdingId);

    assertItemHasCallNumberComponent(itemId, holdingId, "suffix", "testCallNumberSuffix");

    removeHoldingCallNumberComponent(holding, holdingId, "callNumberSuffix");

    assertItemMissingCallNumberComponent(itemId, "suffix");
  }

  private String createSingleItemForHolding(UUID holdingId)
      throws InterruptedException, ExecutionException, TimeoutException {
    var itemToCreate = buildItemRequest(holdingId);
    var response = create(itemsStorageUrl(""), itemToCreate);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    return response.getJson().getString("id");
  }

  private void assertItemHasCallNumberComponent(String itemId, UUID holdingId,
                                                 String componentName, String expectedValue)
      throws InterruptedException, ExecutionException, TimeoutException {
    var itemUrl = itemsStorageUrl(String.format("/%s", itemId));
    var response = get(itemUrl);
    var item = response.getJson();

    assertThat(item.getString("id"), is(itemId));
    assertThat(item.getString("holdingsRecordId"), is(holdingId.toString()));
    assertThat(
      item.getJsonObject("effectiveCallNumberComponents").getString(componentName),
      is(expectedValue));
  }

  private void removeHoldingCallNumberComponent(JsonObject holding, UUID holdingId, String componentKey)
      throws InterruptedException, ExecutionException, TimeoutException {
    var holdingsUrl = holdingsStorageUrl(String.format("/%s", holdingId));
    holding.remove(componentKey);
    var putResponse = update(holdingsUrl, holding);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    assertThat(holding.containsKey(componentKey), is(false));
  }

  private void assertItemMissingCallNumberComponent(String itemId, String componentName)
      throws InterruptedException, ExecutionException, TimeoutException {
    var itemUrl = itemsStorageUrl(String.format("/%s", itemId));
    var response = get(itemUrl);
    var item = response.getJson();

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(item.getString("id"), is(itemId));
    assertThat(
      item.getJsonObject("effectiveCallNumberComponents").containsKey(componentName),
      is(false));
  }

  @Test
  public void holdingsCallNumberSuffixDoesNotSupersedeItemLevelCallNumberSuffix()
    throws InterruptedException, TimeoutException, ExecutionException {
    var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));
    var holdingId = UUID.randomUUID();

    final var holding = createHoldingWithCallNumberComponent(instanceId, holdingId,
      "callNumberSuffix", "holdingsCallNumberSuffix");

    var itemId = createItemWithCallNumberComponent(holdingId, "itemLevelCallNumberSuffix", "itemLevelCallNumberSuffix");
    assertItemHasCallNumberComponent(itemId, holdingId, "suffix", "itemLevelCallNumberSuffix");

    updateHoldingCallNumberComponent(holding, holdingId, "callNumberSuffix", "updatedHoldingCallNumberSuffix");
    assertItemHasCallNumberComponent(itemId, holdingId, "suffix", "itemLevelCallNumberSuffix");
  }

  private String createItemWithCallNumberComponent(UUID holdingId, String componentKey, String componentValue)
      throws InterruptedException, ExecutionException, TimeoutException {
    var itemToCreate = buildItemRequest(holdingId);
    itemToCreate.put(componentKey, componentValue);

    var response = create(itemsStorageUrl(""), itemToCreate);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    return response.getJson().getString("id");
  }

  @Test
  public void updatingHoldingsUpdatesItemEffectiveCallNumberPrefix()
    throws InterruptedException,
    ExecutionException, TimeoutException {

    var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));
    var holdingId = UUID.randomUUID();

    final var holding = createHoldingWithCallNumberComponent(instanceId, holdingId,
      "callNumberPrefix", "testCallNumberPrefix");

    var itemIds = createTwoItemsForHolding(holdingId);

    assertItemsHaveCallNumberComponent(itemIds, holdingId, "prefix", "testCallNumberPrefix");

    updateHoldingCallNumberComponent(holding, holdingId, "callNumberPrefix", "updatedCallNumberPrefix");

    assertItemsHaveCallNumberComponent(itemIds, holdingId, "prefix", "updatedCallNumberPrefix");
  }

  @Test
  public void removingHoldingsCallNumberPrefixUpdatesItemEffectiveCallNumberPrefix()
    throws InterruptedException, ExecutionException, TimeoutException {
    var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));
    var holdingId = UUID.randomUUID();

    final var holding = createHoldingWithCallNumberComponent(instanceId, holdingId,
      "callNumberPrefix", "testCallNumberPrefix");

    var itemId = createSingleItemForHolding(holdingId);

    assertItemHasCallNumberComponent(itemId, holdingId, "prefix", "testCallNumberPrefix");

    removeHoldingCallNumberComponent(holding, holdingId, "callNumberPrefix");

    assertItemMissingCallNumberComponent(itemId, "prefix");
  }

  @Test
  public void holdingsCallNumberPrefixDoesNotSupersedeItemLevelCallNumberPrefix()
    throws InterruptedException, TimeoutException, ExecutionException {
    var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));
    var holdingId = UUID.randomUUID();

    final var holding = createHoldingWithCallNumberComponent(instanceId, holdingId,
      "callNumberPrefix", "holdingsCallNumberPrefix");

    var itemId = createItemWithCallNumberComponent(holdingId, "itemLevelCallNumberPrefix", "itemLevelCallNumberPrefix");

    assertItemHasCallNumberComponent(itemId, holdingId, "prefix", "itemLevelCallNumberPrefix");

    updateHoldingCallNumberComponent(holding, holdingId, "callNumberPrefix", "updatedHoldingCallNumberPrefix");

    assertItemHasCallNumberComponent(itemId, holdingId, "prefix", "itemLevelCallNumberPrefix");
  }

  @Test
  public void cannotCreateHoldingWithoutPermanentLocation() throws Exception {
    UUID instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));

    JsonObject holdingsRecord = new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
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
  public void canCreateHoldingsWhenHridIsSupplied() {
    log.info("Starting canCreateAHoldingsWhenHRIDIsSupplied");

    final UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    final UUID holdingsId = UUID.randomUUID();

    final String hrid = "TEST1001";

    final JsonObject holdings = createHoldingRecord(new HoldingRequestBuilder()
      .withId(holdingsId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withHrid(hrid)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE))).create()).getJson();

    assertThat(holdings.getString("hrid"), is(hrid));

    final Response getResponse = holdingsClient.getById(holdingsId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    final JsonObject holdingsFromGet = getResponse.getJson();

    assertThat(holdingsFromGet.getString("hrid"), is(hrid));

    log.info("Finished canCreateAHoldingsWhenHRIDIsSupplied");
  }

  @Test
  public void cannotCreateHoldingsWhenDuplicateHridIsSupplied()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {
    log.info("Starting cannotCreateAHoldingsWhenDuplicateHRIDIsSupplied");

    final var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));
    final var holdingsId = UUID.randomUUID();
    setHoldingsSequence(1);

    final var sourceId = getPreparedHoldingSourceId();
    final var holdings = createHoldingWithHrid(instanceId, holdingsId, sourceId);

    assertThat(holdings.getString("hrid"), is("ho00000000001"));
    assertHoldingExistsWithHrid(holdingsId, "ho00000000001");

    final var duplicateHoldings = buildDuplicateHoldingRequest(instanceId, sourceId, "ho00000000001");
    final var duplicateResponse = create(holdingsStorageUrl(""), duplicateHoldings);

    assertDuplicateHridError(duplicateResponse, "ho00000000001");

    log.info("Finished cannotCreateAHoldingsWhenDuplicateHRIDIsSupplied");
  }

  private JsonObject createHoldingWithHrid(UUID instanceId, String hrid) {
    return createHoldingRecord(new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withHrid(hrid)
      .create()).getJson();
  }

  private JsonObject createHoldingWithHrid(UUID instanceId, UUID holdingsId, UUID sourceId) {
    return createHoldingRecord(new HoldingRequestBuilder()
      .withId(holdingsId)
      .forInstance(instanceId)
      .withSource(sourceId)
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE))).create()).getJson();
  }

  private void assertHoldingExistsWithHrid(UUID holdingsId, String expectedHrid) {
    final var getResponse = holdingsClient.getById(holdingsId);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    final var holdingsFromGet = getResponse.getJson();
    assertThat(holdingsFromGet.getString("hrid"), is(expectedHrid));
  }

  private JsonObject buildDuplicateHoldingRequest(UUID instanceId, UUID sourceId, String hrid) {
    return new HoldingRequestBuilder()
      .withId(UUID.randomUUID())
      .forInstance(instanceId)
      .withSource(sourceId)
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))
      .withHrid(hrid)
      .create();
  }

  private void assertDuplicateHridError(Response response, String hrid) {
    assertThat(response.getStatusCode(), is(HTTP_UNPROCESSABLE_ENTITY.toInt()));
    final var errors = response.getJson().mapTo(Errors.class);
    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), notNullValue());
    assertThat(errors.getErrors().size(), is(1));
    assertThat(errors.getErrors().getFirst(), notNullValue());
    assertThat(errors.getErrors().getFirst().getMessage(),
      containsString("HRID value already exists in table holdings_record: " + hrid));
    assertThat(errors.getErrors().getFirst().getParameters(), notNullValue());
    assertThat(errors.getErrors().getFirst().getParameters().size(), is(1));
    assertThat(errors.getErrors().getFirst().getParameters().getFirst(), notNullValue());
    assertThat(errors.getErrors().getFirst().getParameters().getFirst().getKey(),
      is("lower(f_unaccent(jsonb ->> 'hrid'::text))"));
    assertThat(errors.getErrors().getFirst().getParameters().getFirst().getValue(),
      is(hrid));
  }

  @Test
  @SneakyThrows
  public void cannotCreateHoldingsWithHridFailure() {
    final UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    setHoldingsSequence(99_999_999_999L);

    final JsonObject goodHholdings = createHoldingRecord(new HoldingRequestBuilder()
      .withId(UUID.randomUUID())
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE))).create()).getJson();

    assertThat(goodHholdings.getString("hrid"), is("ho99999999999"));

    final JsonObject badHoldings = new HoldingRequestBuilder()
      .withId(UUID.randomUUID())
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))
      .create();

    final CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(holdingsStorageUrl(""), badHoldings, TENANT_ID, text(createCompleted));

    final Response response = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_INTERNAL_ERROR));
    assertThat(response.getBody(), isMaximumSequenceValueError("hrid_holdings_seq"));
  }

  @Test
  public void cannotPostSynchronousBatchWithInvalidStatisticalCodeIds() {

    final JsonArray holdingsArray = threeHoldings();
    var invalidHolding = holdingsArray.getJsonObject(1);
    invalidHolding.put(STATISTICAL_CODE_IDS_KEY, Set.of(INVALID_VALUE));

    var response = postSynchronousBatch(holdingsArray);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), containsString(INVALID_TYPE_ERROR_MESSAGE));
  }

  @Test
  @SneakyThrows
  public void cannotCreateHoldingsWhenAlreadyAllocatedHridIsAllocated() {
    final var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));

    setHoldingsSequence(1000L);

    final var holdingsRequest = new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .create();

    // Allocate the HRID
    final var firstAllocation = createHoldingRecord(holdingsRequest).getJson();
    assertThat(firstAllocation.getString("hrid"), is("ho00000001000"));

    // Reset the sequence and attempt second allocation
    setHoldingsSequence(1000L);
    final var response = attemptToCreateHolding(holdingsRequest);

    assertHridAllocationError(response, "ho00000001000");
  }

  private Response attemptToCreateHolding(JsonObject holdingsRequest)
      throws InterruptedException, ExecutionException, TimeoutException {
    final var createCompleted = new CompletableFuture<Response>();
    getClient().post(holdingsStorageUrl(""), holdingsRequest, TENANT_ID, json(createCompleted));
    return createCompleted.get(TIMEOUT, TimeUnit.SECONDS);
  }

  private void assertHridAllocationError(Response response, String hrid) {
    assertThat(response.getStatusCode(), is(422));
    final var errors = response.getJson().mapTo(Errors.class);
    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), notNullValue());
    assertThat(errors.getErrors().getFirst(), notNullValue());
    assertThat(errors.getErrors().getFirst().getMessage(),
      is("HRID value already exists in table holdings_record: " + hrid));
    assertThat(errors.getErrors().getFirst().getParameters(), notNullValue());
    assertThat(errors.getErrors().getFirst().getParameters().getFirst(), notNullValue());
    assertThat(errors.getErrors().getFirst().getParameters().getFirst().getKey(),
      is("lower(f_unaccent(jsonb ->> 'hrid'::text))"));
    assertThat(errors.getErrors().getFirst().getParameters().getFirst().getValue(),
      is(hrid));
  }

  @Test
  public void cannotChangeHridAfterCreation()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {
    log.info("Starting cannotChangeHRIDAfterCreation");

    final UUID instanceId = UUID.randomUUID();
    final UUID holdingsId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));

    setHoldingsSequence(1);

    final JsonObject holdings = createHoldingRecord(new HoldingRequestBuilder()
      .withId(holdingsId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE))).create()).getJson();

    assertThat(holdings.getString("hrid"), is("ho00000000001"));

    holdings.put("hrid", "ABC123");

    final CompletableFuture<Response> updateCompleted = new CompletableFuture<>();

    getClient().put(holdingsStorageUrl(String.format("/%s", holdingsId)), holdings, TENANT_ID,
      text(updateCompleted));

    final Response response = updateCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    assertThat(response.getBody(),
      is("The hrid field cannot be changed: new=ABC123, old=ho00000000001"));

    log.info("Finished cannotChangeHRIDAfterCreation");
  }

  @Test
  public void cannotRemoveHridAfterCreation()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {
    log.info("Starting cannotRemoveHRIDAfterCreation");

    final UUID instanceId = UUID.randomUUID();
    final UUID holdingsId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));

    setHoldingsSequence(1);

    final JsonObject holdings = createHoldingRecord(new HoldingRequestBuilder()
      .withId(holdingsId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE))).create()).getJson();

    assertThat(holdings.getString("hrid"), is("ho00000000001"));

    holdings.remove("hrid");

    final CompletableFuture<Response> updateCompleted = new CompletableFuture<>();

    getClient().put(holdingsStorageUrl(String.format("/%s", holdingsId)), holdings, TENANT_ID,
      text(updateCompleted));

    final Response response = updateCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    assertThat(response.getBody(),
      is("The hrid field cannot be changed: new=null, old=ho00000000001"));

    log.info("Finished cannotRemoveHRIDAfterCreation");
  }

  @Test
  public void canUsePutToCreateHoldingsWhenHridIsSupplied() {
    log.info("Starting canUsePutToCreateAHoldingsWhenHRIDIsSupplied");

    final UUID instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));

    final UUID holdingsId = UUID.randomUUID();

    final String hrid = "TEST1001";

    updateHoldingRecord(holdingsId, new HoldingRequestBuilder()
      .withId(holdingsId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withHrid(hrid)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE))));

    final Response getResponse = holdingsClient.getById(holdingsId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    final JsonObject holdingsFromGet = getResponse.getJson();

    assertThat(holdingsFromGet.getString("hrid"), is(hrid));

    // Make sure a create event published vs update event
    holdingsMessageChecks.createdMessagePublished(holdingsFromGet, TENANT_ID, mockServer.baseUrl());
    holdingsMessageChecks.noHoldingsUpdatedMessagePublished(holdingsId.toString());

    log.info("Finished canUsePutToCreateAHoldingsWhenHRIDIsSupplied");
  }

  @Test
  public void canCreateHoldingAndCreateShadowInstance() {
    log.info("Starting canCreateHoldingAndCreateShadowInstance");
    mockSharingInstance();

    UUID instanceId = UUID.randomUUID();
    HoldingRequestBuilder builder = new HoldingRequestBuilder()
      .withId(null)
      .forInstance(instanceId)
      .withSource(HOLDING_SOURCE_IDS[0])
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID);

    JsonObject holding = createHoldingRecord(builder.create(), CONSORTIUM_MEMBER_TENANT).getJson();

    assertThat(holding.getString("id"), is(notNullValue()));
    assertThat(holding.getString("instanceId"), is(instanceId.toString()));
    assertThat(holding.getString("permanentLocationId"), is(MAIN_LIBRARY_LOCATION_ID.toString()));
    assertExists(holding, CONSORTIUM_MEMBER_TENANT);

    holdingsMessageChecks.createdMessagePublished(
      getById(holding.getString("id"), CONSORTIUM_MEMBER_TENANT).getJson(),
      CONSORTIUM_MEMBER_TENANT, mockServer.baseUrl());

    log.info("Finished canCreateHoldingAndCreateShadowInstance");
  }

  @Test
  public void canPostSynchronousBatchWithGeneratedHrid() {
    log.info("Starting canPostSynchronousBatchWithGeneratedHRID");

    setHoldingsSequence(1);

    final JsonArray holdingsArray = threeHoldings();

    assertThat(postSynchronousBatch(holdingsArray), statusCodeIs(HTTP_CREATED));

    holdingsArray.stream()
      .map(o -> (JsonObject) o)
      .forEach(holdings -> {
        final Response response = getById(holdings.getString("id"));
        assertExists(response, holdings);
        assertHridRange(response, "ho00000000001", "ho00000000003");
        JsonObject body = response.getJson();
        assertEquals(body.getString("effectiveLocationId"), body.getString("permanentLocationId"));
      });

    log.info("Finished canPostSynchronousBatchWithGeneratedHRID");
  }

  @Test
  public void canPostSynchronousBatchWithSuppliedAndGeneratedHrid() {
    log.info("Starting canPostSynchronousBatchWithSuppliedAndGeneratedHRID");

    setHoldingsSequence(1);

    final String hrid = "ABC123";
    final JsonArray holdingsArray = threeHoldings();

    holdingsArray.getJsonObject(1).put("hrid", hrid);

    assertThat(postSynchronousBatch(holdingsArray), statusCodeIs(HTTP_CREATED));

    Response response = getById(holdingsArray.getJsonObject(0).getString("id"));
    assertExists(response, holdingsArray.getJsonObject(0));
    assertHridRange(response, "ho00000000001", "ho00000000002");

    response = getById(holdingsArray.getJsonObject(1).getString("id"));
    assertExists(response, holdingsArray.getJsonObject(1));
    assertThat(response.getJson().getString("hrid"), is(hrid));

    response = getById(holdingsArray.getJsonObject(2).getString("id"));
    assertExists(response, holdingsArray.getJsonObject(2));
    assertHridRange(response, "ho00000000001", "ho00000000002");

    log.info("Finished canPostSynchronousBatchWithSuppliedAndGeneratedHRID");
  }

  @Test
  public void cannotPostSynchronousBatchWithDuplicateHrids() {
    log.info("Starting cannotPostSynchronousBatchWithDuplicateHRIDs");

    setHoldingsSequence(1);

    final var holdingsArray = threeHoldings();
    final var duplicateHrid = "ho00000000001";
    holdingsArray.getJsonObject(1).put("hrid", duplicateHrid);

    var response = postSynchronousBatch(holdingsArray);

    assertBatchDuplicateHridError(response, duplicateHrid);
    assertAllHoldingsNotCreated(holdingsArray);

    log.info("Finished cannotPostSynchronousBatchWithDuplicateHRIDs");
  }

  private void assertBatchDuplicateHridError(Response response, String hrid) {
    assertThat(response.getStatusCode(), is(422));
    final var errors = response.getJson().mapTo(Errors.class);
    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), notNullValue());
    var error = errors.getErrors().getFirst();
    assertThat(error, notNullValue());
    assertThat(error.getMessage(),
      is("HRID value already exists in table holdings_record: " + hrid));
    assertThat(error.getParameters(), notNullValue());
    var parameter = error.getParameters().getFirst();
    assertThat(parameter, notNullValue());
    assertThat(parameter.getKey(),
      is("lower(f_unaccent(jsonb ->> 'hrid'::text))"));
    assertThat(parameter.getValue(),
      is(hrid));
  }

  private void assertAllHoldingsNotCreated(JsonArray holdingsArray) {
    for (int i = 0; i < holdingsArray.size(); i++) {
      assertGetNotFound(holdingsStorageUrl("/" + holdingsArray.getJsonObject(i).getString("id")));
    }
  }

  @Test
  public void cannotPostSynchronousBatchWithHridFailure() {
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
  public void canFilterByFullCallNumber() {
    var instance = instancesClient
      .create(smallAngryPlanet(UUID.randomUUID()));

    final var wholeCallNumberHolding =
      createHoldingWithCallNumberComponents(instance, "prefix", "callNumber", "suffix");
    createHoldingWithCallNumberComponents(instance, "prefix", "callNumber", null);
    createHoldingWithCallNumberComponents(instance, "prefix", "differentCallNumber", "suffix");

    final var foundHoldings = holdingsClient
      .getMany("fullCallNumber == \"%s\"", "prefix callNumber suffix");

    assertThat(foundHoldings.size(), is(1));
    assertThat(foundHoldings.getFirst().getId(), is(wholeCallNumberHolding.getId()));
  }

  private IndividualResource createHoldingWithCallNumberComponents(
      IndividualResource instance, String prefix, String callNumber, String suffix) {
    var builder = new HoldingRequestBuilder()
      .forInstance(instance.getId())
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withCallNumberPrefix(prefix)
      .withCallNumber(callNumber);

    if (suffix != null) {
      builder.withCallNumberSuffix(suffix);
    }

    return createHoldingRecord(builder.create());
  }

  @Test
  public void canFilterByCallNumberAndSuffix() {
    var instance = instancesClient
      .create(smallAngryPlanet(UUID.randomUUID()));

    var wholeCallNumberHolding =
      createHoldingWithCallNumberComponents(instance, "prefix", "callNumber", "suffix");
    createHoldingWithCallNumberComponents(instance, "prefix", "callNumber", null);
    var noPrefixHolding =
      createHoldingWithCallNumberComponents(instance, null, "callNumber", "suffix");

    final var foundHoldings = holdingsClient
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
    UUID holdingPlanet = createHolding(instancePlanet.getId(), MAIN_LIBRARY_LOCATION_ID, null);
    final UUID holdingUprooted = createHolding(instanceUprooted.getId(), MAIN_LIBRARY_LOCATION_ID, null);

    var foundPlanet = holdingsClient.getMany("instance.title = planet");
    assertThat(foundPlanet, hasSize(1));
    assertThat(foundPlanet.getFirst().getId(), is(holdingPlanet));

    var foundUprooted = holdingsClient.getMany("instance.title = uprooted");
    assertThat(foundUprooted, hasSize(1));
    assertThat(foundUprooted.getFirst().getId(), is(holdingUprooted));
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
  public void cannotPostSynchronousBatchUnsafeWithInvalidStatisticalCodeIds() {
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
      Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));

    // insert
    JsonArray holdings = threeHoldings();
    var invalidHolding = holdings.getJsonObject(1);
    invalidHolding.put(STATISTICAL_CODE_IDS_KEY, Set.of(INVALID_VALUE));

    var response = postSynchronousBatchUnsafe(holdings);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), containsString(INVALID_TYPE_ERROR_MESSAGE));
  }

  @Test
  @Ignore
  public void canPostSynchronousBatchUnsafeAndCreateShadowInstanceWithoutUpsert() {
    canPostSynchronousBatchUnsafeAndCreateShadowInstance("");
  }

  @Test
  @Ignore
  public void canPostSynchronousBatchUnsafeAndCreateShadowInstanceWithUpsertTrue() {
    canPostSynchronousBatchUnsafeAndCreateShadowInstance("?upsert=true");
  }

  @Test
  public void canPostSynchronousBatch() {
    JsonArray holdingsArray = threeHoldings();
    assertThat(postSynchronousBatch(holdingsArray), statusCodeIs(HTTP_CREATED));
    for (Object hrObj : holdingsArray) {
      final JsonObject holding = (JsonObject) hrObj;

      assertExists(holding);

      holdingsMessageChecks.createdMessagePublished(getById(holding.getString("id")).getJson(),
        TENANT_ID, mockServer.baseUrl());
    }
  }

  @Test
  public void canPostSynchronousBatchWithoutIdsWithUpsertTrue() {
    var holdingsArray = new JsonArray();
    var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId), TENANT_ID);
    var holdingsRecord = new JsonObject()
      .put("instanceId", instanceId.toString())
      .put("sourceId", HOLDING_SOURCE_IDS[0].toString())
      .put("_version", 1)
      .put("callNumber", "test-call-number")
      .put("permanentLocationId", MAIN_LIBRARY_LOCATION_ID.toString());
    holdingsArray.add(holdingsRecord);
    assertThat(postSynchronousBatch("?upsert=true", holdingsArray), statusCodeIs(HTTP_CREATED));
    var getCompleted = new CompletableFuture<Response>();
    getClient().get(holdingsStorageUrl("") + "?query=callNumber=test-call-number", TENANT_ID, json(getCompleted));
    var response = TestBase.get(getCompleted);
    assertThat(response, statusCodeIs(HTTP_OK));
    var createdHolding = response.getJson().getJsonArray("holdingsRecords").getJsonObject(0);
    assertThat(createdHolding.getString("id"), notNullValue());
    assertThat(createdHolding.getString("callNumber"), equalTo("test-call-number"));
    holdingsMessageChecks.createdMessagePublished(getById(createdHolding.getString("id")).getJson(),
      TENANT_ID, mockServer.baseUrl());
  }

  @Test
  public void canPostSynchronousBatchForConsortiumMember() {
    JsonArray holdingsArray = threeHoldings(CONSORTIUM_MEMBER_TENANT);
    assertThat(postSynchronousBatch("", holdingsArray, CONSORTIUM_MEMBER_TENANT), statusCodeIs(HTTP_CREATED));
    for (Object hrObj : holdingsArray) {
      final JsonObject holding = (JsonObject) hrObj;

      assertExists(holding, CONSORTIUM_MEMBER_TENANT);

      holdingsMessageChecks.createdMessagePublished(getById(holding.getString("id"),
        CONSORTIUM_MEMBER_TENANT).getJson(), CONSORTIUM_MEMBER_TENANT, mockServer.baseUrl());
    }
  }

  @Test
  public void canPostSynchronousBatchAndCreateShadowInstanceWithoutUpsert() {
    canPostSynchronousBatchAndCreateShadowInstance("");
  }

  @Test
  public void canPostSynchronousBatchAndCreateShadowInstanceWithUpsertTrue() {
    canPostSynchronousBatchAndCreateShadowInstance("?upsert=true");
  }

  @Test
  public void cannotPostSynchronousBatchWithNonExistingInstanceAndNonConsortiumTenant() {
    JsonArray holdingsArray = threeHoldingsWithoutInstance();
    var response = postSynchronousBatch(holdingsArray);

    assertThat(response, statusCodeIs(HTTP_UNPROCESSABLE_ENTITY));
    JsonArray errors = response.getJson().getJsonArray("errors");
    assertThat(errors.size(), is(1));

    JsonObject firstError = errors.getJsonObject(0);
    assertThat(firstError.getString("message"), matchesPattern("Cannot set holdings_record.instanceid = [\\S]+ "
                                                               + "because it does not exist in instance.id."));
    assertThat(firstError.getJsonArray("parameters").getJsonObject(0).getString("key"),
      is("holdings_record.instanceid"));

    for (int i = 0; i < holdingsArray.size(); i++) {
      assertGetNotFound(holdingsStorageUrl("/" + holdingsArray.getJsonObject(i).getString("id")));
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

    for (int i = 0; i < holdingsArray.size(); i++) {
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
  public void canPostSynchronousBatchWithExistingIdWithUpsertTrueMultipleTimesAndItemUpdate()
    throws ExecutionException, InterruptedException, TimeoutException {
    final var existingHrId = UUID.randomUUID().toString();
    final var holdingsArray1 = threeHoldings();
    final var holdingsArray2 = threeHoldings();

    holdingsArray1.getJsonObject(1).put("id", existingHrId);
    final var holdingIdWithoutVersion = holdingsArray1.getJsonObject(0).getString("id");
    holdingsArray1.getJsonObject(0).remove("_version");

    // Create 3 holdings
    final var firstResponse = postSynchronousBatch("?upsert=true", holdingsArray1);
    assertThat(firstResponse, statusCodeIs(HTTP_CREATED));

    final var createdHolding = getById(existingHrId).getJson();
    holdingsArray2.set(1, createdHolding);

    // Create item and update holdings
    final var itemForExistingHolding = createItemForHolding(UUID.fromString(existingHrId));
    updateHoldingsAndVerifyMessages(holdingsArray2, createdHolding, existingHrId, holdingIdWithoutVersion,
      holdingsArray1);

    // Update holding location and verify item update
    updateHoldingLocationAndVerifyItemUpdate(createdHolding, existingHrId, itemForExistingHolding);
  }

  private JsonObject createItemForHolding(UUID holdingId)
    throws InterruptedException, ExecutionException, TimeoutException {
    return create(itemsStorageUrl(""), new ItemRequestBuilder()
      .forHolding(holdingId)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withMaterialType(bookMaterialTypeId)
      .create()).getJson();
  }

  private void updateHoldingsAndVerifyMessages(JsonArray holdingsArray2, JsonObject createdHolding,
      String existingHrId, String holdingIdWithoutVersion, JsonArray holdingsArray1) {
    final var secondResponse = postSynchronousBatch("?upsert=true", holdingsArray2);
    assertThat(secondResponse, statusCodeIs(HTTP_CREATED));

    var updatedHolding = createdHolding.copy().put("_version", 2);
    holdingsMessageChecks.updatedMessagePublished(createdHolding, updatedHolding, mockServer.baseUrl());

    //verify all holdings create messages
    Stream.concat(holdingsArray1.stream(), holdingsArray2.stream())
      .map(json -> ((JsonObject) json).getString("id"))
      .filter(id -> !id.equals(existingHrId))
      .map(this::getById)
      .map(Response::getJson)
      .map(holding -> {
        // Remove _version from holding that was created without it
        if (holding.getString("id").equals(holdingIdWithoutVersion)) {
          holding.remove("_version");
        }
        return holding;
      })
      .forEach(holding -> holdingsMessageChecks.createdMessagePublished(holding, TENANT_ID, mockServer.baseUrl()));
  }

  private void updateHoldingLocationAndVerifyItemUpdate(JsonObject createdHolding, String existingHrId,
      JsonObject itemForExistingHolding) {
    var updatedHolding = createdHolding.copy().put("_version", 2);
    var holdingsArray3 = new JsonArray().add(
      updatedHolding.copy().put("permanentLocationId", ANNEX_LIBRARY_LOCATION_ID.toString()));

    final var thirdResponse = postSynchronousBatch("?upsert=true", holdingsArray3);
    assertThat(thirdResponse, statusCodeIs(HTTP_CREATED));

    var updatedHolding2 = getById(existingHrId).getJson();
    holdingsMessageChecks.updatedMessagePublished(updatedHolding, updatedHolding2, mockServer.baseUrl());

    //Verify item is updated since holding's effective location is changed
    var expectedUpdatedItem = itemForExistingHolding.copy()
      .put("_version", 2)
      .put("effectiveLocationId", ANNEX_LIBRARY_LOCATION_ID.toString());
    itemMessageChecks.updatedMessagePublished(itemForExistingHolding, expectedUpdatedItem,
      createdHolding.getString("instanceId"));
  }

  @Test
  public void canSearchByDiscoverySuppressProperty() {
    final var instance = instancesClient
      .create(smallAngryPlanet(UUID.randomUUID()));

    final var suppressedHolding = createHoldingWithDiscoverySuppress(instance, true);
    final var notSuppressedHolding = createHoldingWithDiscoverySuppress(instance, false);
    final var notSuppressedHoldingDefault = createHoldingWithoutDiscoverySuppress(instance);

    assertDiscoverySuppressSearch(suppressedHolding, notSuppressedHolding, notSuppressedHoldingDefault);
  }

  private IndividualResource createHoldingWithDiscoverySuppress(IndividualResource instance, boolean suppressValue) {
    return createHoldingRecord(
      new HoldingRequestBuilder()
        .forInstance(instance.getId())
        .withSource(getPreparedHoldingSourceId())
        .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
        .withDiscoverySuppress(suppressValue).create());
  }

  private IndividualResource createHoldingWithoutDiscoverySuppress(IndividualResource instance) {
    return createHoldingRecord(
      new HoldingRequestBuilder()
        .forInstance(instance.getId())
        .withSource(getPreparedHoldingSourceId())
        .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID).create());
  }

  private void assertDiscoverySuppressSearch(IndividualResource suppressedHolding,
                                             IndividualResource notSuppressedHolding,
                                             IndividualResource notSuppressedHoldingDefault) {
    final var suppressedHoldings = holdingsClient
      .getMany("discoverySuppress==true");
    final var notSuppressedHoldings = holdingsClient
      .getMany("cql.allRecords=1 not discoverySuppress==true");

    assertThat(suppressedHoldings.size(), is(1));
    assertThat(suppressedHoldings.getFirst().getId(), is(suppressedHolding.getId()));

    assertThat(notSuppressedHoldings.size(), is(2));
    assertThat(notSuppressedHoldings.stream()
        .map(IndividualResource::getId)
        .toList(),
      containsInAnyOrder(notSuppressedHolding.getId(), notSuppressedHoldingDefault.getId()));
  }

  @Test
  public void shouldFindHoldingByCallNumberWhenThereIsSuffix() {
    final var instance = instancesClient
      .create(smallAngryPlanet(UUID.randomUUID()));

    final var firstHoldingsToMatch =
      createHoldingWithCallNumber(instance, "GE77 .F73 2014", null);
    final var secondHoldingsToMatch =
      createHoldingWithCallNumber(instance, "GE77 .F73 2014", "Curriculum Materials Collection");
    createHoldingWithCallNumber(instance, "GE77 .F73 ", "2014 Curriculum Materials Collection");

    final List<UUID> foundHoldings = searchByCallNumberEyeReadable("GE77 .F73 2014");

    assertThat(foundHoldings.size(), is(2));
    assertThat(foundHoldings, hasItems(firstHoldingsToMatch.getId(),
      secondHoldingsToMatch.getId()));
  }

  private JsonObject createHoldingWithCallNumber(UUID instanceId, UUID holdingId, String callNumber) {
    return createHoldingRecord(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withCallNumber(callNumber)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE))).create()).getJson();
  }

  private IndividualResource createHoldingWithCallNumber(IndividualResource instance,
                                                         String callNumber, String suffix) {
    var builder = new HoldingRequestBuilder()
      .forInstance(instance.getId())
      .withSource(getPreparedHoldingSourceId())
      .withCallNumber(callNumber)
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID);

    if (suffix != null) {
      builder.withCallNumberSuffix(suffix);
    }

    return createHoldingRecord(builder.create());
  }

  @Test
  public void explicitRightTruncationCanBeApplied() {
    final var instance = instancesClient
      .create(smallAngryPlanet(UUID.randomUUID()));

    final var firstHoldingsToMatch =
      createHoldingWithCallNumber(instance, "GE77 .F73 2014", null);
    final var secondHoldingsToMatch =
      createHoldingWithCallNumber(instance, "GE77 .F73 2014", "Curriculum Materials Collection");
    final var thirdHoldingsToMatch =
      createHoldingWithCallNumber(instance, "GE77 .F73 ", "2014 Curriculum Materials Collection");
    createHoldingWithCallNumber(instance, "GE77 .F74 ", "2014 Curriculum Materials Collection");

    final var foundHoldings = searchByCallNumberEyeReadable("GE77 .F73*");

    assertThat(foundHoldings.size(), is(3));
    assertThat(foundHoldings, hasItems(firstHoldingsToMatch.getId(),
      secondHoldingsToMatch.getId(), thirdHoldingsToMatch.getId()));
  }

  @Test
  public void canSetHoldingStatementWithNotes() {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();

    JsonObject holdingsStatement = new JsonObject();
    holdingsStatement.put("statement", "Test statement");
    holdingsStatement.put("note", "Test note");
    holdingsStatement.put("staffNote", "Test staff note");

    JsonArray holdingsStatements = new JsonArray().add(holdingsStatement);

    IndividualResource holdingResponse = createHoldingRecord(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))
      .withHoldingsStatements(holdingsStatements).create());

    JsonObject holding = holdingResponse.getJson();

    assertThat(holding.getString("instanceId"), is(instanceId.toString()));
    assertThat(holding.getString("permanentLocationId"), is(MAIN_LIBRARY_LOCATION_ID.toString()));

    JsonObject responseStatement = holding.getJsonArray("holdingsStatements").getJsonObject(0);

    assertThat(responseStatement.getString("statement"), is("Test statement"));
    assertThat(responseStatement.getString("note"), is("Test note"));
    assertThat(responseStatement.getString("staffNote"), is("Test staff note"));
  }

  @Test
  public void canSetHoldingStatementForIndexesWithNotes() {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();

    JsonObject holdingsStatement = new JsonObject();
    holdingsStatement.put("statement", "Test statement");
    holdingsStatement.put("note", "Test note");
    holdingsStatement.put("staffNote", "Test staff note");

    JsonArray holdingsStatements = new JsonArray().add(holdingsStatement);

    IndividualResource holdingResponse = createHoldingRecord(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))
      .withHoldingsStatementsForIndexes(holdingsStatements).create());

    JsonObject holding = holdingResponse.getJson();

    assertThat(holding.getString("instanceId"), is(instanceId.toString()));
    assertThat(holding.getString("permanentLocationId"), is(MAIN_LIBRARY_LOCATION_ID.toString()));

    JsonObject responseStatement = holding.getJsonArray("holdingsStatementsForIndexes")
      .getJsonObject(0);

    assertThat(responseStatement.getString("statement"), is("Test statement"));
    assertThat(responseStatement.getString("note"), is("Test note"));
    assertThat(responseStatement.getString("staffNote"), is("Test staff note"));
  }

  @Test
  public void canSetHoldingStatementForSupplementsWithNotes() {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();

    JsonObject holdingsStatement = new JsonObject();
    holdingsStatement.put("statement", "Test statement");
    holdingsStatement.put("note", "Test note");
    holdingsStatement.put("staffNote", "Test staff note");

    JsonArray holdingsStatements = new JsonArray().add(holdingsStatement);

    IndividualResource holdingResponse = createHoldingRecord(new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withTags(new JsonObject().put("tagList", new JsonArray().add(TAG_VALUE)))
      .withHoldingsStatementsForSupplements(holdingsStatements).create());

    JsonObject holding = holdingResponse.getJson();

    assertThat(holding.getString("instanceId"), is(instanceId.toString()));
    assertThat(holding.getString("permanentLocationId"), is(MAIN_LIBRARY_LOCATION_ID.toString()));

    JsonObject responseStatement = holding.getJsonArray("holdingsStatementsForSupplements")
      .getJsonObject(0);

    assertThat(responseStatement.getString("statement"), is("Test statement"));
    assertThat(responseStatement.getString("note"), is("Test note"));
    assertThat(responseStatement.getString("staffNote"), is("Test staff note"));
  }

  @Test
  public void cannotCreateHoldingWithAdditionalCallNumbersMissingCallNumber()
    throws InterruptedException, ExecutionException, TimeoutException {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();
    List<EffectiveCallNumberComponents> additionalCallNumbers = List.of(new EffectiveCallNumberComponents());

    final JsonObject request = new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withAdditionalCallNumbers(additionalCallNumbers).create();

    CompletableFuture<JsonErrorResponse> createCompleted = new CompletableFuture<>();

    getClient().post(holdingsStorageUrl(""), request, TENANT_ID,
      ResponseHandler.jsonErrors(createCompleted));

    JsonErrorResponse response = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(422));
  }

  @Test
  public void canCreateHoldingWithMinimalAdditionalCallNumbers()
    throws InterruptedException, ExecutionException, TimeoutException {
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();
    List<EffectiveCallNumberComponents> additionalCallNumbers = List.of(new EffectiveCallNumberComponents()
      .withCallNumber("123456789"));

    final JsonObject request = new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withAdditionalCallNumbers(additionalCallNumbers).create();
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    getClient().post(holdingsStorageUrl(""), request, TENANT_ID,
      ResponseHandler.json(createCompleted));
    Response response = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
  }

  @Test
  public void canCreateHoldingWithAdditionalCallNumbers()
    throws InterruptedException, ExecutionException, TimeoutException {
    var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));

    var holdingId = UUID.randomUUID();
    final var additionalCallNumbers = createAdditionalCallNumber("123456789", "A", "Z", LC_CN_TYPE_ID);

    final var request = new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withAdditionalCallNumbers(additionalCallNumbers).create();

    var response = createHoldingAndGetResponse(request);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertAdditionalCallNumberInResponse(response, "123456789", "A", "Z", LC_CN_TYPE_ID);
  }

  private List<EffectiveCallNumberComponents> createAdditionalCallNumber(String callNumber,
                                                                          String prefix, String suffix, String typeId) {
    var additionalCallNumbers = new ArrayList<EffectiveCallNumberComponents>();
    additionalCallNumbers.add(new EffectiveCallNumberComponents()
      .withCallNumber(callNumber)
      .withPrefix(prefix)
      .withSuffix(suffix)
      .withTypeId(typeId));
    return additionalCallNumbers;
  }

  private Response createHoldingAndGetResponse(JsonObject request)
      throws InterruptedException, ExecutionException, TimeoutException {
    var createCompleted = new CompletableFuture<Response>();
    getClient().post(holdingsStorageUrl(""), request, TENANT_ID,
      ResponseHandler.json(createCompleted));
    return createCompleted.get(TIMEOUT, TimeUnit.SECONDS);
  }

  private void assertAdditionalCallNumberInResponse(Response response, String callNumber,
                                                     String prefix, String suffix, String typeId) {
    var additionalCallNumbersResponse = response.getJson()
      .getJsonArray("additionalCallNumbers")
      .getJsonObject(0);
    assertThat(additionalCallNumbersResponse.getString("callNumber"), is(callNumber));
    assertThat(additionalCallNumbersResponse.getString("prefix"), is(prefix));
    assertThat(additionalCallNumbersResponse.getString("suffix"), is(suffix));
    assertThat(additionalCallNumbersResponse.getString("typeId"), is(typeId));
  }

  @Test
  public void canCreateHoldingWithEmptyAdditionalCallNumbers()
    throws InterruptedException, ExecutionException, TimeoutException {
    UUID instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();
    List<EffectiveCallNumberComponents> additionalCallNumbers = new ArrayList<>();

    final JsonObject request = new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withAdditionalCallNumbers(additionalCallNumbers).create();
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    getClient().post(holdingsStorageUrl(""), request, TENANT_ID,
      ResponseHandler.json(createCompleted));
    Response response = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
  }

  @Test
  public void canDeleteAdditionalCallNumberFromHolding()
    throws InterruptedException, ExecutionException, TimeoutException {
    var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));

    var holdingId = UUID.randomUUID();
    final var additionalCallNumbers = createAdditionalCallNumber("123456789", "A", "Z", LC_CN_TYPE_ID);

    final var request = new HoldingRequestBuilder()
      .withId(holdingId)
      .withHrid("hrid")
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withAdditionalCallNumbers(additionalCallNumbers).create();

    var response = createHoldingAndGetResponse(request);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    request.remove("additionalCallNumbers");
    request.put("_version", 1);

    final var updateResponse = updateHoldingAndGetResponse(holdingId, request);
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  private JsonErrorResponse updateHoldingAndGetResponse(UUID holdingId, JsonObject request)
      throws InterruptedException, ExecutionException, TimeoutException {
    final var updateCompleted = new CompletableFuture<JsonErrorResponse>();
    getClient().put(holdingsStorageUrl(String.format("/%s", holdingId)), request, TENANT_ID,
      jsonErrors(updateCompleted));
    return updateCompleted.get(TIMEOUT, TimeUnit.SECONDS);
  }

  @Test
  public void canUpdateHoldingsAdditionalCallNumbers()
    throws InterruptedException, ExecutionException, TimeoutException {
    var instanceId = UUID.randomUUID();
    instancesClient.create(smallAngryPlanet(instanceId));

    var holdingId = UUID.randomUUID();
    final var additionalCallNumbers = createAdditionalCallNumber("123456789", "A", "Z", LC_CN_TYPE_ID);

    final var request = new HoldingRequestBuilder()
      .withId(holdingId)
      .withHrid("hrid")
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withAdditionalCallNumbers(additionalCallNumbers).create();

    var response = createHoldingAndGetResponse(request);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    additionalCallNumbers.add(new EffectiveCallNumberComponents()
      .withCallNumber("secondCallNumber").withPrefix("A").withSuffix("Z").withTypeId(LC_CN_TYPE_ID));
    request.put("additionalCallNumbers", additionalCallNumbers);
    request.put("_version", 1);

    final var updateResponse = updateHoldingAndGetResponse(holdingId, request);
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    assertSecondCallNumberAdded(holdingId);
  }

  private void assertSecondCallNumberAdded(UUID holdingId)
      throws InterruptedException, ExecutionException, TimeoutException {
    final CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    getClient().get(holdingsStorageUrl(String.format("/%s", holdingId)), TENANT_ID,
      ResponseHandler.json(getCompleted));
    final Response getResponse = getCompleted.get(TIMEOUT, TimeUnit.SECONDS);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    final JsonObject holding = getResponse.getJson();
    assertThat(holding.getJsonArray("additionalCallNumbers").size(), is(2));
    final JsonObject additionalCallNumber = holding.getJsonArray("additionalCallNumbers").getJsonObject(1);
    assertThat(additionalCallNumber.getString("callNumber"), is("secondCallNumber"));
  }

  @Test
  public void canNotRemoveHoldingsSourcesAttachedToHoldings() {
    var instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    setHoldingsSequence(1);

    var holdingId = UUID.randomUUID();
    var holdingToCreate = new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .create();
    var holdingSourceId = holdingToCreate.getString("sourceId");

    createHoldingRecord(holdingToCreate);

    var response = holdingsSourceClient.attemptToDelete(UUID.fromString(holdingSourceId));
    assertThat(response.getStatusCode(), is(HttpStatus.HTTP_BAD_REQUEST.toInt()));
  }

  private void canPostSynchronousBatchUnsafeAndCreateShadowInstance(String subpath) {
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
      Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));
    mockSharingInstance();

    JsonArray holdingsArray = threeHoldingsWithoutInstance();
    assertThat(postSynchronousBatchUnsafe(subpath, holdingsArray, CONSORTIUM_MEMBER_TENANT),
      statusCodeIs(HTTP_CREATED));
    for (Object hrObj : holdingsArray) {
      final JsonObject holding = (JsonObject) hrObj;
      assertExists(holding, CONSORTIUM_MEMBER_TENANT);
      holdingsMessageChecks.createdMessagePublished(getById(holding.getString("id"),
        CONSORTIUM_MEMBER_TENANT).getJson(), CONSORTIUM_MEMBER_TENANT, mockServer.baseUrl());
    }
  }

  private void canPostSynchronousBatchAndCreateShadowInstance(String subpath) {
    mockSharingInstance();

    JsonArray holdingsArray = threeHoldingsWithoutInstance();
    assertThat(postSynchronousBatch(subpath, holdingsArray, CONSORTIUM_MEMBER_TENANT), statusCodeIs(HTTP_CREATED));
    for (Object hrObj : holdingsArray) {
      final JsonObject holding = (JsonObject) hrObj;

      assertExists(holding, CONSORTIUM_MEMBER_TENANT);

      holdingsMessageChecks.createdMessagePublished(getById(holding.getString("id"),
        CONSORTIUM_MEMBER_TENANT).getJson(), CONSORTIUM_MEMBER_TENANT, mockServer.baseUrl());
    }
  }

  private void setHoldingsSequence(long sequenceNumber) {
    final PostgresClient postgresClient =
      PostgresClient.getInstance(getVertx(), TENANT_ID);
    final CompletableFuture<Void> sequenceSet = new CompletableFuture<>();

    getVertx().runOnContext(v ->
      postgresClient.selectSingle("select setval('hrid_holdings_seq'," + sequenceNumber + ",FALSE)",
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

  /**
   * Create three instances, and for each of them return a JsonObject of a new holding belonging to it.
   * The holdings are not created.
   */
  private JsonArray threeHoldings() {
    return threeHoldings(TENANT_ID);
  }

  private JsonArray threeHoldings(String tenantId) {
    JsonArray holdingsArray = new JsonArray();
    for (int i = 0; i < 3; i++) {
      UUID instanceId = UUID.randomUUID();
      instancesClient.create(smallAngryPlanet(instanceId), tenantId);
      holdingsArray.add(new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("instanceId", instanceId.toString())
        .put("sourceId", HOLDING_SOURCE_IDS[i].toString())
        .put("_version", 1)
        .put("permanentLocationId", MAIN_LIBRARY_LOCATION_ID.toString()));
    }
    return holdingsArray;
  }

  private JsonArray threeHoldingsWithoutInstance() {
    JsonArray holdingsArray = new JsonArray();
    for (int i = 0; i < 3; i++) {
      UUID instanceId = UUID.randomUUID();
      holdingsArray.add(new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("instanceId", instanceId.toString())
        .put("sourceId", HOLDING_SOURCE_IDS[i].toString())
        .put("_version", 1)
        .put("permanentLocationId", MAIN_LIBRARY_LOCATION_ID.toString()));
    }
    return holdingsArray;
  }

  private static void prepareThreeHoldingSource() {
    for (int i = 0; i < 3; i++) {
      String sourceId = HOLDING_SOURCE_IDS[i].toString();
      holdingsSourceClient.create(new JsonObject()
        .put("id", sourceId)
        .put("name", "holding source name for " + sourceId));
      holdingsSourceClient.create(new JsonObject()
          .put("id", sourceId)
          .put("name", "holding source name for " + sourceId),
        CONSORTIUM_MEMBER_TENANT);
    }
  }

  private Response postSynchronousBatchUnsafe(JsonArray holdingsArray) {
    return postSynchronousBatch(holdingsStorageSyncUnsafeUrl(""), holdingsArray);
  }

  private Response postSynchronousBatchUnsafe(String subPath, JsonArray holdingsArray, String tenantId) {
    return postSynchronousBatch(holdingsStorageSyncUnsafeUrl(subPath), holdingsArray, tenantId);
  }

  private Response postSynchronousBatch(JsonArray holdingsArray) {
    return postSynchronousBatch(holdingsStorageSyncUrl(""), holdingsArray);
  }

  private Response postSynchronousBatch(String subPath, JsonArray holdingsArray) {
    return postSynchronousBatch(holdingsStorageSyncUrl(subPath), holdingsArray);
  }

  private Response postSynchronousBatch(URL url, JsonArray holdingsArray) {
    return postSynchronousBatch(url, holdingsArray, TENANT_ID);
  }

  private Response postSynchronousBatch(String subPath, JsonArray holdingsArray, String tenantId) {
    return postSynchronousBatch(holdingsStorageSyncUrl(subPath), holdingsArray, tenantId);
  }

  private Response postSynchronousBatch(URL url, JsonArray holdingsArray, String tenantId) {
    JsonObject holdingsCollection = new JsonObject().put("holdingsRecords", holdingsArray);
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    getClient().post(url, holdingsCollection, Map.of(X_OKAPI_URL, mockServer.baseUrl()), tenantId,
      ResponseHandler.any(createCompleted));
    try {
      return createCompleted.get(10, SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private static JsonObject smallAngryPlanet(UUID id) {
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
    return getById(id, TENANT_ID);
  }

  private Response getById(String id, String tenantId) {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    getClient().get(holdingsStorageUrl("/" + id), tenantId, json(getCompleted));
    try {
      return getCompleted.get(10, SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private void assertExists(JsonObject expectedHolding) {
    assertExists(expectedHolding, TENANT_ID);
  }

  private void assertExists(JsonObject expectedHolding, String tenantId) {
    Response response = getById(expectedHolding.getString("id"), tenantId);
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

  private void assertHridRange(Response response, String minHrid, String maxHrid) {
    assertThat(response.getJson().getString("hrid"),
      is(both(greaterThanOrEqualTo(minHrid)).and(lessThanOrEqualTo(maxHrid))));
  }

  private Response create(URL url, Object entity) throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(url, entity, TENANT_ID,
      ResponseHandler.json(createCompleted));

    return createCompleted.get(TIMEOUT, TimeUnit.SECONDS);
  }

  private Response get(URL url) throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(url, TENANT_ID,
      ResponseHandler.json(getCompleted));

    return getCompleted.get(TIMEOUT, TimeUnit.SECONDS);
  }

  private List<UUID> searchByCallNumberEyeReadable(String searchTerm) {

    return holdingsClient
      .getMany("fullCallNumber==\"%1$s\" OR callNumberAndSuffix==\"%1$s\" OR callNumber==\"%1$s\"",
        searchTerm)
      .stream()
      .map(IndividualResource::getId)
      .toList();
  }

  private Response update(URL url, Object entity) throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    getClient().put(url, entity, TENANT_ID,
      ResponseHandler.empty(putCompleted));

    return putCompleted.get(TIMEOUT, TimeUnit.SECONDS);
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
      .map(String.class::cast)
      .toList();
  }

  private void mockUserTenantsForTenantWithoutPermissions() {
    WireMock.stubFor(WireMock.get(USER_TENANTS_PATH)
      .withHeader(X_OKAPI_TENANT, equalToIgnoreCase(TENANT_WITHOUT_USER_TENANTS_PERMISSIONS))
      .willReturn(WireMock.forbidden()));
  }

  private void mockSharingInstance() {
    WireMock.stubFor(WireMock.post("/consortia/mobius/sharing/instances")
      .willReturn(WireMock.created().withTransformers(ConsortiumInstanceSharingTransformer.NAME)));
  }

  public static class ConsortiumInstanceSharingTransformer extends ResponseTransformer {
    public static final String NAME = "consortium-instance-sharing-transformer";

    @SneakyThrows
    @Override
    public com.github.tomakehurst.wiremock.http.Response transform(
      Request request,
      com.github.tomakehurst.wiremock.http.Response response,
      FileSource fileSource,
      com.github.tomakehurst.wiremock.extension.Parameters parameters) {

      SharingInstance sharingInstance = Json.getObjectMapper().readValue(request.getBody(),
        SharingInstance.class);
      sharingInstance.setStatus(SharingStatus.COMPLETE);

      instancesClient.create(smallAngryPlanet(sharingInstance.getInstanceIdentifier()),
        sharingInstance.getTargetTenantId());

      return like(response).body(Json.getObjectMapper().writeValueAsString(sharingInstance)).build();
    }

    @Override
    public String getName() {
      return NAME;
    }

    @Override
    public boolean applyGlobally() {
      return false;
    }
  }
}
