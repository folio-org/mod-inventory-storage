package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.impl.HoldingsStorageFixtures.createHolding;
import static org.folio.rest.impl.HoldingsStorageFixtures.createItem;
import static org.folio.rest.impl.HoldingsStorageFixtures.createLoanType;
import static org.folio.rest.impl.HoldingsStorageFixtures.createMaterialType;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstanceType;
import static org.folio.rest.impl.LocationStorageFixtures.createLocation;

import io.vertx.core.json.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the {@code /record-bulk/ids} streaming endpoint, which returns bare instance or holdings
 * record ids (optionally CQL-filtered) for downstream bulk processing.
 */
class RecordBulkIT extends BaseIntegrationTest {

  private static final String ID_FIELD = "id";
  private static final String IDS_FIELD = "ids";
  private static final String TOTAL_RECORDS_FIELD = "totalRecords";

  private static String instanceTypeId;
  private static String materialTypeId;
  private static String loanTypeId;

  @BeforeAll
  static void seedReferenceData() {
    instanceTypeId = createInstanceType(client);
    materialTypeId = createMaterialType(client);
    loanTypeId = createLoanType(client);
  }

  @BeforeEach
  void clearRecords() {
    runQuery("TRUNCATE TABLE instance, holdings_record, item CASCADE");
  }

  @Test
  @DisplayName("should get instance ids using default query parameters")
  void shouldGetInstanceIds_usingDefaults() {
    var moonIds = createMoons(2);

    var response = get(doGet(client, ResourcePaths.RECORD_BULK_IDS));

    assertIdsResponse(response, moonIds);
  }

  @Test
  @DisplayName("should get instance ids when the field parameter is explicitly set to id")
  void shouldGetInstanceIds_whenFieldParameterIsId() {
    var moonIds = createMoons(2);

    var response = get(doGet(client, ResourcePaths.RECORD_BULK_IDS + "?field=id"));

    assertIdsResponse(response, moonIds);
  }

  @Test
  @DisplayName("should get a page of instance ids when limit and offset are provided")
  void shouldGetInstanceIds_withLimitAndOffset() {
    var moonIds = createMoons(20);

    var response = get(doGet(client, ResourcePaths.RECORD_BULK_IDS + "?limit=5&offset=0"));

    assertThat(response.status()).isEqualTo(SC_OK);
    var ids = returnedIds(response);
    assertThat(ids).hasSize(5);
    assertThat(moonIds).containsAll(ids);
  }

  @Test
  @DisplayName("should get instance ids filtered by the effective location of their items")
  void shouldGetInstanceIds_filteredByItemsEffectiveLocation() {
    var expectedLocationId = createLocation(client);
    var otherLocationId = createLocation(client);

    var expectedInstanceId = createInstance("Instance with matching item location");
    var expectedHoldingId = createHolding(client, expectedInstanceId, expectedLocationId);
    createItem(client, expectedHoldingId, materialTypeId, loanTypeId);

    var otherInstanceId = createInstance("Instance with other item location");
    var otherHoldingId = createHolding(client, otherInstanceId, otherLocationId);
    createItem(client, otherHoldingId, materialTypeId, loanTypeId);

    var query = "?query=" + urlEncode("(items.effectiveLocationId==\"" + expectedLocationId + "\")");
    var response = get(doGet(client, ResourcePaths.RECORD_BULK_IDS + query));

    assertIdsResponse(response, Set.of(expectedInstanceId));
  }

  @Test
  @DisplayName("should get instance ids matching an exact title query")
  void shouldGetInstanceIds_matchingExactTitleQuery() {
    createMoons(10);

    var query = "?query=" + urlEncode("title all \"Moon #3\"");
    var response = get(doGet(client, ResourcePaths.RECORD_BULK_IDS + query));

    assertThat(response.status()).isEqualTo(SC_OK);
    assertThat(returnedIds(response)).hasSize(1);
    assertThat(response.jsonBody().getInteger(TOTAL_RECORDS_FIELD)).isEqualTo(1);
  }

  @Test
  @DisplayName("should return no instance ids when the keyword query matches nothing")
  void shouldReturnNoInstanceIds_whenKeywordQueryMatchesNothing() {
    createMoons(2);

    var query = "?query=" + urlEncode("keyword all \"Planet #1*\"");
    var response = get(doGet(client, ResourcePaths.RECORD_BULK_IDS + query));

    assertIdsResponse(response, Set.of());
  }

  @Test
  @DisplayName("should get holdings record ids")
  void shouldGetHoldingsIds() {
    var holdingIds = createHoldings(2);

    var response = get(doGet(client, ResourcePaths.RECORD_BULK_IDS + "?recordType=HOLDING"));

    assertIdsResponse(response, holdingIds);
  }

  @Test
  @DisplayName("should get a page of holdings record ids when limit and offset are provided")
  void shouldGetHoldingsIds_withLimitAndOffset() {
    var holdingIds = createHoldings(20);

    var response = get(doGet(client, ResourcePaths.RECORD_BULK_IDS + "?recordType=HOLDING&limit=5"));

    assertThat(response.status()).isEqualTo(SC_OK);
    var ids = returnedIds(response);
    assertThat(ids).hasSize(5);
    assertThat(holdingIds).containsAll(ids);
  }

  private static void assertIdsResponse(TestResponse response, Set<String> expectedIds) {
    assertThat(response.status()).isEqualTo(SC_OK);
    assertThat(returnedIds(response)).containsExactlyInAnyOrderElementsOf(expectedIds);
    assertThat(response.jsonBody().getInteger(TOTAL_RECORDS_FIELD)).isEqualTo(expectedIds.size());
  }

  private static Set<String> returnedIds(TestResponse response) {
    var ids = new HashSet<String>();
    response.jsonBody().getJsonArray(IDS_FIELD).forEach(id -> ids.add(((JsonObject) id).getString(ID_FIELD)));
    return ids;
  }

  private static String createInstance(String title) {
    var id = UUID.randomUUID();
    var request = new JsonObject()
      .put(ID_FIELD, id.toString())
      .put("source", "TEST")
      .put("title", title)
      .put("instanceTypeId", instanceTypeId)
      .put("hrid", "in" + id.toString().replace("-", "").substring(0, 11));

    get(doPost(client, ResourcePaths.INSTANCES, request));

    return id.toString();
  }

  private static Set<String> createMoons(int total) {
    var moonIds = new HashSet<String>();
    for (var i = 0; i < total; i++) {
      moonIds.add(createInstance("Moon #" + i));
    }
    return moonIds;
  }

  private static Set<String> createHoldings(int total) {
    var holdingIds = new HashSet<String>();
    var locationId = createLocation(client);
    for (var i = 0; i < total; i++) {
      var instanceId = createInstance("Instance for holding #" + i);
      holdingIds.add(createHolding(client, instanceId, locationId));
    }
    return holdingIds;
  }

  private static String urlEncode(String value) {
    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
