package org.folio.it.api.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_CREATED;
import static org.folio.HttpStatus.SC_NOT_FOUND;
import static org.folio.HttpStatus.SC_OK;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.it.HoldingsStorageFixtures.createCallNumberType;
import static org.folio.it.HoldingsStorageFixtures.createHoldingsRecordsSource;
import static org.folio.it.HoldingsStorageFixtures.createLoanType;
import static org.folio.it.HoldingsStorageFixtures.createMaterialType;
import static org.folio.it.InstanceStorageFixtures.createInstance;
import static org.folio.it.InstanceStorageFixtures.createInstanceType;
import static org.folio.it.LocationStorageFixtures.createLocation;
import static org.folio.support.ResourcePaths.HOLDINGS;
import static org.folio.support.ResourcePaths.ITEMS;
import static org.folio.support.ResourcePaths.ITEMS_SYNC;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.it.BaseIntegrationTest;
import org.folio.it.HoldingsStorageFixtures;
import org.folio.rest.tools.utils.OptimisticLockingUtil;
import org.folio.support.builders.HoldingRequestBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/**
 * Shared reference data, lifecycle, and request/assertion helpers for the {@code ItemStorage*IT} classes.
 */
abstract class ItemStorageTestBase extends BaseIntegrationTest {

  static final String TAG_VALUE = "test-tag";
  static final String INVALID_VALUE = "invalid value";
  static final String INVALID_TYPE_ERROR_MESSAGE =
    "invalid input syntax for type uuid: \"" + INVALID_VALUE + "\"";
  static final String ORDER_FIELD = "order";
  static final String STATISTICAL_CODE_IDS_KEY = "statisticalCodeIds";
  static final String ITEMS_KEY = "items";
  static final String STATUS_KEY = "status";

  static String instanceTypeId;
  static String materialTypeId;
  static String loanTypeId;
  static String secondLoanTypeId;
  static String mainLibraryLocationId;
  static String annexLibraryLocationId;
  static String onlineLocationId;
  static String secondFloorLocationId;
  static String lcCallNumberTypeId;
  static String deweyCallNumberTypeId;

  // The additionalCallNumbers typeId CallNumberUtils/tests key off; cannot be a fresh/random id.
  private static final String LC_CALL_NUMBER_TYPE_ID = "95467209-6d7b-468b-94df-0f5d7ad2747d";
  private static final String DEWEY_CALL_NUMBER_TYPE_ID = "03dd64d0-5626-4ecd-8ece-4531e0069f35";

  @BeforeAll
  static void seedReferenceData() {
    instanceTypeId = createInstanceType(client);
    materialTypeId = createMaterialType(client);
    loanTypeId = createLoanType(client);
    secondLoanTypeId = createLoanType(client);
    mainLibraryLocationId = createLocation(client);
    annexLibraryLocationId = createLocation(client);
    onlineLocationId = createLocation(client);
    secondFloorLocationId = createLocation(client);
    lcCallNumberTypeId = createCallNumberType(client, LC_CALL_NUMBER_TYPE_ID, "Library of Congress classification");
    deweyCallNumberTypeId = createCallNumberType(client, DEWEY_CALL_NUMBER_TYPE_ID, "Dewey Decimal classification");
  }

  @BeforeEach
  void clearItems() {
    runQuery("TRUNCATE TABLE instance, holdings_record, item CASCADE");
  }

  @AfterEach
  void resetItemSequenceAndOptimisticLockingOverride() {
    setItemSequence(1);
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(Map.of());
  }

  static String createHoldingRecord() {
    return createHoldingRecord(mainLibraryLocationId);
  }

  static String createHoldingRecord(String locationId) {
    var instanceId = createInstance(client, "an instance " + UUID.randomUUID(), instanceTypeId);
    return HoldingsStorageFixtures.createHolding(client, instanceId, locationId);
  }

  private static String createHoldingRecordWithCallNumber(String callNumber) {
    var instanceId = createInstance(client, "an instance " + UUID.randomUUID(), instanceTypeId);
    var request = new HoldingRequestBuilder().forInstance(UUID.fromString(instanceId))
      .withSource(UUID.fromString(createHoldingsRecordsSource(client)))
      .withPermanentLocation(UUID.fromString(mainLibraryLocationId)).withCallNumber(callNumber).create();
    return await(doPost(client, HOLDINGS, request)).jsonBody().getString("id");
  }

  static JsonObject minimalItemRequest(UUID id, String holdingId) {
    var item = new JsonObject().put(STATUS_KEY, new JsonObject().put("name", "Available"))
      .put("holdingsRecordId", holdingId).put("materialTypeId", materialTypeId)
      .put("permanentLoanTypeId", loanTypeId).put("tags", tags(TAG_VALUE));
    if (id != null) {
      item.put("id", id.toString());
    }
    return item;
  }

  static JsonObject smallAngryPlanet(UUID itemId, String holdingId) {
    var item = minimalItemRequest(itemId, holdingId).put("barcode", "036000291452")
      .put("temporaryLocationId", annexLibraryLocationId).put("_version", 1);
    item.remove("tags");
    return item;
  }

  static JsonObject tags(String... tagValues) {
    return new JsonObject().put("tagList", new JsonArray(List.of(tagValues)));
  }

  static List<String> getTags(JsonObject item) {
    return item.getJsonObject("tags").getJsonArray("tagList").stream().map(String.class::cast).toList();
  }

  static JsonObject createItem(JsonObject request) {
    var response = await(doPost(client, ITEMS, request));
    assertThat(response.status()).isEqualTo(SC_CREATED);
    return response.jsonBody();
  }

  static TestResponse getItemById(String id) {
    return await(doGet(client, ITEMS + "/" + id));
  }

  static JsonObject getItemJsonById(String id) {
    return getItemById(id).jsonBody();
  }

  static TestResponse updateItem(JsonObject item) {
    return await(doPut(client, ITEMS + "/" + item.getString("id"), item));
  }

  static void assertGetNotFound(String path) {
    assertThat(await(doGet(client, path)).status()).isEqualTo(SC_NOT_FOUND);
  }

  static void assertExists(JsonObject expectedItem) {
    var response = await(doGet(client, ITEMS + "/" + expectedItem.getString("id")));
    assertThat(response.status()).isEqualTo(SC_OK);
    assertThat(response.body().toString()).contains(expectedItem.getString("holdingsRecordId"));
  }

  static TestResponse syncBatch(JsonArray itemsArray) {
    return syncBatch("", itemsArray);
  }

  static TestResponse syncBatch(String queryParams, JsonArray itemsArray) {
    return await(doPost(client, ITEMS_SYNC + queryParams, new JsonObject().put(ITEMS_KEY, itemsArray)));
  }

  static void setItemSequence(long sequenceNumber) {
    runQuery("select setval('hrid_items_seq'," + sequenceNumber + ",FALSE)");
  }

  static JsonArray threeItems() {
    var holdingId = createHoldingRecordWithCallNumber("hrCallNumber");
    return new JsonArray()
      .add(minimalItemRequest(UUID.randomUUID(), holdingId).put("barcode", UUID.randomUUID().toString()))
      .add(minimalItemRequest(UUID.randomUUID(), holdingId).put("barcode", UUID.randomUUID().toString()))
      .add(minimalItemRequest(UUID.randomUUID(), holdingId).put("barcode", UUID.randomUUID().toString()));
  }
}
