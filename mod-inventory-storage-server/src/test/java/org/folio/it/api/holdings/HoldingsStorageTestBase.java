package org.folio.it.api.holdings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_CREATED;
import static org.folio.HttpStatus.SC_NOT_FOUND;
import static org.folio.HttpStatus.SC_OK;
import static org.folio.HttpStatus.SC_UNPROCESSABLE_CONTENT;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.it.HoldingsStorageFixtures.createCallNumberType;
import static org.folio.it.HoldingsStorageFixtures.createHoldingsRecordsSource;
import static org.folio.it.InstanceStorageFixtures.createInstance;
import static org.folio.it.InstanceStorageFixtures.createInstanceType;
import static org.folio.it.LocationStorageFixtures.createLocation;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.it.BaseIntegrationTest;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.tools.utils.OptimisticLockingUtil;
import org.folio.support.ResourcePaths;
import org.folio.support.builders.HoldingRequestBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/**
 * Shared reference data, lifecycle, and request/assertion helpers for the
 * {@code HoldingsStorage*IT} classes (split by feature from the former monolithic
 * {@code HoldingsStorageIT} - see docs/test-quality-improvement-plan.md WS5). None of
 * this is feature-specific: every helper here is used by at least two of the split
 * classes, so it lives here rather than being duplicated or guessed at per class.
 */
abstract class HoldingsStorageTestBase extends BaseIntegrationTest {

  static final String TAG_VALUE = "test-tag";
  static final String NEW_TAG_VALUE = "new test tag";
  static final String STATISTICAL_CODE_IDS_KEY = "statisticalCodeIds";
  static final String ADMINISTRATIVE_NOTES_KEY = "administrativeNotes";
  static final String PERMANENT_LOCATION_ID_KEY = "permanentLocationId";
  static final String EFFECTIVE_LOCATION_ID_KEY = "effectiveLocationId";
  static final String HOLDINGS_RECORDS_KEY = "holdingsRecords";
  static final String TOTAL_RECORDS_KEY = "totalRecords";
  static final String INVALID_VALUE = "invalid value";
  static final String INVALID_TYPE_ERROR_MESSAGE =
    "invalid input syntax for type uuid: \"" + INVALID_VALUE + "\"";

  // The exact reference-data id CallNumberUtils keys its LC shelf-key parsing off; cannot be
  // a fresh/random id (see HoldingsStorageFixtures.createCallNumberType).
  static final String LC_CALL_NUMBER_TYPE_ID = "95467209-6d7b-468b-94df-0f5d7ad2747d";

  static String instanceTypeId;
  static String mainLibraryLocationId;
  static String annexLibraryLocationId;
  static String lcCallNumberTypeId;

  @BeforeAll
  static void seedReferenceData() {
    instanceTypeId = createInstanceType(client);
    mainLibraryLocationId = createLocation(client);
    annexLibraryLocationId = createLocation(client);
    lcCallNumberTypeId = createCallNumberType(client, LC_CALL_NUMBER_TYPE_ID, "Library of Congress classification");
  }

  @BeforeEach
  void clearHoldings() {
    runQuery("TRUNCATE TABLE instance, holdings_record, item CASCADE");
  }

  @AfterEach
  void resetHridSequenceAndOptimisticLockingOverride() {
    setHoldingsSequence(1);
    // this is process-wide, static RMB config (shared across every *IT class in the JVM) - reset
    // it after every test so a test enabling it doesn't leak into unrelated later tests
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(Map.of());
  }

  static String createInstanceRecord() {
    return createInstance(client, "an instance " + UUID.randomUUID(), instanceTypeId);
  }

  static HoldingRequestBuilder holdingRequest(String instanceId) {
    return new HoldingRequestBuilder()
      .forInstance(UUID.fromString(instanceId))
      .withSource(UUID.fromString(createHoldingsRecordsSource(client)))
      .withPermanentLocation(UUID.fromString(mainLibraryLocationId));
  }

  static HoldingRequestBuilder withHrid(HoldingRequestBuilder builder, String hrid) {
    return builder.withHrid(hrid);
  }

  static JsonObject tags(String... tagValues) {
    return new JsonObject().put("tagList", new JsonArray(List.of(tagValues)));
  }

  static JsonObject createHolding(HoldingRequestBuilder builder) {
    return createHolding(builder.create());
  }

  static JsonObject createHolding(JsonObject request) {
    var response = await(doPost(client, ResourcePaths.HOLDINGS, request));
    assertThat(response.status()).isEqualTo(SC_CREATED);
    return response.jsonBody();
  }

  static void createThreeHoldings() {
    var instanceId = createInstanceRecord();
    createHolding(holdingRequest(instanceId));
    createHolding(holdingRequest(instanceId));
    createHolding(holdingRequest(instanceId));
  }

  static TestResponse updateHolding(JsonObject holding) {
    return await(doPut(client, ResourcePaths.HOLDINGS + "/" + holding.getString("id"), holding));
  }

  static TestResponse getHoldingById(UUID id) {
    return getHoldingById(id.toString());
  }

  static TestResponse getHoldingById(String id) {
    return await(doGet(client, ResourcePaths.HOLDINGS + "/" + id));
  }

  static TestResponse patchHolding(String holdingId, JsonObject patch) {
    return await(doPatch(client, ResourcePaths.HOLDINGS + "/" + holdingId, patch));
  }

  static void assertGetNotFound(String path) {
    assertThat(await(doGet(client, path)).status()).isEqualTo(SC_NOT_FOUND);
  }

  static void assertExists(JsonObject expectedHolding) {
    var response = await(doGet(client, ResourcePaths.HOLDINGS + "/" + expectedHolding.getString("id")));
    assertThat(response.status()).isEqualTo(SC_OK);
    assertThat(response.body().toString()).contains(expectedHolding.getString("instanceId"));
  }

  static List<String> getTags(JsonObject holding) {
    return holding.getJsonObject("tags").getJsonArray("tagList").stream().map(String.class::cast).toList();
  }

  static void setHoldingsSequence(long sequenceNumber) {
    runQuery("select setval('hrid_holdings_seq'," + sequenceNumber + ",FALSE)");
  }

  static TestResponse updateOptimizeUpdatesSetting(boolean value) {
    return await(doPatch(client, "/inventory-settings/inventory.optimize-updates.enabled",
      new JsonObject().put("value", value)));
  }

  static TestResponse syncBatch(JsonArray holdingsArray) {
    return syncBatch("", holdingsArray);
  }

  static TestResponse syncBatch(String queryParams, JsonArray holdingsArray) {
    return await(doPost(client, ResourcePaths.HOLDINGS_SYNC + queryParams,
      new JsonObject().put(HOLDINGS_RECORDS_KEY, holdingsArray)));
  }

  static TestResponse syncBatchUnsafe(JsonArray holdingsArray) {
    return await(doPost(client, ResourcePaths.HOLDINGS_SYNC_UNSAFE,
      new JsonObject().put(HOLDINGS_RECORDS_KEY, holdingsArray)));
  }

  static JsonArray threeHoldingsRequest() {
    var holdingsArray = new JsonArray();
    for (int i = 0; i < 3; i++) {
      var instanceId = createInstance(client, "an instance " + UUID.randomUUID(), instanceTypeId);
      holdingsArray.add(new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("instanceId", instanceId)
        .put("sourceId", createHoldingsRecordsSource(client))
        .put("_version", 1)
        .put(PERMANENT_LOCATION_ID_KEY, mainLibraryLocationId));
    }
    return holdingsArray;
  }

  static JsonArray threeHoldingsRequestWithoutInstance() {
    var holdingsArray = new JsonArray();
    for (int i = 0; i < 3; i++) {
      holdingsArray.add(new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("instanceId", UUID.randomUUID().toString())
        .put("sourceId", createHoldingsRecordsSource(client))
        .put("_version", 1)
        .put(PERMANENT_LOCATION_ID_KEY, mainLibraryLocationId));
    }
    return holdingsArray;
  }

  static void assertHridError(TestResponse response, String hrid) {
    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    var error = response.jsonBody().mapTo(Errors.class).getErrors().getFirst();
    assertThat(error.getMessage()).contains("HRID value already exists in table holdings_record: " + hrid);
    var parameter = error.getParameters().getFirst();
    assertThat(parameter.getKey()).isEqualTo("lower(f_unaccent(jsonb ->> 'hrid'::text))");
    assertThat(parameter.getValue()).isEqualTo(hrid);
  }

  static JsonObject searchForHoldings(String cql) {
    return await(doGet(client, ResourcePaths.HOLDINGS + "?query=" + urlEncode(cql))).jsonBody();
  }

  static List<String> idsOf(JsonObject searchResponse) {
    return searchResponse.getJsonArray(HOLDINGS_RECORDS_KEY).stream()
      .map(JsonObject.class::cast)
      .map(holding -> holding.getString("id"))
      .toList();
  }

  static List<String> searchByCallNumberEyeReadable(String searchTerm) {
    var cql = "fullCallNumber==\"" + searchTerm + "\" OR callNumberAndSuffix==\"" + searchTerm
              + "\" OR callNumber==\"" + searchTerm + "\"";
    return idsOf(searchForHoldings(cql));
  }

  static String urlEncode(String value) {
    return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
  }
}
