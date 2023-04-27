package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.instanceStatusesUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.loanTypesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.materialTypesStorageUrl;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;
import lombok.SneakyThrows;
import org.folio.HttpStatus;
import org.folio.rest.jaxrs.model.InstanceType;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.folio.rest.support.client.LoanTypesClient;
import org.folio.rest.support.client.MaterialTypesClient;
import org.folio.utility.LocationUtility;
import org.junit.BeforeClass;


public abstract class TestBaseWithInventoryUtil extends TestBase {
  public static final String MAIN_LIBRARY_LOCATION = "Main Library";
  public static final String SECOND_FLOOR_LOCATION = "Second Floor";
  public static final String ANNEX_LIBRARY_LOCATION = "Annex Library";
  public static final String ONLINE_LOCATION = "Online";
  public static final String THIRD_FLOOR_LOCATION = "Third Floor";
  public static final String FOURTH_FLOOR_LOCATION = "Fourth Floor";
  // Creating the UUIDs here because they are used in ItemEffectiveLocationTest.parameters()
  // that JUnit calls *before* the @BeforeClass beforeAny() method.
  public static final UUID MAIN_LIBRARY_LOCATION_ID = UUID.randomUUID();
  public static final UUID ANNEX_LIBRARY_LOCATION_ID = UUID.randomUUID();
  public static final UUID ONLINE_LOCATION_ID = UUID.randomUUID();
  public static final UUID SECOND_FLOOR_LOCATION_ID = UUID.randomUUID();
  public static final UUID THIRD_FLOOR_LOCATION_ID = UUID.randomUUID();
  public static final UUID FOURTH_FLOOR_LOCATION_ID = UUID.randomUUID();
  protected static final String PERMANENT_LOCATION_ID_KEY = "permanentLocationId";
  protected static final String TEMPORARY_LOCATION_ID_KEY = "temporaryLocationId";
  protected static final String EFFECTIVE_LOCATION_ID_KEY = "effectiveLocationId";
  // These UUIDs were taken from reference-data folder.
  // When the vertical gets started the data from the reference-data folder are loaded to the DB.
  // see org.folio.rest.impl.TenantRefAPI.refPaths
  protected static final UUID UUID_INVALID_ISBN = UUID.fromString("fcca2643-406a-482a-b760-7a7f8aec640e");
  protected static final UUID UUID_ISBN = UUID.fromString("8261054f-be78-422d-bd51-4ed9f33c3422");
  protected static final UUID UUID_ASIN = UUID.fromString("7f907515-a1bf-4513-8a38-92e1a07c539d");
  protected static final UUID UUID_PERSONAL_NAME = UUID.fromString("2b94c631-fca9-4892-a730-03ee529ffe2a");
  protected static final UUID UUID_TEXT = UUID.fromString("6312d172-f0cf-40f6-b27d-9fa8feaf332f");
  protected static final UUID UUID_INSTANCE_TYPE = UUID.fromString("535e3160-763a-42f9-b0c0-d8ed7df6e2a2");
  protected static UUID journalMaterialTypeId;
  protected static String journalMaterialTypeID;
  protected static UUID bookMaterialTypeId;
  protected static String bookMaterialTypeID;
  protected static UUID canCirculateLoanTypeId;
  protected static String canCirculateLoanTypeID;
  protected static UUID nonCirculatingLoanTypeId;
  protected static String nonCirculatingLoanTypeID;

  @SneakyThrows
  @BeforeClass
  public static void testBaseWithInvUtilBeforeClass() {
    logger.info("starting @BeforeClass testBaseWithInvUtilBeforeClass()");

    StorageTestSuite.deleteAll(TENANT_ID, "preceding_succeeding_title");
    StorageTestSuite.deleteAll(TENANT_ID, "instance_relationship");
    StorageTestSuite.deleteAll(TENANT_ID, "bound_with_part");

    clearData();

    createDefaultInstanceType();
    setupMaterialTypes();
    setupLoanTypes();
    setupLocations();

    KAFKA_CONSUMER.discardAllMessages();

    logger.info("finishing @BeforeClass testBaseWithInvUtilBeforeClass()");
  }

  protected static void setupMaterialTypes() {
    MaterialTypesClient materialTypesClient = new MaterialTypesClient(getClient(), materialTypesStorageUrl(""));
    journalMaterialTypeID = materialTypesClient.create("journal");
    journalMaterialTypeId = UUID.fromString(journalMaterialTypeID);
    bookMaterialTypeID = materialTypesClient.create("book");
    bookMaterialTypeId = UUID.fromString(bookMaterialTypeID);
  }

  protected static void setupLoanTypes() {
    LoanTypesClient loanTypesClient = new LoanTypesClient(getClient(), loanTypesStorageUrl(""));
    canCirculateLoanTypeID = loanTypesClient.create("Can Circulate");
    canCirculateLoanTypeId = UUID.fromString(canCirculateLoanTypeID);
    nonCirculatingLoanTypeID = loanTypesClient.create("Non-Circulating");
    nonCirculatingLoanTypeId = UUID.fromString(nonCirculatingLoanTypeID);
  }

  protected static void setupLocations() {
    LocationUtility.clearServicePointIds();
    LocationUtility.createLocationUnits(true);
    LocationUtility.createLocation(MAIN_LIBRARY_LOCATION_ID, MAIN_LIBRARY_LOCATION, "TestBaseWI/M");
    LocationUtility.createLocation(ANNEX_LIBRARY_LOCATION_ID, ANNEX_LIBRARY_LOCATION, "TestBaseWI/A");
    LocationUtility.createLocation(ONLINE_LOCATION_ID, ONLINE_LOCATION, "TestBaseWI/O");
    LocationUtility.createLocation(SECOND_FLOOR_LOCATION_ID, SECOND_FLOOR_LOCATION, "TestBaseWI/SF");
    LocationUtility.createLocation(THIRD_FLOOR_LOCATION_ID, THIRD_FLOOR_LOCATION, "TestBaseWI/TF");
    LocationUtility.createLocation(FOURTH_FLOOR_LOCATION_ID, FOURTH_FLOOR_LOCATION, "TestBaseWI/FF");
  }

  protected static UUID createInstanceAndHolding(UUID holdingsPermanentLocationId) {
    return createInstanceAndHolding(holdingsPermanentLocationId, null);
  }

  protected static UUID createInstanceAndHolding(UUID holdingsPermanentLocationId,
                                                 UUID holdingsTemporaryLocationId) {

    UUID instanceId = UUID.randomUUID();
    instancesClient.create(instance(instanceId));
    return createHolding(instanceId, holdingsPermanentLocationId, holdingsTemporaryLocationId);
  }

  protected static UUID createInstanceAndHoldingWithBuilder(
    UUID holdingsPermanentLocationId, UnaryOperator<HoldingRequestBuilder> holdingsBuilderProcessor) {

    UUID instanceId = UUID.randomUUID();
    instancesClient.create(instance(instanceId));

    HoldingRequestBuilder holdingsBuilder = new HoldingRequestBuilder()
      .withId(UUID.randomUUID())
      .forInstance(instanceId)
      .withPermanentLocation(holdingsPermanentLocationId);

    return holdingsClient
      .create(holdingsBuilderProcessor.apply(holdingsBuilder))
      .getId();
  }

  protected static UUID createHolding(UUID instanceId,
                                      UUID holdingsPermanentLocationId,
                                      UUID holdingsTemporaryLocationId) {
    return holdingsClient.create(
      new HoldingRequestBuilder()
        .withId(UUID.randomUUID())
        .forInstance(instanceId)
        .withPermanentLocation(holdingsPermanentLocationId)
        .withTemporaryLocation(holdingsTemporaryLocationId)
    ).getId();
  }

  protected static UUID createInstanceAndHoldingWithCallNumber(UUID holdingsPermanentLocationId) {
    UUID instanceId = UUID.randomUUID();
    instancesClient.create(instance(instanceId));

    return holdingsClient.create(
      new HoldingRequestBuilder()
        .withId(UUID.randomUUID())
        .forInstance(instanceId)
        .withPermanentLocation(holdingsPermanentLocationId)
        .withCallNumber("testCallNumber")
    ).getId();
  }

  protected static UUID createInstanceAndHoldingWithCallNumberPrefix(UUID holdingsPermanentLocationId) {
    UUID instanceId = UUID.randomUUID();
    instancesClient.create(instance(instanceId));

    return holdingsClient.create(
      new HoldingRequestBuilder()
        .withId(UUID.randomUUID())
        .forInstance(instanceId)
        .withPermanentLocation(holdingsPermanentLocationId)
        .withCallNumberPrefix("testCallNumberPrefix")
    ).getId();
  }

  protected static UUID createInstanceAndHoldingWithCallNumberSuffix(UUID holdingsPermanentLocationId) {
    UUID instanceId = UUID.randomUUID();
    instancesClient.create(instance(instanceId));

    return holdingsClient.create(
      new HoldingRequestBuilder()
        .withId(UUID.randomUUID())
        .forInstance(instanceId)
        .withPermanentLocation(holdingsPermanentLocationId)
        .withCallNumberSuffix("testCallNumberSuffix")
    ).getId();
  }

  protected static JsonObject instance(UUID id) {
    return createInstanceRequest(
      id,
      "TEST",
      "Long Way to a Small Angry Planet",
      new JsonArray().add(identifier(UUID_ISBN, "9781473619777")),
      new JsonArray().add(contributor(UUID_PERSONAL_NAME, "Chambers, Becky")),
      UUID_INSTANCE_TYPE,
      new JsonArray().add("test-tag")
    );
  }

  protected static Item buildItem(UUID holdingsRecordId,
                                  UUID permLocation,
                                  UUID tempLocation) {
    JsonObject itemToCreate = new JsonObject();

    itemToCreate.put("id", UUID.randomUUID().toString());
    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("barcode", Long.toString(new Random().nextLong()));
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("materialTypeId", journalMaterialTypeID);
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    if (tempLocation != null) {
      itemToCreate.put(TEMPORARY_LOCATION_ID_KEY, tempLocation.toString());
    }
    if (permLocation != null) {
      itemToCreate.put(PERMANENT_LOCATION_ID_KEY, permLocation.toString());
    }

    return itemToCreate.mapTo(Item.class);
  }

  protected static void createDefaultInstanceType() {
    if (instanceTypeDoesNotAlreadyExist(UUID_INSTANCE_TYPE)) {
      InstanceType it = new InstanceType();
      it.withId(UUID_INSTANCE_TYPE.toString());
      it.withCode("DIT");
      it.withName("Default Instance Type");
      it.withSource("local");

      instanceTypesClient.create(JsonObject.mapFrom(it));
    }
  }

  private static boolean instanceTypeDoesNotAlreadyExist(UUID id) {
    Response response = instanceTypesClient.getById(id);
    return response.getStatusCode() == HttpStatus.HTTP_NOT_FOUND.toInt();
  }

  protected static JsonObject identifier(UUID identifierTypeId, String value) {
    return new JsonObject()
      .put("identifierTypeId", identifierTypeId.toString())
      .put("value", value);
  }

  protected static JsonObject contributor(UUID contributorNameTypeId, String name) {
    return new JsonObject()
      .put("contributorNameTypeId", contributorNameTypeId.toString())
      .put("name", name);
  }

  protected static JsonObject createInstanceRequest(
    UUID id,
    String source,
    String title,
    JsonArray identifiers,
    JsonArray contributors,
    UUID instanceTypeId,
    JsonArray tags) {

    JsonObject instanceToCreate = new JsonObject();

    if (id != null) {
      instanceToCreate.put("id", id.toString());
    }

    instanceToCreate.put("title", title);
    instanceToCreate.put("source", source);
    instanceToCreate.put("identifiers", identifiers);
    instanceToCreate.put("contributors", contributors);
    instanceToCreate.put("instanceTypeId", instanceTypeId.toString());
    instanceToCreate.put("tags", new JsonObject().put("tagList", tags));
    instanceToCreate.put("_version", 1);
    return instanceToCreate;
  }

  protected JsonObject createItem(JsonObject itemToCreate) {
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(itemsStorageUrl(""), itemToCreate, TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response response = get(createCompleted);

    assertThat(response.getStatusCode(), is(201));

    return response.getJson();
  }

  protected IndividualResource createItem(Item item) {
    return itemsClient.create(JsonObject.mapFrom(item));
  }

  protected IndividualResource createItem(ItemRequestBuilder item) {
    return itemsClient.create(item.create());
  }

  IndividualResource getCatalogedInstanceType() {
    return getInstanceStatusByCode("cat");
  }

  IndividualResource getOtherInstanceType() {
    return getInstanceStatusByCode("other");
  }

  private IndividualResource getInstanceStatusByCode(String code) {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(instanceStatusesUrl("?query=code=" + code),
      TENANT_ID, ResponseHandler.json(getCompleted));

    JsonObject instanceStatus = get(getCompleted)
      .getJson()
      .getJsonArray("instanceStatuses").getJsonObject(0);

    return new IndividualResource(instanceStatus);
  }
}
