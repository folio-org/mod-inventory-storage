package org.folio.it.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_BAD_REQUEST;
import static org.folio.HttpStatus.SC_CONFLICT;
import static org.folio.HttpStatus.SC_CREATED;
import static org.folio.HttpStatus.SC_REQUEST_TOO_LONG;
import static org.folio.HttpStatus.SC_UNPROCESSABLE_CONTENT;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.it.HoldingsStorageFixtures.createHoldingsRecordsSource;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.Map;
import java.util.Set;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.tools.utils.OptimisticLockingUtil;
import org.folio.support.ResourcePaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HoldingsStorageBatchIT extends HoldingsStorageTestBase {

  @Test
  @DisplayName("should return 413 when an unsafe synchronous batch is not allowed")
  void shouldReturn413_whenUnsafeBatchNotAllowed() {
    // not allowed because DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING is not configured
    var response = syncBatchUnsafe(threeHoldingsRequest());

    assertThat(response.status()).isEqualTo(SC_REQUEST_TOO_LONG);
  }

  @Test
  @DisplayName("should allow an unsafe synchronous batch update when configured")
  void shouldAllowUnsafeBatchUpdate_whenConfigured() {
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
      Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));
    var holdingsArray = threeHoldingsRequest();
    assertThat(syncBatchUnsafe(holdingsArray).status()).isEqualTo(SC_CREATED);

    holdingsArray.getJsonObject(1).put("copyNumber", "456");
    assertThat(syncBatchUnsafe(holdingsArray).status()).isEqualTo(SC_CREATED);

    // safe update, env var should not influence the regular API
    holdingsArray.getJsonObject(1).put("copyNumber", "789");
    assertThat(syncBatch("?upsert=true", holdingsArray).status()).isEqualTo(SC_CONFLICT);
  }

  @Test
  @DisplayName("should return 400 when an unsafe synchronous batch has an invalid statistical code id")
  void shouldReturn400_whenUnsafeBatchHasInvalidStatisticalCodeId() {
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
      Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));
    var holdingsArray = threeHoldingsRequest();
    holdingsArray.getJsonObject(1).put(STATISTICAL_CODE_IDS_KEY, Set.of(INVALID_VALUE));

    var response = syncBatchUnsafe(holdingsArray);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains(INVALID_TYPE_ERROR_MESSAGE);
  }

  @Test
  @DisplayName("should return 400 when a synchronous batch has an invalid statistical code id")
  void shouldReturn400_whenBatchHasInvalidStatisticalCodeId() {
    var holdingsArray = threeHoldingsRequest();
    holdingsArray.getJsonObject(1).put(STATISTICAL_CODE_IDS_KEY, Set.of(INVALID_VALUE));

    var response = syncBatch(holdingsArray);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains(INVALID_TYPE_ERROR_MESSAGE);
  }

  @Test
  @DisplayName("should create holdings via a synchronous batch")
  void shouldCreateHoldings_viaSynchronousBatch() {
    var holdingsArray = threeHoldingsRequest();

    assertThat(syncBatch(holdingsArray).status()).isEqualTo(SC_CREATED);

    holdingsArray.forEach(holding -> assertExists((JsonObject) holding));
  }

  @Test
  @DisplayName("should create a holding without an id via a synchronous batch with upsert=true")
  void shouldCreateHolding_viaSynchronousBatchWithoutIdAndUpsertTrue() {
    var instanceId = createInstanceRecord();
    var request = new JsonObject()
      .put("instanceId", instanceId)
      .put("sourceId", createHoldingsRecordsSource(client))
      .put("_version", 1)
      .put("callNumber", "test-call-number")
      .put(PERMANENT_LOCATION_ID_KEY, mainLibraryLocationId);

    assertThat(syncBatch("?upsert=true", new JsonArray().add(request)).status()).isEqualTo(SC_CREATED);

    var found = await(doGet(client, ResourcePaths.HOLDINGS + "?query=callNumber=test-call-number")).jsonBody()
      .getJsonArray(HOLDINGS_RECORDS_KEY).getJsonObject(0);
    assertThat(found.getString("id")).isNotNull();
    assertThat(found.getString("callNumber")).isEqualTo("test-call-number");
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch references a non-existing instance at a "
               + "non-consortium tenant")
  void shouldReturn422_whenBatchInstanceDoesNotExist() {
    var holdingsArray = threeHoldingsRequestWithoutInstance();

    var response = syncBatch(holdingsArray);

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    var error = response.jsonBody().mapTo(Errors.class).getErrors().getFirst();
    assertThat(error.getMessage()).matches("Cannot set holdings_record.instanceid = \\S+ "
                                           + "because it does not exist in instance.id.");
    assertThat(error.getParameters().getFirst().getKey()).isEqualTo("holdings_record.instanceid");
    holdingsArray.forEach(holding ->
      assertGetNotFound(ResourcePaths.HOLDINGS + "/" + ((JsonObject) holding).getString("id")));
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch has a duplicate id")
  void shouldReturn422_whenBatchHasDuplicateId() {
    var holdingsArray = threeHoldingsRequest();
    var duplicateId = holdingsArray.getJsonObject(0).getString("id");
    holdingsArray.getJsonObject(1).put("id", duplicateId);

    var response = syncBatch(holdingsArray);

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    holdingsArray.forEach(holding ->
      assertGetNotFound(ResourcePaths.HOLDINGS + "/" + ((JsonObject) holding).getString("id")));
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch reuses an existing id without upsert")
  void shouldReturn422_whenBatchReusesExistingId_withoutUpsert() {
    assertReturns422ForExistingId("");
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch reuses an existing id with upsert=false")
  void shouldReturn422_whenBatchReusesExistingId_withUpsertFalse() {
    assertReturns422ForExistingId("?upsert=false");
  }

  static void assertReturns422ForExistingId(String queryParams) {
    var holdingsArray1 = threeHoldingsRequest();
    var holdingsArray2 = threeHoldingsRequest();
    var existingId = holdingsArray1.getJsonObject(1).getString("id");
    holdingsArray2.getJsonObject(1).put("id", existingId);

    assertThat(syncBatch(queryParams, holdingsArray1).status()).isEqualTo(SC_CREATED);
    assertThat(syncBatch(queryParams, holdingsArray2).status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
  }
}
