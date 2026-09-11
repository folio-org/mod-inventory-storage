package org.folio.it.api;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.it.HoldingsStorageFixtures.createHoldingsRecordsSource;
import static org.folio.it.HoldingsStorageFixtures.createLoanType;
import static org.folio.it.HoldingsStorageFixtures.createMaterialType;
import static org.folio.it.InstanceStorageFixtures.createInstance;
import static org.folio.it.InstanceStorageFixtures.createInstanceType;
import static org.folio.it.LocationStorageFixtures.createLocation;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.UUID;
import org.folio.it.BaseIntegrationTest;
import org.folio.it.HoldingsStorageFixtures;
import org.folio.support.ResourcePaths;
import org.folio.support.builders.HoldingRequestBuilder;
import org.folio.support.builders.ItemRequestBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CQL search/filter tests for {@code /item-storage/items}: barcode, tags, status,
 * discovery-suppress, effective location, call number, and purchase-order-line-identifier
 * indexes. Split out of {@link ItemStorageIT} because this cluster of tests is large and shares
 * a distinct "find items by X" concern.
 */
class ItemSearchIT extends BaseIntegrationTest {

  private static final String DISCOVERY_SUPPRESS = "discoverySuppress";
  private static final String ITEMS_KEY = "items";

  private static String instanceTypeId;
  private static String materialTypeId;
  private static String loanTypeId;
  private static String mainLibraryLocationId;
  private static String annexLibraryLocationId;
  private static String onlineLocationId;
  private static String secondFloorLocationId;

  @BeforeAll
  static void seedReferenceData() {
    instanceTypeId = createInstanceType(client);
    materialTypeId = createMaterialType(client);
    loanTypeId = createLoanType(client);
    mainLibraryLocationId = createLocation(client);
    annexLibraryLocationId = createLocation(client);
    onlineLocationId = createLocation(client);
    secondFloorLocationId = createLocation(client);
  }

  @BeforeEach
  void clearItems() {
    runQuery("TRUNCATE TABLE instance, holdings_record, item CASCADE");
  }

  @Test
  @DisplayName("should search for items by barcode with a leading zero")
  void shouldSearchForItems_byBarcodeWithLeadingZero() {
    var holdingId = createHoldingRecord(mainLibraryLocationId);
    createItem(itemRequest(holdingId));
    createItem(itemRequest(holdingId).put("barcode", "36000291452"));
    createItem(itemRequest(holdingId).put("barcode", "036000291452"));

    assertBarcodesFound("barcode==036000291452", "036000291452");
    assertBarcodesFound("barcode==36000291452", "36000291452");
  }

  @Test
  @DisplayName("should search for items by barcode")
  void shouldSearchForItems_byBarcode() {
    var holdingId = createHoldingRecord(mainLibraryLocationId);
    createItem(itemRequest(holdingId).put("barcode", "123456a"));
    createItem(itemRequest(holdingId).put("barcode", "123456ä"));
    createItem(itemRequest(holdingId).put("barcode", "673274826203"));

    assertBarcodesFound("barcode==673274826203", "673274826203");
    // respect accents, ignore case
    assertBarcodesFound("barcode==123456a", "123456a");
    assertBarcodesFound("barcode==123456A", "123456a");
    assertBarcodesFound("barcode==123456ä", "123456ä");
    assertBarcodesFound("barcode==123456Ä", "123456ä");
    assertBarcodesFound("barcode==123456*", "123456a", "123456ä");
  }

  @Test
  @DisplayName("should search for items by tags")
  void shouldSearchForItems_byTags() {
    var holdingId = createHoldingRecord(mainLibraryLocationId);
    var tagValue = "test-tag";
    createItem(itemRequest(holdingId).put("tags", new JsonObject().put("tagList", new JsonArray().add(tagValue))));
    createItem(itemRequest(holdingId));

    var response = searchForItems("tags.tagList=" + tagValue);

    var foundItems = response.getJsonArray(ITEMS_KEY);
    assertThat(foundItems).hasSize(1);
    assertThat(response.getInteger("totalRecords")).isEqualTo(1);
    var tags = foundItems.getJsonObject(0).getJsonObject("tags").getJsonArray("tagList");
    assertThat(tags).contains(tagValue);
  }

  @Test
  @DisplayName("should search for items by status")
  void shouldSearchForItems_byStatus() {
    var holdingId = createHoldingRecord(mainLibraryLocationId);
    for (var i = 0; i < 5; i++) {
      createItem(itemRequest(holdingId).put("barcode", UUID.randomUUID().toString()));
    }

    var response = searchForItems("status.name==\"Available\"");

    assertThat(response.getInteger("totalRecords")).isEqualTo(5);
    assertThat(response.getJsonArray(ITEMS_KEY)).hasSize(5);
  }

  @Test
  @DisplayName("should find only the item matching both barcode and a non-matching id exclusion")
  void shouldFindOnlyItem_matchingBarcodeAndNotMatchingId() {
    var holdingId = createHoldingRecord(mainLibraryLocationId);
    createItem(itemRequest(holdingId));
    createItem(itemRequest(holdingId).put("barcode", "673274826203"));

    var response = searchForItems("barcode==\"673274826203\" and id<>" + UUID.randomUUID() + "'");

    var foundItems = response.getJsonArray(ITEMS_KEY);
    assertThat(foundItems).hasSize(1);
    assertThat(response.getInteger("totalRecords")).isEqualTo(1);
    assertThat(foundItems.getJsonObject(0).getString("barcode")).isEqualTo("673274826203");
  }

  @Test
  @DisplayName("should search for many items by barcode without a stack overflow")
  void shouldSearchForManyItems_byBarcode() {
    var holdingId = createHoldingRecord(mainLibraryLocationId);
    var tagValue = "test-tag";
    createItem(itemRequest(holdingId).put("barcode", "673274826203")
      .put("tags", new JsonObject().put("tagList", new JsonArray().add(tagValue))));

    // StackOverflowError in java.util.regex.Pattern https://issues.folio.org/browse/CIRC-119
    var response = searchForItems("barcode==(a or b or c or d or e or f or g or h or j or k or l or m or n or o "
      + "or p or q or s or t or u or v or w or x or y or z or 673274826203)");

    var foundItems = response.getJsonArray(ITEMS_KEY);
    assertThat(foundItems).hasSize(1);
    assertThat(response.getInteger("totalRecords")).isEqualTo(1);
    assertThat(foundItems.getJsonObject(0).getString("barcode")).isEqualTo("673274826203");
    assertThat(foundItems.getJsonObject(0).getJsonObject("tags").getJsonArray("tagList")).contains(tagValue);
  }

  @Test
  @DisplayName("should search items by effective location")
  void shouldSearchItems_byEffectiveLocation() {
    var permLocationHoldingId = createHoldingRecord(mainLibraryLocationId);
    var tempLocationHoldingId = createHoldingRecordWithTemporaryLocation(mainLibraryLocationId, annexLibraryLocationId);

    var itemWithHoldingPermLocation = createItem(itemRequest(permLocationHoldingId));
    var itemWithHoldingTempLocation = createItem(itemRequest(tempLocationHoldingId));
    var itemWithItemTempLocation = createItem(itemRequest(permLocationHoldingId).put("temporaryLocationId",
      onlineLocationId));
    var itemWithItemPermLocation = createItem(itemRequest(tempLocationHoldingId).put("permanentLocationId",
      secondFloorLocationId));
    var itemWithBothItemLocations = createItem(itemRequest(tempLocationHoldingId)
      .put("permanentLocationId", secondFloorLocationId).put("temporaryLocationId", onlineLocationId));

    assertThat(idsOf(searchForItems("effectiveLocationId==" + mainLibraryLocationId)))
      .containsExactly(itemWithHoldingPermLocation.getString("id"));
    assertThat(idsOf(searchForItems("effectiveLocationId==" + annexLibraryLocationId)))
      .containsExactly(itemWithHoldingTempLocation.getString("id"));
    assertThat(idsOf(searchForItems("effectiveLocationId==" + onlineLocationId)))
      .containsExactlyInAnyOrder(itemWithItemTempLocation.getString("id"), itemWithBothItemLocations.getString("id"));
    assertThat(idsOf(searchForItems("effectiveLocationId==" + secondFloorLocationId)))
      .containsExactly(itemWithItemPermLocation.getString("id"));
  }

  @Test
  @DisplayName("should return 400 when searching without specifying an index")
  void shouldReturn400_whenSearchingWithoutAnIndex() {
    var holdingId = createHoldingRecord(mainLibraryLocationId);
    createItem(itemRequest(holdingId));

    var response = await(doGet(client, ResourcePaths.ITEMS + "?query=t"));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString())
      .contains("QueryValidationException: cql.serverChoice requested, but no serverChoiceIndexes defined.");
  }

  @Test
  @DisplayName("should filter by the full call number")
  void shouldFilterByFullCallNumber() {
    var holdingId = createHoldingRecord(mainLibraryLocationId);
    var itemWithWholeCallNumber = createItem(itemWithCallNumber(holdingId, "prefix", "callNumber", "suffix"));
    createItem(itemWithCallNumber(holdingId, "prefix", "callNumber", null));
    createItem(itemWithCallNumber(holdingId, "prefix", "differentCallNumber", null));

    var found = searchForItems("fullCallNumber == \"prefix callNumber suffix\"");

    assertThat(idsOf(found)).containsExactly(itemWithWholeCallNumber.getString("id"));
  }

  @Test
  @DisplayName("should filter by the call number and suffix")
  void shouldFilterByCallNumberAndSuffix() {
    var holdingId = createHoldingRecord(mainLibraryLocationId);
    var itemWithWholeCallNumber = createItem(itemWithCallNumber(holdingId, "prefix", "callNumber", "suffix"));
    createItem(itemWithCallNumber(holdingId, "prefix", "callNumber", null));
    var itemNoPrefix = createItem(itemWithCallNumber(holdingId, null, "callNumber", "suffix"));

    var found = searchForItems("callNumberAndSuffix == \"callNumber suffix\"");

    assertThat(idsOf(found)).containsExactlyInAnyOrder(
      itemWithWholeCallNumber.getString("id"), itemNoPrefix.getString("id"));
  }

  @Test
  @DisplayName("should search by the discoverySuppress property")
  void shouldSearchByDiscoverySuppressProperty() {
    var holdingId = createHoldingRecord(mainLibraryLocationId);
    var suppressed = createItem(itemRequest(holdingId).put(DISCOVERY_SUPPRESS, true));
    var notSuppressed = createItem(itemRequest(holdingId).put(DISCOVERY_SUPPRESS, false));
    var notSuppressedDefault = createItem(itemRequest(holdingId));

    var suppressedItems = idsOf(searchForItems(DISCOVERY_SUPPRESS + "==true"));
    var notSuppressedItems = idsOf(searchForItems("cql.allRecords=1 not " + DISCOVERY_SUPPRESS + "==true"));

    assertThat(suppressedItems).containsExactly(suppressed.getString("id"));
    assertThat(notSuppressedItems)
      .containsExactlyInAnyOrder(notSuppressed.getString("id"), notSuppressedDefault.getString("id"));
  }

  @Test
  @DisplayName("should find an item by call number when there is a suffix")
  void shouldFindItem_byCallNumberWithSuffix() {
    var holdingId = createHoldingRecord(mainLibraryLocationId);
    var first = createItem(itemWithCallNumber(holdingId, null, "GE77 .F73 2014", null));
    var second = createItem(itemWithCallNumber(holdingId, null, "GE77 .F73 2014", "Curriculum Materials Collection"));
    createItem(itemWithCallNumber(holdingId, null, "GE77 .F73 ", "2014 Curriculum Materials Collection"));

    var found = searchByCallNumberEyeReadable("GE77 .F73 2014");

    assertThat(found).containsExactlyInAnyOrder(first.getString("id"), second.getString("id"));
  }

  @Test
  @DisplayName("should filter items with call number explicit right truncation")
  void shouldFilterItems_withCallNumberExplicitRightTruncation() {
    var holdingId = createHoldingRecord(mainLibraryLocationId);
    var first = createItem(itemWithCallNumber(holdingId, null, "GE77 .F73 2014", null));
    var second = createItem(itemWithCallNumber(holdingId, null, "GE77 .F73 2014", "Curriculum Materials Collection"));
    var third = createItem(itemWithCallNumber(holdingId, null, "GE77 .F73 ", "2014 Curriculum Materials Collection"));
    createItem(itemWithCallNumber(holdingId, null, "GE77 .F74 ", "2014 Curriculum Materials Collection"));

    var found = searchByCallNumberEyeReadable("GE77 .F73*");

    assertThat(found).containsExactlyInAnyOrder(first.getString("id"), second.getString("id"), third.getString("id"));
  }

  @Test
  @DisplayName("should search by the purchase order line identifier")
  void shouldSearchByPurchaseOrderLineIdentifier() {
    var holdingId = createHoldingRecord(mainLibraryLocationId);
    var firstItem = createItem(itemRequest(holdingId).put("purchaseOrderLineIdentifier", "poli-1"));
    createItem(itemRequest(holdingId).put("purchaseOrderLineIdentifier", "poli-2"));

    var found = searchForItems("purchaseOrderLineIdentifier==\"poli-1\"");

    assertThat(idsOf(found)).containsExactly(firstItem.getString("id"));
  }

  // -- shared helpers --

  private static String createHoldingRecord(String locationId) {
    var instanceId = createInstance(client, "an instance " + UUID.randomUUID(), instanceTypeId);
    return HoldingsStorageFixtures.createHolding(client, instanceId, locationId);
  }

  private static String createHoldingRecordWithTemporaryLocation(String permanentLocationId,
                                                                   String temporaryLocationId) {
    var instanceId = createInstance(client, "an instance " + UUID.randomUUID(), instanceTypeId);
    var request = new HoldingRequestBuilder().forInstance(UUID.fromString(instanceId))
      .withSource(UUID.fromString(createHoldingsRecordsSource(client)))
      .withPermanentLocation(UUID.fromString(permanentLocationId))
      .withTemporaryLocation(UUID.fromString(temporaryLocationId)).create();
    return await(doPost(client, ResourcePaths.HOLDINGS, request)).jsonBody().getString("id");
  }

  private static JsonObject itemRequest(String holdingId) {
    return new ItemRequestBuilder().forHolding(UUID.fromString(holdingId))
      .withMaterialType(UUID.fromString(materialTypeId)).withPermanentLoanType(UUID.fromString(loanTypeId))
      .create();
  }

  private static JsonObject itemWithCallNumber(String holdingId, String prefix, String callNumber, String suffix) {
    var builder = new ItemRequestBuilder().forHolding(UUID.fromString(holdingId))
      .withMaterialType(UUID.fromString(materialTypeId)).withPermanentLoanType(UUID.fromString(loanTypeId))
      .withItemLevelCallNumber(callNumber).available();
    if (prefix != null) {
      builder = builder.withItemLevelCallNumberPrefix(prefix);
    }
    if (suffix != null) {
      builder = builder.withItemLevelCallNumberSuffix(suffix);
    }
    return builder.create();
  }

  private static JsonObject createItem(JsonObject request) {
    var response = await(doPost(client, ResourcePaths.ITEMS, request));
    assertThat(response.status()).isEqualTo(SC_CREATED);
    return response.jsonBody();
  }

  private static JsonObject searchForItems(String cql) {
    return await(doGet(client, ResourcePaths.ITEMS + "?query=" + urlEncode(cql))).jsonBody();
  }

  private static void assertBarcodesFound(String cql, String... expectedBarcodes) {
    var response = searchForItems(cql);
    var barcodes = response.getJsonArray(ITEMS_KEY).stream()
      .map(item -> ((JsonObject) item).getString("barcode")).toList();
    assertThat(barcodes).containsExactlyInAnyOrder(expectedBarcodes);
    assertThat(response.getInteger("totalRecords")).isEqualTo(expectedBarcodes.length);
  }

  private static List<String> idsOf(JsonObject searchResponse) {
    return searchResponse.getJsonArray(ITEMS_KEY).stream()
      .map(item -> ((JsonObject) item).getString("id"))
      .toList();
  }

  private static List<String> searchByCallNumberEyeReadable(String searchTerm) {
    var cql = "fullCallNumber==\"" + searchTerm + "\" OR callNumberAndSuffix==\"" + searchTerm
      + "\" OR effectiveCallNumberComponents.callNumber==\"" + searchTerm + "\"";
    return idsOf(searchForItems(cql));
  }

  private static String urlEncode(String value) {
    return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
  }
}
