package org.folio.it.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_BAD_REQUEST;
import static org.folio.HttpStatus.SC_CONFLICT;
import static org.folio.HttpStatus.SC_CREATED;
import static org.folio.HttpStatus.SC_OK;
import static org.folio.HttpStatus.SC_REQUEST_TOO_LONG;
import static org.folio.HttpStatus.SC_UNPROCESSABLE_CONTENT;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.support.ResourcePaths.ITEMS;
import static org.folio.support.ResourcePaths.ITEMS_SYNC_UNSAFE;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.folio.rest.tools.utils.OptimisticLockingUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ItemStorageBatchIT extends ItemStorageTestBase {

  @Test
  @DisplayName("should return 400 when a synchronous batch has non-UUID statistical code ids")
  void shouldReturn400_whenSynchronousBatchHasNonUuidStatisticalCodeIds() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put(STATISTICAL_CODE_IDS_KEY,
      new JsonArray().add("00000000-0000-4444-8888-000000000000").add("12345678"));

    var response = syncBatch(new JsonArray().add(itemToCreate));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains("invalid input syntax for type uuid: \"12345678\"");
  }

  @Test
  @DisplayName("should create items via a synchronous batch")
  void shouldCreateItems_viaSynchronousBatch() {
    var itemsArray = threeItems();

    assertThat(syncBatch(itemsArray).status()).isEqualTo(SC_CREATED);

    itemsArray.forEach(item -> assertExists((JsonObject) item));
  }

  @Test
  @DisplayName("should create an item without an id via a synchronous batch with upsert=true")
  void shouldCreateItem_withoutIdViaSynchronousBatchWithUpsertTrue() {
    var holdingId = createHoldingRecord();
    var item = minimalItemRequest(null, holdingId).put("barcode", "item1");

    assertThat(syncBatch("?upsert=true", new JsonArray().add(item)).status()).isEqualTo(SC_CREATED);

    var found = await(doGet(client, ITEMS + "?query=barcode=item1")).jsonBody()
      .getJsonArray(ITEMS_KEY).getJsonObject(0);
    assertThat(found.getString("id")).isNotNull();
    assertThat(found.getString("barcode")).isEqualTo("item1");
  }

  @Test
  @DisplayName("should return 400 when a synchronous batch has invalid statistical code ids")
  void shouldReturn400_whenSynchronousBatchHasInvalidStatisticalCodeIds() {
    var itemsArray = threeItems();
    itemsArray.getJsonObject(1).put(STATISTICAL_CODE_IDS_KEY, List.of(INVALID_VALUE));

    var response = syncBatch(itemsArray);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains(INVALID_TYPE_ERROR_MESSAGE);
  }

  @Test
  @DisplayName("should return 413 when an unsafe synchronous batch is not allowed")
  void shouldReturn413_whenUnsafeSynchronousBatchNotAllowed() {
    // not allowed because DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING is not configured
    assertThat(syncBatchUnsafe(threeItems()).status()).isEqualTo(SC_REQUEST_TOO_LONG);
  }

  @Test
  @DisplayName("should create items via an unsafe synchronous batch")
  void shouldCreateItems_viaUnsafeSynchronousBatch() {
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
      Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));
    var itemsArray = threeItems();
    assertThat(syncBatchUnsafe(itemsArray).status()).isEqualTo(SC_CREATED);

    itemsArray.getJsonObject(1).put("barcode", "123");
    assertThat(syncBatchUnsafe(itemsArray).status()).isEqualTo(SC_CREATED);

    // safe update, env var should not influence the regular API
    itemsArray.getJsonObject(1).put("barcode", "456");
    assertThat(syncBatch("?upsert=true", itemsArray).status()).isEqualTo(SC_CONFLICT);
  }

  @Test
  @DisplayName("should return 400 when an unsafe synchronous batch has invalid statistical code ids")
  void shouldReturn400_whenUnsafeSynchronousBatchHasInvalidStatisticalCodeIds() {
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
      Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));
    var itemsArray = threeItems();
    itemsArray.getJsonObject(1).put(STATISTICAL_CODE_IDS_KEY, List.of(INVALID_VALUE));

    var response = syncBatchUnsafe(itemsArray);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains(INVALID_TYPE_ERROR_MESSAGE);
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch has a duplicate id")
  void shouldReturn422_whenSynchronousBatchHasDuplicateId() {
    var itemsArray = threeItems();
    var duplicateId = itemsArray.getJsonObject(0).getString("id");
    itemsArray.getJsonObject(1).put("id", duplicateId);

    var response = syncBatch(itemsArray);

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    itemsArray.forEach(item -> assertGetNotFound(ITEMS + "/" + ((JsonObject) item).getString("id")));
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch reuses an existing id without upsert")
  void shouldReturn422_whenSynchronousBatchReusesExistingId_withoutUpsert() {
    assertReturns422ForExistingId("");
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch reuses an existing id with upsert=false")
  void shouldReturn422_whenSynchronousBatchReusesExistingId_withUpsertFalse() {
    assertReturns422ForExistingId("?upsert=false");
  }

  @Test
  @DisplayName("should create items via a synchronous batch reusing an existing id with upsert=true")
  void shouldCreateItems_viaSynchronousBatchReusingExistingIdWithUpsertTrue() {
    var holdingId = createHoldingRecord();
    var existingItemId = UUID.randomUUID();
    var itemsArray1 = threeItemsWithId(holdingId, existingItemId);
    var itemsArray2 = threeItemsWithId(holdingId, existingItemId);

    var firstResponse = syncBatch("?upsert=true", itemsArray1);
    var secondResponse = syncBatch("?upsert=true", itemsArray2);

    assertThat(firstResponse.status()).isEqualTo(SC_CREATED);
    assertThat(secondResponse.status()).isEqualTo(SC_CREATED);
    Stream.concat(itemsArray1.stream(), itemsArray2.stream())
      .map(item -> ((JsonObject) item).getString("id"))
      .forEach(id -> assertThat(getItemById(id).status()).isEqualTo(SC_OK));
  }

  @Test
  @DisplayName("should batch create items")
  void shouldBatchCreateItems() {
    var itemsArray = threeItems();

    assertThat(syncBatch(itemsArray).status()).isEqualTo(SC_CREATED);

    itemsArray.forEach(item -> {
      var itemFromGet = getItemJsonById(((JsonObject) item).getString("id"));
      assertThat(itemFromGet.getString("hrid")).isNotNull();
      assertThat(itemFromGet.getInteger(ORDER_FIELD)).isIn(1, 2, 3);
    });
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch has a non-existent holdings record id")
  void shouldReturn422_whenSynchronousBatchHasNonExistentHoldingsRecordId() {
    var itemsArray = threeItems();
    itemsArray.getJsonObject(2).put("holdingsRecordId", UUID.randomUUID().toString());

    var response = syncBatch(itemsArray);

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(response.body().toString()).contains("Holdings record does not exist");
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch item is missing a status")
  void shouldReturn422_whenSynchronousBatchItemMissingStatus() {
    var itemsArray = threeItems();
    itemsArray.getJsonObject(1).remove(STATUS_KEY);

    var response = syncBatch(itemsArray);

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    itemsArray.forEach(item -> assertGetNotFound(ITEMS + "/" + ((JsonObject) item).getString("id")));
  }

  @Test
  @DisplayName("should set the status date for items created via a synchronous batch")
  void shouldSetStatusDate_forItemsCreatedViaSynchronousBatch() {
    var itemsArray = threeItems();

    assertThat(syncBatch(itemsArray).status()).isEqualTo(SC_CREATED);

    itemsArray.forEach(item -> assertThat(getItemJsonById(((JsonObject) item).getString("id"))
      .getJsonObject(STATUS_KEY).getString("date")).isNotNull());
  }

  private static void assertReturns422ForExistingId(String queryParams) {
    var itemsArray1 = threeItems();
    var itemsArray2 = threeItems();
    var existingId = itemsArray1.getJsonObject(1).getString("id");
    itemsArray2.getJsonObject(1).put("id", existingId);

    assertThat(syncBatch(queryParams, itemsArray1).status()).isEqualTo(SC_CREATED);
    assertThat(syncBatch(queryParams, itemsArray2).status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
  }

  private static JsonArray threeItemsWithId(String holdingId, UUID existingItemId) {
    // _version=1 matches the existing item's stored version after its first upsert (an insert),
    // so a second upsert reusing existingItemId is accepted as a same-version update rather than
    // rejected by optimistic locking.
    var array = new JsonArray()
      .add(minimalItemRequest(existingItemId, holdingId).put("barcode", UUID.randomUUID().toString())
        .put("_version", 1))
      .add(minimalItemRequest(UUID.randomUUID(), holdingId).put("barcode", UUID.randomUUID().toString())
        .put("_version", 1))
      .add(minimalItemRequest(UUID.randomUUID(), holdingId).put("barcode", UUID.randomUUID().toString())
        .put("_version", 1));
    for (var i = 0; i < array.size(); i++) {
      array.getJsonObject(i).put(ORDER_FIELD, i);
    }
    return array;
  }

  private static TestResponse syncBatchUnsafe(JsonArray itemsArray) {
    return await(doPost(client, ITEMS_SYNC_UNSAFE, new JsonObject().put(ITEMS_KEY, itemsArray)));
  }
}
