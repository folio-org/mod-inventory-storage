package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.impl.HoldingsStorageFixtures.createLoanType;
import static org.folio.rest.impl.HoldingsStorageFixtures.createMaterialType;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstanceRelationship;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstanceRelationshipType;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstanceType;
import static org.folio.rest.impl.LocationStorageFixtures.createLocation;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.folio.rest.support.builders.InstanceRequestBuilder;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@code /inventory-view/instance-set} endpoint, which given a CQL query over
 * instances returns each matching instance optionally expanded with its holdings records,
 * items, preceding/succeeding titles, and instance relationships.
 */
class InstanceSetIT extends BaseIntegrationTest {

  private static final String INSTANCE_SETS_KEY = "instanceSets";
  private static final String ID_KEY = "id";

  // Deliberately patterned, ascending ids rather than random ones: several tests assert exact
  // sortBy-id ordering and offset/limit slicing across all seven instances, which only stays
  // deterministic if the ids themselves sort in a known order.
  private static final String INSTANCE_ID_1 = "10000000-0000-4000-8000-000000000000";
  private static final String INSTANCE_ID_2 = "20000000-0000-4000-8000-000000000000";
  private static final String INSTANCE_ID_3 = "30000000-0000-4000-8000-000000000000";
  private static final String INSTANCE_ID_4 = "40000000-0000-4000-8000-000000000000";
  private static final String INSTANCE_ID_5 = "50000000-0000-4000-8000-000000000000";
  // The "hub" instance: has holdings/items of its own and is the subject of every
  // preceding/succeeding-title and instance-relationship fixture below.
  private static final String INSTANCE_ID_6 = "60000000-0000-4000-8000-000000000000";
  // Has no holdings, items, titles, or relationships - used to assert empty-array responses.
  private static final String INSTANCE_ID_7 = "70000000-0000-4000-8000-000000000000";
  private static final String HOLDING_ID_1 = "11000000-0000-4000-8000-000000000000";
  private static final String HOLDING_ID_6A = "61000000-0000-4000-8000-000000000000";
  private static final String HOLDING_ID_6B = "62000000-0000-4000-8000-000000000000";
  private static final String ITEM_ID_1 = "11100000-0000-4000-8000-000000000000";
  private static final String ITEM_ID_6A = "61100000-0000-4000-8000-000000000000";
  private static final String ITEM_ID_6B = "61200000-0000-4000-8000-000000000000";
  private static final String ITEM_ID_6C = "62100000-0000-4000-8000-000000000000";

  private static String instanceTypeId;

  @BeforeAll
  static void seedReferenceData() {
    // WireMockExtension resets all stubs before every test METHOD (via the shared @BeforeEach),
    // but this @BeforeAll runs before any of that - the fixtures below need the stub already
    // registered, since creating a holding checks consortium membership.
    mockUserTenantsForNonConsortiumMember();

    instanceTypeId = createInstanceType(client);

    createInstance(INSTANCE_ID_1, "in1");
    createInstance(INSTANCE_ID_2, "in2");
    createInstance(INSTANCE_ID_3, "in3");
    createInstance(INSTANCE_ID_4, "in4");
    createInstance(INSTANCE_ID_5, "in5");
    createInstance(INSTANCE_ID_6, "in6");
    createInstance(INSTANCE_ID_7, "in7");

    seedHoldingsAndItems();
    seedPrecedingSucceedingTitles();
    seedInstanceRelationships();
  }

  private static void seedHoldingsAndItems() {
    final var locationId = createLocation(client);
    final var materialTypeId = createMaterialType(client);
    final var loanTypeId = createLoanType(client);

    createHolding(HOLDING_ID_1, INSTANCE_ID_1, locationId);
    createHolding(HOLDING_ID_6A, INSTANCE_ID_6, locationId);
    createHolding(HOLDING_ID_6B, INSTANCE_ID_6, locationId);

    createItem(ITEM_ID_1, HOLDING_ID_1, "111", materialTypeId, loanTypeId);
    createItem(ITEM_ID_6A, HOLDING_ID_6A, "611", materialTypeId, loanTypeId);
    createItem(ITEM_ID_6B, HOLDING_ID_6A, "612", materialTypeId, loanTypeId);
    createItem(ITEM_ID_6C, HOLDING_ID_6B, "621", materialTypeId, loanTypeId);
  }

  private static void seedPrecedingSucceedingTitles() {
    createPrecedingSucceedingTitle(INSTANCE_ID_1, INSTANCE_ID_6);
    createPrecedingSucceedingTitle(INSTANCE_ID_2, INSTANCE_ID_6);
    createPrecedingSucceedingTitle(INSTANCE_ID_6, INSTANCE_ID_3);
    createPrecedingSucceedingTitle(INSTANCE_ID_6, INSTANCE_ID_4);
  }

  private static void seedInstanceRelationships() {
    final var relationshipTypeId = createInstanceRelationshipType(client);

    createInstanceRelationship(client, INSTANCE_ID_1, INSTANCE_ID_6, relationshipTypeId);
    createInstanceRelationship(client, INSTANCE_ID_4, INSTANCE_ID_6, relationshipTypeId);
    createInstanceRelationship(client, INSTANCE_ID_6, INSTANCE_ID_3, relationshipTypeId);
    createInstanceRelationship(client, INSTANCE_ID_6, INSTANCE_ID_5, relationshipTypeId);
  }

  @Test
  @DisplayName("should return all instances when no CQL query is given")
  void shouldReturnAllInstances_whenNoCqlQueryGiven() {
    assertThat(getInstanceSets(null)).hasSize(7);
  }

  @Test
  @DisplayName("should return matching instances when querying by two ids")
  void shouldReturnMatchingInstances_whenQueryingByTwoIds() {
    var sets = getInstanceSets("id==" + INSTANCE_ID_3 + " OR id==" + INSTANCE_ID_5 + " sortBy id");
    assertThat(ids(sets)).containsExactly(INSTANCE_ID_3, INSTANCE_ID_5);
  }

  @Test
  @DisplayName("should return the matching instance when querying by hrid")
  void shouldReturnMatchingInstance_whenQueryingByHrid() {
    var sets = getInstanceSets("hrid==in2");
    assertThat(ids(sets)).containsExactly(INSTANCE_ID_2);
  }

  @Test
  @DisplayName("should return the matching instance when querying by item barcode")
  void shouldReturnMatchingInstance_whenQueryingByItemBarcode() {
    var sets = getInstanceSets("item.barcode==111");
    assertThat(ids(sets)).containsExactly(INSTANCE_ID_1);
  }

  @Test
  @DisplayName("should return 400 when the CQL query is invalid")
  void shouldReturn400_whenCqlQueryIsInvalid() {
    var response = get(doGet(client, ResourcePaths.INVENTORY_VIEW_INSTANCE_SET + "?limit=1&query=id=="));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should respect limit and offset when sorting ascending or descending")
  void shouldRespectLimitAndOffset_whenSortingAscendingOrDescending() {
    var ascending = getInstanceSets("cql.allRecords=1 sortBy id/sort.ascending", "", 2, 3);
    assertThat(ids(ascending)).containsExactly(INSTANCE_ID_4, INSTANCE_ID_5);

    var descending = getInstanceSets("cql.allRecords=1 sortBy id/sort.descending", "", 2, 3);
    assertThat(ids(descending)).containsExactly(INSTANCE_ID_4, INSTANCE_ID_3);
  }

  @Test
  @DisplayName("should return empty arrays when an instance has no related records")
  void shouldReturnEmptyArrays_whenInstanceHasNoRelatedRecords() {
    var parameters = "&holdingsRecords=true&items=true"
      + "&precedingTitles=true&succeedingTitles=true"
      + "&superInstanceRelationships=true&subInstanceRelationships=true";
    var set = getInstanceSets("id==" + INSTANCE_ID_7, parameters, 1, 0).getJsonObject(0);

    // .size() fails if the JsonArray is null
    assertThat(set.getJsonArray("holdingsRecords")).isEmpty();
    assertThat(set.getJsonArray("items")).isEmpty();
    assertThat(set.getJsonArray("precedingTitles")).isEmpty();
    assertThat(set.getJsonArray("succeedingTitles")).isEmpty();
    assertThat(set.getJsonArray("superInstanceRelationships")).isEmpty();
    assertThat(set.getJsonArray("subInstanceRelationships")).isEmpty();
  }

  @Test
  @DisplayName("should include the instance when the instance parameter is true")
  void shouldIncludeInstance_whenInstanceParameterIsTrue() {
    var set = getHubInstanceSet("instance=true");

    assertThat(set.fieldNames()).containsExactlyInAnyOrder(ID_KEY, "instance");
    assertThat(set.getJsonObject("instance").getString(ID_KEY)).isEqualTo(INSTANCE_ID_6);
  }

  @Test
  @DisplayName("should include holdings records when the holdingsRecords parameter is true")
  void shouldIncludeHoldingsRecords_whenHoldingsRecordsParameterIsTrue() {
    var set = getHubInstanceSet("holdingsRecords=true");

    assertThat(set.fieldNames()).containsExactlyInAnyOrder(ID_KEY, "holdingsRecords");
    assertThat(ids(set.getJsonArray("holdingsRecords"))).containsExactlyInAnyOrder(HOLDING_ID_6A, HOLDING_ID_6B);
  }

  @Test
  @DisplayName("should include items when the items parameter is true")
  void shouldIncludeItems_whenItemsParameterIsTrue() {
    var set = getHubInstanceSet("items=true");

    assertThat(set.fieldNames()).containsExactlyInAnyOrder(ID_KEY, "items");
    assertThat(ids(set.getJsonArray("items"))).containsExactlyInAnyOrder(ITEM_ID_6A, ITEM_ID_6B, ITEM_ID_6C);
  }

  @Test
  @DisplayName("should include preceding titles when the precedingTitles parameter is true")
  void shouldIncludePrecedingTitles_whenPrecedingTitlesParameterIsTrue() {
    var set = getHubInstanceSet("precedingTitles=true");

    assertThat(set.fieldNames()).containsExactlyInAnyOrder(ID_KEY, "precedingTitles");
    assertThat(fieldValues(set, "precedingTitles", "precedingInstanceId"))
      .containsExactlyInAnyOrder(INSTANCE_ID_1, INSTANCE_ID_2);
  }

  @Test
  @DisplayName("should include succeeding titles when the succeedingTitles parameter is true")
  void shouldIncludeSucceedingTitles_whenSucceedingTitlesParameterIsTrue() {
    var set = getHubInstanceSet("succeedingTitles=true");

    assertThat(set.fieldNames()).containsExactlyInAnyOrder(ID_KEY, "succeedingTitles");
    assertThat(fieldValues(set, "succeedingTitles", "succeedingInstanceId"))
      .containsExactlyInAnyOrder(INSTANCE_ID_3, INSTANCE_ID_4);
  }

  @Test
  @DisplayName("should include super-instance relationships when the superInstanceRelationships parameter is true")
  void shouldIncludeSuperInstanceRelationships_whenSuperInstanceRelationshipsParameterIsTrue() {
    var set = getHubInstanceSet("superInstanceRelationships=true");

    assertThat(set.fieldNames()).containsExactlyInAnyOrder(ID_KEY, "superInstanceRelationships");
    assertThat(fieldValues(set, "superInstanceRelationships", "superInstanceId"))
      .containsExactlyInAnyOrder(INSTANCE_ID_1, INSTANCE_ID_4);
  }

  @Test
  @DisplayName("should include sub-instance relationships when the subInstanceRelationships parameter is true")
  void shouldIncludeSubInstanceRelationships_whenSubInstanceRelationshipsParameterIsTrue() {
    var set = getHubInstanceSet("subInstanceRelationships=true");

    assertThat(set.fieldNames()).containsExactlyInAnyOrder(ID_KEY, "subInstanceRelationships");
    assertThat(fieldValues(set, "subInstanceRelationships", "subInstanceId"))
      .containsExactlyInAnyOrder(INSTANCE_ID_3, INSTANCE_ID_5);
  }

  private static void createInstance(String id, String hrid) {
    var request = new InstanceRequestBuilder()
      .withId(UUID.fromString(id))
      .withTitle("Instance " + hrid)
      .withSource("TEST")
      .withInstanceTypeId(UUID.fromString(instanceTypeId))
      .create()
      .put("hrid", hrid);

    get(doPost(client, ResourcePaths.INSTANCES, request));
  }

  private static void createHolding(String id, String instanceId, String locationId) {
    var request = new HoldingRequestBuilder()
      .withId(UUID.fromString(id))
      .forInstance(UUID.fromString(instanceId))
      .withPermanentLocation(UUID.fromString(locationId))
      .withSource(UUID.fromString(HoldingsStorageFixtures.createHoldingsRecordsSource(client)))
      .create();

    get(doPost(client, ResourcePaths.HOLDINGS, request));
  }

  private static void createItem(String id, String holdingId, String barcode, String materialTypeId,
                                  String loanTypeId) {
    var request = new ItemRequestBuilder()
      .withId(UUID.fromString(id))
      .forHolding(UUID.fromString(holdingId))
      .withBarcode(barcode)
      .withMaterialType(UUID.fromString(materialTypeId))
      .withPermanentLoanType(UUID.fromString(loanTypeId))
      .create();

    get(doPost(client, ResourcePaths.ITEMS, request));
  }

  private static void createPrecedingSucceedingTitle(String precedingInstanceId, String succeedingInstanceId) {
    var request = new JsonObject()
      .put("precedingInstanceId", precedingInstanceId)
      .put("succeedingInstanceId", succeedingInstanceId);

    get(doPost(client, ResourcePaths.PRECEDING_SUCCEEDING_TITLES, request));
  }

  private JsonArray getInstanceSets(String cql) {
    return getInstanceSets(cql, "", 10, 0);
  }

  private JsonArray getInstanceSets(String cql, String parameters, int limit, int offset) {
    var query = cql == null ? "" : "&query=" + urlEncode(cql);
    var path = ResourcePaths.INVENTORY_VIEW_INSTANCE_SET + "?limit=" + limit + "&offset=" + offset
      + query + parameters;

    return get(doGet(client, path)).jsonBody().getJsonArray(INSTANCE_SETS_KEY);
  }

  private JsonObject getHubInstanceSet(String parameters) {
    var path = ResourcePaths.INVENTORY_VIEW_INSTANCE_SET + "?limit=10&query=id==" + INSTANCE_ID_6 + "&" + parameters;
    var sets = get(doGet(client, path)).jsonBody().getJsonArray(INSTANCE_SETS_KEY);

    assertThat(ids(sets)).containsExactly(INSTANCE_ID_6);
    return sets.getJsonObject(0);
  }

  private List<String> ids(JsonArray sets) {
    return sets.stream().map(o -> ((JsonObject) o).getString(ID_KEY)).toList();
  }

  private List<String> fieldValues(JsonObject set, String arrayName, String fieldName) {
    return set.getJsonArray(arrayName).stream().map(o -> ((JsonObject) o).getString(fieldName)).toList();
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
