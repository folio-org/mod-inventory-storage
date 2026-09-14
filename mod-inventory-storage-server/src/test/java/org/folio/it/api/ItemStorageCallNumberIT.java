package org.folio.it.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_CREATED;
import static org.folio.HttpStatus.SC_NO_CONTENT;
import static org.folio.HttpStatus.SC_UNPROCESSABLE_CONTENT;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.support.ResourcePaths.ITEMS;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ItemStorageCallNumberIT extends ItemStorageTestBase {

  @CsvSource({
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
  @ParameterizedTest
  @DisplayName("should compute the effective shelving order when an item is created")
  void shouldComputeEffectiveShelvingOrder_whenItemIsCreated(
    String desiredShelvingOrder, String initiallyDesiredShelvesOrder, String prefix, String callNumber,
    String volume, String enumeration, String chronology, String copy, String suffix) {
    var holdingId = createHoldingRecord();
    var itemToCreate = itemRequestWithCallNumberComponents(holdingId, prefix, callNumber, volume, enumeration,
      chronology, copy, suffix);

    var item = createItem(itemToCreate);

    assertThat(item.getString("effectiveShelvingOrder")).isEqualTo(desiredShelvingOrder);
  }

  @Test
  @DisplayName("should return 422 when the item-level call number type id is not a UUID")
  void shouldReturn422_whenItemLevelCallNumberTypeIdIsNotUuid() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId)
      .put("itemLevelCallNumberTypeId", INVALID_VALUE);

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(response.body().toString()).contains("must match");
  }

  @Test
  @DisplayName("should return 422 when the effective call number components type id is not a UUID")
  void shouldReturn422_whenEffectiveCallNumberComponentsTypeIdIsNotUuid() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId)
      .put("effectiveCallNumberComponents", new JsonObject().put("typeId", INVALID_VALUE));

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(response.body().toString()).contains("must match");
  }

  @Test
  @DisplayName("should return 422 when the item-level call number type id does not exist")
  void shouldReturn422_whenItemLevelCallNumberTypeIdDoesNotExist() {
    var holdingId = createHoldingRecord();
    var nonExistingTypeId = UUID.randomUUID();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId)
      .put("itemLevelCallNumberTypeId", nonExistingTypeId);

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(response.body().toString()).contains(("Cannot set item.itemlevelcallnumbertypeid = %s "
                                                     + "because it does not exist in call_number_type.id.").formatted(
      nonExistingTypeId));
  }

  @Test
  @DisplayName("should create an item with a minimal additional call number")
  void shouldCreateItem_withMinimalAdditionalCallNumber() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put("hrid", "123456789")
      .put("additionalCallNumbers", new JsonArray().add(additionalCallNumber("This is the only mandatory field",
        null, null, null)));

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  @Test
  @DisplayName("should return 422 when an additional call number is missing its call number")
  void shouldReturn422_whenAdditionalCallNumberMissingCallNumber() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put("hrid", "123456789")
      .put("additionalCallNumbers", new JsonArray().add(
        additionalCallNumber(null, "prefix", "suffix", lcCallNumberTypeId)));

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
  }

  @Test
  @DisplayName("should create and update an item with additional call numbers")
  void shouldCreateAndUpdateItem_withAdditionalCallNumbers() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put("hrid", "123456789")
      .put("additionalCallNumbers", new JsonArray().add(
        additionalCallNumber("Test", "A", "Z", lcCallNumberTypeId)));
    setItemSequence(1);

    var item = createItem(itemToCreate);
    var created = item.getJsonArray("additionalCallNumbers").getJsonObject(0);
    assertThat(created.getString("callNumber")).isEqualTo("Test");
    assertThat(created.getString("prefix")).isEqualTo("A");
    assertThat(created.getString("suffix")).isEqualTo("Z");
    assertThat(created.getString("typeId")).isEqualTo(lcCallNumberTypeId);

    item.getJsonArray("additionalCallNumbers").add(additionalCallNumber("some", "prefix", "suffix",
      lcCallNumberTypeId));
    assertThat(updateItem(item).status()).isEqualTo(SC_NO_CONTENT);

    assertThat(getItemJsonById(item.getString("id")).getJsonArray("additionalCallNumbers")).hasSize(2);
  }

  @Test
  @DisplayName("should delete the additional call numbers from an item")
  void shouldDeleteAdditionalCallNumbers_fromItem() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put("additionalCallNumbers",
      new JsonArray().add(additionalCallNumber("Test", "A", "Z", lcCallNumberTypeId)));
    var item = createItem(itemToCreate);

    item.remove("additionalCallNumbers");
    assertThat(updateItem(item).status()).isEqualTo(SC_NO_CONTENT);

    var updated = getItemJsonById(item.getString("id"));
    assertThat(updated.containsKey("additionalCallNumbers")).isTrue();
    assertThat(updated.getJsonArray("additionalCallNumbers")).isEmpty();
  }

  @Test
  @DisplayName("should create an item with empty additional call numbers")
  void shouldCreateItem_withEmptyAdditionalCallNumbers() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put("hrid", "123456789")
      .put("additionalCallNumbers", new JsonArray());

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  @Test
  @DisplayName("should return 422 when an additional call number type id is not a UUID")
  void shouldReturn422_whenAdditionalCallNumberTypeIdIsNotUuid() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put("hrid", "123456789")
      .put("additionalCallNumbers", new JsonArray().add(additionalCallNumber("Test", "A", "Z", "non-uuid")));

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
  }

  private static JsonObject itemRequestWithCallNumberComponents(String holdingId, String prefix, String callNumber,
                                                                String volume, String enumeration,
                                                                String chronology, String copy, String suffix) {
    return minimalItemRequest(UUID.randomUUID(), holdingId).put("barcode", "565578437802")
      .put("temporaryLocationId", annexLibraryLocationId).put("tags", tags(TAG_VALUE))
      .put("itemLevelCallNumber", callNumber).put("itemLevelCallNumberSuffix", suffix)
      .put("itemLevelCallNumberPrefix", prefix).put("itemLevelCallNumberTypeId", lcCallNumberTypeId)
      .put("volume", volume).put("enumeration", enumeration).put("chronology", chronology).put("copyNumber", copy)
      .put("inTransitDestinationServicePointId", UUID.randomUUID().toString());
  }

  private static JsonObject additionalCallNumber(String callNumber, String prefix, String suffix, String typeId) {
    var json = new JsonObject();
    json.put("callNumber", callNumber);
    json.put("prefix", prefix);
    json.put("suffix", suffix);
    json.put("typeId", typeId);
    return json;
  }
}
