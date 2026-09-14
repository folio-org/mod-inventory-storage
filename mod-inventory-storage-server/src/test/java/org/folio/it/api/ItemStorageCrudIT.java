package org.folio.it.api;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_CREATED;
import static org.folio.HttpStatus.SC_NO_CONTENT;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.it.ItemStorageFixtures.createStatisticalCode;
import static org.folio.support.ResourcePaths.ITEMS;
import static org.folio.support.ResourcePaths.ITEMS_RETRIEVE;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.folio.rest.jaxrs.model.ItemLastCheckIn;
import org.folio.support.builders.ItemRequestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ItemStorageCrudIT extends ItemStorageTestBase {

  @Test
  @DisplayName("should create an item via the collection resource with many properties populated")
  void shouldCreateItem_viaCollectionResourceWithManyPropertiesPopulated() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    var inTransitServicePointId = UUID.randomUUID().toString();
    var adminNote = "an admin note";
    var displaySummary = "Important item";
    var statisticalCodeId = createStatisticalCode(client);
    var itemToCreate = detailedItemRequest(id, holdingId, adminNote, displaySummary, inTransitServicePointId,
      statisticalCodeId);

    var item = createItem(itemToCreate);
    assertDetailedItem(item, id, adminNote, holdingId, displaySummary, inTransitServicePointId, statisticalCodeId);

    var itemFromGet = getItemJsonById(id.toString());
    assertDetailedItem(itemFromGet, id, adminNote, holdingId, displaySummary, inTransitServicePointId,
      statisticalCodeId);
  }

  @Test
  @DisplayName("should create an item with minimal properties")
  void shouldCreateItem_withMinimalProperties() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    var itemToCreate = minimalItemRequest(id, holdingId).put("tags", tags(TAG_VALUE));

    var item = createItem(itemToCreate);
    assertThat(item.getString("id")).isEqualTo(id.toString());

    var itemFromGet = getItemJsonById(id.toString());
    assertThat(itemFromGet.getString("id")).isEqualTo(id.toString());
    assertThat(itemFromGet.getJsonObject(STATUS_KEY).getString("name")).isEqualTo("Available");
    assertThat(itemFromGet.getInteger(ORDER_FIELD)).isEqualTo(1);
    assertThat(getTags(itemFromGet)).containsExactly(TAG_VALUE);
  }

  @Test
  @DisplayName("should create an item with circulation note ids populated")
  void shouldCreateItem_withCirculationNoteIdsPopulated() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    var circulationNoteId = UUID.randomUUID();
    var itemToCreate = itemRequestWithCirculationNotes(holdingId, itemId, circulationNoteId);

    var item = createItem(itemToCreate);

    assertThat(item.getString("id")).isEqualTo(itemId.toString());
    assertCirculationNotesPopulated(getItemJsonById(itemId.toString()), circulationNoteId);
  }

  @Test
  @DisplayName("should replace an item with new properties")
  void shouldReplaceItem_withNewProperties() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    createItem(smallAngryPlanet(id, holdingId));
    var createdItem = getItemJsonById(id.toString());
    assertThat(createdItem.getString("copyNumber")).isNull();

    var updatedItem = createdItem.copy().put("copyNumber", "copy1").put("displaySummary", "Important item")
      .put("administrativeNotes", new JsonArray().add("an admin note"));
    assertThat(updateItem(updatedItem).status()).isEqualTo(SC_NO_CONTENT);

    var itemFromGet = getItemJsonById(id.toString());
    assertThat(itemFromGet.getString("copyNumber")).isEqualTo("copy1");
    assertThat(itemFromGet.getJsonArray("administrativeNotes")).contains("an admin note");
    assertThat(itemFromGet.getString("displaySummary")).isEqualTo("Important item");
  }

  @Test
  @DisplayName("should move an item to a new holding")
  void shouldMoveItem_toNewHolding() {
    var oldHoldingId = createHoldingRecord();
    var newHoldingId = createHoldingRecord();
    var id = UUID.randomUUID();
    createItem(smallAngryPlanet(id, oldHoldingId));
    var createdItem = getItemJsonById(id.toString());

    var updatedItem = createdItem.copy().put("holdingsRecordId", newHoldingId);
    assertThat(updateItem(updatedItem).status()).isEqualTo(SC_NO_CONTENT);

    assertThat(getItemJsonById(id.toString()).getString("holdingsRecordId")).isEqualTo(newHoldingId);
  }

  @Test
  @DisplayName("should create an item without providing an id")
  void shouldCreateItem_withoutProvidingId() {
    var holdingId = createHoldingRecord();
    var itemToCreate = smallAngryPlanet(null, holdingId).put("tags", tags(TAG_VALUE));

    var item = createItem(itemToCreate);
    var newId = item.getString("id");

    var itemFromGet = getItemJsonById(newId);
    assertThat(itemFromGet.getString("holdingsRecordId")).isEqualTo(holdingId);
    assertThat(itemFromGet.getString("barcode")).isEqualTo("036000291452");
    assertThat(itemFromGet.getJsonObject(STATUS_KEY).getString("name")).isEqualTo("Available");
    assertThat(getTags(itemFromGet)).containsExactly(TAG_VALUE);
  }

  @Test
  @DisplayName("should update an item with circulation note ids populated")
  void shouldUpdateItem_withCirculationNoteIdsPopulated() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    setItemSequence(1);
    createItem(minimalItemRequest(itemId, holdingId));

    var circulationNoteId = UUID.randomUUID();
    var itemToUpdate = getItemJsonById(itemId.toString())
      .put("circulationNotes", circulationNotes(circulationNoteId));
    assertThat(updateItem(itemToUpdate).status()).isEqualTo(SC_NO_CONTENT);

    assertCirculationNotesPopulated(getItemJsonById(itemId.toString()), circulationNoteId);
  }

  @Test
  @DisplayName("should replace an item at a specific location")
  void shouldReplaceItem_atSpecificLocation() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    createItem(smallAngryPlanet(id, holdingId).put("hrid", "testHRID"));

    var replacement = getItemJsonById(id.toString()).put("barcode", "125845734657")
      .put("temporaryLocationId", mainLibraryLocationId).put("tags", tags(TAG_VALUE));
    assertThat(updateItem(replacement).status()).isEqualTo(SC_NO_CONTENT);

    var item = getItemJsonById(id.toString());
    assertThat(item.getString("barcode")).isEqualTo("125845734657");
    assertThat(item.getString("temporaryLocationId")).isEqualTo(mainLibraryLocationId);
    assertThat(getTags(item)).containsExactly(TAG_VALUE);
  }

  @Test
  @DisplayName("should delete an item")
  void shouldDeleteItem() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    createItem(smallAngryPlanet(id, holdingId));

    var deleteResponse = await(doDelete(client, ITEMS + "/" + id));

    assertThat(deleteResponse.status()).isEqualTo(SC_NO_CONTENT);
    assertGetNotFound(ITEMS + "/" + id);
  }

  @Test
  @DisplayName("should page through all items")
  void shouldPageThroughAllItems() {
    var holdingId = createHoldingRecord();
    createFiveItems(holdingId);

    var firstPage = await(doGet(client, ITEMS + "?limit=3&offset=0")).jsonBody();
    var secondPage = await(doGet(client, ITEMS + "?limit=3&offset=3")).jsonBody();

    assertThat(firstPage.getJsonArray(ITEMS_KEY)).hasSize(3);
    assertThat(secondPage.getJsonArray(ITEMS_KEY)).hasSize(2);
  }

  @Test
  @DisplayName("should retrieve items via the retrieve endpoint")
  void shouldRetrieveItems_viaRetrieveEndpoint() {
    var holdingId = createHoldingRecord();
    var itemIds = new ArrayList<String>();
    for (var i = 0; i < 5; i++) {
      itemIds.add(createItem(minimalItemRequest(UUID.randomUUID(), holdingId)
        .put("barcode", UUID.randomUUID().toString())).getString("id"));
    }
    var query = "id==(" + String.join(" or ", itemIds) + ")";

    var response = await(doPost(client, ITEMS_RETRIEVE, new JsonObject().put("query", query)
      .put("limit", 2000))).jsonBody();

    assertThat(response.getJsonArray(ITEMS_KEY)).hasSize(5);
    assertThat(response.getInteger("totalRecords")).isEqualTo(5);
  }

  @Test
  @DisplayName("should page through all items via the retrieve endpoint")
  void shouldPageThroughAllItems_viaRetrieveEndpoint() {
    var holdingId = createHoldingRecord();
    createFiveItems(holdingId);

    var firstPage = await(doPost(client, ITEMS_RETRIEVE,
      new JsonObject().put("limit", 3).put("offset", 0))).jsonBody();
    var secondPage = await(doPost(client, ITEMS_RETRIEVE,
      new JsonObject().put("limit", 3).put("offset", 3))).jsonBody();

    assertThat(firstPage.getJsonArray(ITEMS_KEY)).hasSize(3);
    assertThat(secondPage.getJsonArray(ITEMS_KEY)).hasSize(2);
  }

  @Test
  @DisplayName("should create multiple items without a barcode")
  void shouldCreateMultipleItems_withoutBarcode() {
    var holdingId = createHoldingRecord();

    createItem(removeBarcode(smallAngryPlanet(UUID.randomUUID(), holdingId)));
    createItem(removeBarcode(smallAngryPlanet(UUID.randomUUID(), holdingId)));
    createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).putNull("barcode"));
    createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).putNull("barcode"));

    var response = await(doGet(client, ITEMS + "?query=" + urlEncode("id==*"))).jsonBody();
    assertThat(response.getInteger("totalRecords")).isEqualTo(4);
  }

  @Test
  @DisplayName("should create an item with a last check-in")
  void shouldCreateItem_withLastCheckIn() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    var itemData = smallAngryPlanet(itemId, holdingId).put("hrid", "testHRID");
    createItem(itemData);
    var expected = new ItemLastCheckIn().withStaffMemberId(UUID.randomUUID().toString())
      .withServicePointId(UUID.randomUUID().toString()).withDateTime(new java.util.Date());

    itemData.put("lastCheckIn", pojo2JsonObject(expected));
    assertThat(updateItem(itemData.put("id", itemId.toString())).status()).isEqualTo(SC_NO_CONTENT);

    var actual = getItemJsonById(itemId.toString()).getJsonObject("lastCheckIn").mapTo(ItemLastCheckIn.class);
    assertThat(actual.getDateTime()).isEqualTo(expected.getDateTime());
    assertThat(actual.getServicePointId()).isEqualTo(expected.getServicePointId());
    assertThat(actual.getStaffMemberId()).isEqualTo(expected.getStaffMemberId());
  }

  @Test
  @DisplayName("should create an item with multiple statistical code ids")
  void shouldCreateItem_withMultipleStatisticalCodeIds() {
    var holdingId = createHoldingRecord();
    var firstStatisticalCodeId = createStatisticalCode(client);
    var secondStatisticalCodeId = createStatisticalCode(client);
    var statisticalCodeIds = List.of(UUID.fromString(firstStatisticalCodeId), UUID.fromString(secondStatisticalCodeId));
    var itemToCreate = new ItemRequestBuilder().forHolding(UUID.fromString(holdingId))
      .withMaterialType(UUID.fromString(materialTypeId)).withPermanentLoanType(UUID.fromString(loanTypeId))
      .withStatisticalCodeIds(statisticalCodeIds).create();

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  @Test
  @DisplayName("should update an item with a statistical code id")
  void shouldUpdateItem_withStatisticalCodeId() {
    var holdingId = createHoldingRecord();
    var statisticalCodeId = createStatisticalCode(client);
    var item = createItem(minimalItemRequest(UUID.randomUUID(), holdingId).put("hrid", "testHRID"));
    item.put(STATISTICAL_CODE_IDS_KEY, List.of(statisticalCodeId));

    assertThat(updateItem(item).status()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should delete all items")
  void shouldDeleteAllItems() {
    var holdingId = createHoldingRecord();
    createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", UUID.randomUUID().toString()));
    createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", UUID.randomUUID().toString()));
    createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", UUID.randomUUID().toString()));

    var deleteResponse = await(doDelete(client, ITEMS + "?query=cql.allRecords=1"));

    assertThat(deleteResponse.status()).isEqualTo(SC_NO_CONTENT);
    var response = await(doGet(client, ITEMS)).jsonBody();
    assertThat(response.getJsonArray(ITEMS_KEY)).isEmpty();
    assertThat(response.getInteger("totalRecords")).isZero();
  }

  @Test
  @DisplayName("should delete items matching a CQL query")
  void shouldDeleteItems_matchingCqlQuery() {
    var holdingId = createHoldingRecord();
    final var item1 = createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "1234"));
    var item2 = createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "23"));
    final var item3 = createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "12"));
    var item4 = createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "234"));
    final var item5 = createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "123"));

    var response = await(doDelete(client, ITEMS + "?query=barcode==12*"));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    assertExists(item2);
    assertExists(item4);
    assertNotExists(item1);
    assertNotExists(item3);
    assertNotExists(item5);
  }

  private static JsonObject detailedItemRequest(UUID id, String holdingId, String adminNote, String displaySummary,
                                                String inTransitServicePointId, String statisticalCodeId) {
    return minimalItemRequest(id, holdingId).put(ORDER_FIELD, 100)
      .put("administrativeNotes", new JsonArray().add(adminNote)).put("barcode", "565578437802")
      .put("displaySummary", displaySummary).put("temporaryLocationId", annexLibraryLocationId)
      .put("copyNumber", "copy1").put("itemLevelCallNumber", "PS3623.R534 P37 2005")
      .put("itemLevelCallNumberSuffix", "allOwnComponentsCNS").put("itemLevelCallNumberPrefix", "allOwnComponentsCNP")
      .put("itemLevelCallNumberTypeId", lcCallNumberTypeId).put(STATISTICAL_CODE_IDS_KEY, List.of(statisticalCodeId))
      .put("inTransitDestinationServicePointId", inTransitServicePointId);
  }

  private static void assertDetailedItem(JsonObject item, UUID id, String adminNote, String holdingId,
                                         String displaySummary, String inTransitServicePointId,
                                         String statisticalCodeId) {
    assertThat(item.getString("id")).isEqualTo(id.toString());
    assertThat(item.getInteger(ORDER_FIELD)).isEqualTo(100);
    assertThat(item.getJsonArray("administrativeNotes")).contains(adminNote);
    assertThat(item.getString("holdingsRecordId")).isEqualTo(holdingId);
    assertThat(item.getString("barcode")).isEqualTo("565578437802");
    assertThat(item.getJsonObject(STATUS_KEY).getString("name")).isEqualTo("Available");
    assertThat(item.getString("displaySummary")).isEqualTo(displaySummary);
    assertThat(item.getString("temporaryLocationId")).isEqualTo(annexLibraryLocationId);
    assertThat(item.getString("inTransitDestinationServicePointId")).isEqualTo(inTransitServicePointId);
    assertThat(item.getString("hrid")).isNotNull();
    assertThat(getTags(item)).containsExactly(TAG_VALUE);
    assertThat(item.getString("copyNumber")).isEqualTo("copy1");
    assertThat(item.getJsonArray(STATISTICAL_CODE_IDS_KEY)).contains(statisticalCodeId);
  }

  private static JsonObject itemRequestWithCirculationNotes(String holdingId, UUID itemId, UUID circulationNoteId) {
    return minimalItemRequest(itemId, holdingId).put("circulationNotes", circulationNotes(circulationNoteId));
  }

  private static JsonArray circulationNotes(UUID circulationNoteId) {
    var checkInNote = new JsonObject().put("noteType", "Check in").put("note", "Check in note")
      .put("staffOnly", false);
    var checkOutNote = new JsonObject().put("id", circulationNoteId.toString()).put("noteType", "Check out")
      .put("note", "Check out note").put("staffOnly", false);
    return new JsonArray().add(checkInNote).add(checkOutNote);
  }

  private static void assertCirculationNotesPopulated(JsonObject item, UUID circulationNoteId) {
    var notes = item.getJsonArray("circulationNotes");
    assertThat(notes).hasSize(2);
    notes.forEach(note -> {
      var noteJson = (JsonObject) note;
      assertThat(noteJson.getString("id")).isNotNull();
      if ("Check out".equals(noteJson.getString("noteType"))) {
        assertThat(noteJson.getString("id")).isEqualTo(circulationNoteId.toString());
      }
    });
  }

  private static void createFiveItems(String holdingId) {
    for (var i = 0; i < 5; i++) {
      createItem(minimalItemRequest(UUID.randomUUID(), holdingId).put("barcode", UUID.randomUUID().toString()));
    }
  }

  private static JsonObject removeBarcode(JsonObject item) {
    item.remove("barcode");
    return item;
  }

  private static String urlEncode(String value) {
    return java.net.URLEncoder.encode(value, UTF_8);
  }

  private static void assertNotExists(JsonObject item) {
    assertGetNotFound(ITEMS + "/" + item.getString("id"));
  }
}
