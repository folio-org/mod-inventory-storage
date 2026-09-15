package org.folio.it.api.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_BAD_REQUEST;
import static org.folio.HttpStatus.SC_UNPROCESSABLE_CONTENT;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.it.ItemStorageFixtures.createStatisticalCode;
import static org.folio.support.ResourcePaths.ITEMS;
import static org.folio.support.ResourcePaths.LOAN_TYPES;
import static org.folio.support.ResourcePaths.LOCATIONS;
import static org.folio.support.ResourcePaths.MATERIAL_TYPES;
import static org.folio.validator.NotesValidators.MAX_NOTE_LENGTH;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.UUID;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.ItemNote;
import org.folio.support.builders.ItemRequestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ItemStorageValidationIT extends ItemStorageTestBase {

  @Test
  @DisplayName("should return 400 when a statistical code id is invalid")
  void shouldReturn400_whenStatisticalCodeIdIsInvalid() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId)
      .put("tags", tags(TAG_VALUE)).put(STATISTICAL_CODE_IDS_KEY, List.of(INVALID_VALUE));

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains(INVALID_TYPE_ERROR_MESSAGE);
  }

  @Test
  @DisplayName("should return 422 when the permanent location does not exist")
  void shouldReturn422_whenPermanentLocationDoesNotExist() {
    var holdingId = createHoldingRecord();
    var badLocation = UUID.randomUUID();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put("permanentLocationId", badLocation);

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(response.body().toString()).contains("Cannot set item.permanentlocationid");
  }

  @Test
  @DisplayName("should return 422 when the temporary location does not exist")
  void shouldReturn422_whenTemporaryLocationDoesNotExist() {
    var holdingId = createHoldingRecord();
    var badLocation = UUID.randomUUID();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put("temporaryLocationId", badLocation);

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(response.body().toString()).contains("Cannot set item.temporarylocationid");
  }

  @Test
  @DisplayName("should return 422 when the item id is not a UUID")
  void shouldReturn422_whenItemIdIsNotUuid() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put("id", "1234")
      .put("temporaryLocationId", annexLibraryLocationId);

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(response.body().toString()).contains("UUID");
  }

  @Test
  @DisplayName("should return 422 when the material type is missing")
  void shouldReturn422_whenMaterialTypeIsMissing() {
    var holdingId = createHoldingRecord();
    var itemToCreate = new JsonObject().put("id", UUID.randomUUID().toString())
      .put(STATUS_KEY, new JsonObject().put("name", "Available")).put("holdingsRecordId", holdingId)
      .put("permanentLoanTypeId", loanTypeId);

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    var errors = response.jsonBody().mapTo(Errors.class).getErrors();
    assertThat(errors).hasSize(1);
    assertThat(errors.getFirst().getMessage()).isIn("may not be null", "must not be null");
    assertThat(errors.getFirst().getParameters().getFirst().getKey()).isEqualTo("materialTypeId");
  }

  @Test
  @DisplayName("should return 422 when the material type does not exist")
  void shouldReturn422_whenMaterialTypeDoesNotExist() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put("materialTypeId", UUID.randomUUID());

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(response.body().toString()).contains("Cannot set item.materialtypeid");
  }

  @Test
  @DisplayName("should return 422 when creating an item whose note exceeds the maximum length")
  void shouldReturn422_whenCreatingItemNoteExceedsMaximumLength() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId)
      .put("notes", new JsonArray().add(pojo2JsonObject(new ItemNote().withNote("x".repeat(MAX_NOTE_LENGTH + 1)))));

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
  }

  @Test
  @DisplayName("should return 422 when creating an item whose administrative note exceeds the maximum length")
  void shouldReturn422_whenCreatingItemAdministrativeNoteExceedsMaximumLength() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId)
      .put("administrativeNotes", new JsonArray().add("x".repeat(MAX_NOTE_LENGTH + 1)));

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
  }

  @Test
  @DisplayName("should return 422 when updating an item's administrative note to exceed the maximum length")
  void shouldReturn422_whenUpdatingItemAdministrativeNoteExceedsMaximumLength() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    createItem(minimalItemRequest(itemId, holdingId).put("hrid", "testHRID"));
    var item = getItemJsonById(itemId.toString())
      .put("administrativeNotes", new JsonArray().add("x".repeat(MAX_NOTE_LENGTH + 1)));

    assertThat(updateItem(item).status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
  }

  @Test
  @DisplayName("should return 422 when updating an item's note to exceed the maximum length")
  void shouldReturn422_whenUpdatingItemNoteExceedsMaximumLength() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    createItem(smallAngryPlanet(itemId, holdingId));
    var item = getItemJsonById(itemId.toString())
      .put("notes", new JsonArray().add(pojo2JsonObject(new ItemNote().withNote("x".repeat(MAX_NOTE_LENGTH + 1)))));

    assertThat(updateItem(item).status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
  }

  @Test
  @DisplayName("should return 422 when updating an item with a non-existing material type")
  void shouldReturn422_whenUpdatingItemWithNonExistingMaterialType() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    createItem(minimalItemRequest(itemId, holdingId).put("hrid", "testHRID"));
    var item = getItemJsonById(itemId.toString()).put("materialTypeId", UUID.randomUUID().toString());

    var response = updateItem(item);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains("Cannot set item").contains("materialtypeid");
  }

  @Test
  @DisplayName("should return 400 when the statistical code ids are not UUIDs")
  void shouldReturn400_whenStatisticalCodeIdsAreNotUuids() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put(STATISTICAL_CODE_IDS_KEY,
      new JsonArray().add("07"));

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString())
      .contains("invalid input syntax for type uuid: \"07\"");
  }

  @Test
  @DisplayName("should return 400 when replacing an item with non-UUID statistical code ids")
  void shouldReturn400_whenReplacingItemWithNonUuidStatisticalCodeIds() {
    var holdingId = createHoldingRecord();
    var item = createItem(minimalItemRequest(UUID.randomUUID(), holdingId));
    item.put(STATISTICAL_CODE_IDS_KEY, new JsonArray().add("1234567890123456789012345678901234567890"));

    var response = updateItem(item);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString())
      .contains("invalid input syntax for type uuid: \"1234567890123456789012345678901234567890\"");
  }

  @Test
  @DisplayName("should return 422 when an item has an additional unrecognized property")
  void shouldReturn422_whenItemHasAdditionalProperty() {
    var holdingId = createHoldingRecord();
    var itemToCreate = smallAngryPlanet(UUID.randomUUID(), holdingId).put("somethingAdditional", "foo");

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(response.jsonBody().mapTo(Errors.class).getErrors().getFirst().getMessage())
      .contains("Unrecognized field");
  }

  @Test
  @DisplayName("should return 422 when an item's status has an additional unrecognized property")
  void shouldReturn422_whenItemStatusHasAdditionalProperty() {
    var holdingId = createHoldingRecord();
    var itemToCreate = smallAngryPlanet(UUID.randomUUID(), holdingId)
      .put(STATUS_KEY, new JsonObject().put("somethingAdditional", "foo"));

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(response.jsonBody().mapTo(Errors.class).getErrors().getFirst().getMessage())
      .contains("Unrecognized field");
  }

  @Test
  @DisplayName("should return 400 when creating an item with a duplicate barcode")
  void shouldReturn400_whenCreatingItemWithDuplicateBarcode() {
    var holdingId = createHoldingRecord();
    createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "9876a"));

    var response1 = await(doPost(client, ITEMS,
      smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "9876a")));
    var response2 = await(doPost(client, ITEMS,
      smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "9876A")));

    assertThat(response1.body().toString()).contains("9876a");
    assertThat(response2.body().toString()).contains("9876a");
  }

  @Test
  @DisplayName("should return 400 when updating an item with a duplicate barcode")
  void shouldReturn400_whenUpdatingItemWithDuplicateBarcode() {
    var holdingId = createHoldingRecord();
    createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "9876a"));
    var otherItemId = UUID.randomUUID();
    var otherItem = createItem(smallAngryPlanet(otherItemId, holdingId).put("barcode", "123"));

    var response = updateItem(otherItem.put("barcode", "9876A"));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains("already exists in table item: 9876a");
  }

  @Test
  @DisplayName("should return 422 when creating an item with a non-existent holdings record id")
  void shouldReturn422_whenCreatingItemWithNonExistentHoldingsRecordId() {
    var nonExistingHoldingId = UUID.randomUUID();
    var itemToCreate = new ItemRequestBuilder().forHolding(nonExistingHoldingId)
      .withMaterialType(UUID.fromString(materialTypeId)).withPermanentLoanType(UUID.fromString(loanTypeId)).create();

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(response.body().toString()).contains("Holdings record does not exist");
  }

  @Test
  @DisplayName("should return 422 when the statistical code id does not exist")
  void shouldReturn422_whenStatisticalCodeIdDoesNotExist() {
    var holdingId = createHoldingRecord();
    var nonExistingStatisticalCodeId = UUID.randomUUID();
    var itemToCreate = new ItemRequestBuilder().forHolding(UUID.fromString(holdingId))
      .withMaterialType(UUID.fromString(materialTypeId)).withPermanentLoanType(UUID.fromString(loanTypeId))
      .withStatisticalCodeIds(List.of(nonExistingStatisticalCodeId)).create();

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(response.body().toString()).contains(("Cannot set item.statistical_code_id = %s "
                                                     + "because it does not exist in statistical_code.id.").formatted(
      nonExistingStatisticalCodeId));
  }

  @Test
  @DisplayName("should return 422 when at least one statistical code id does not exist")
  void shouldReturn422_whenAtLeastOneStatisticalCodeIdDoesNotExist() {
    var holdingId = createHoldingRecord();
    var statisticalCodeId = createStatisticalCode(client);
    var nonExistingStatisticalCodeId = UUID.randomUUID();
    var itemToCreate = new ItemRequestBuilder().forHolding(UUID.fromString(holdingId))
      .withMaterialType(UUID.fromString(materialTypeId)).withPermanentLoanType(UUID.fromString(loanTypeId))
      .withStatisticalCodeIds(List.of(UUID.fromString(statisticalCodeId), nonExistingStatisticalCodeId)).create();

    var response = await(doPost(client, ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(response.body().toString()).contains(nonExistingStatisticalCodeId.toString());
  }

  @Test
  @DisplayName("should return 400 when updating an item with a non-existent statistical code id")
  void shouldReturn400_whenUpdatingItemWithNonExistentStatisticalCodeId() {
    var holdingId = createHoldingRecord();
    var nonExistingStatisticalCodeId = UUID.randomUUID();
    var item = createItem(minimalItemRequest(UUID.randomUUID(), holdingId).put("hrid", "testHRID"));
    item.put(STATISTICAL_CODE_IDS_KEY, List.of(nonExistingStatisticalCodeId.toString()));

    var response = updateItem(item);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body()).hasToString(("Cannot set item statistical_code_id = %s "
                                             + "because it does not exist in statistical_code.id.").formatted(
      nonExistingStatisticalCodeId));
  }

  @Test
  @DisplayName("should return 422 when the permanent loan type does not exist")
  void shouldReturn422_whenPermanentLoanTypeDoesNotExist() {
    var holdingId = createHoldingRecord();

    var response = await(doPost(client, ITEMS,
      itemRequestForLoanTypes(holdingId, UUID.randomUUID().toString(), null)));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
  }

  @Test
  @DisplayName("should return 422 when the temporary loan type does not exist")
  void shouldReturn422_whenTemporaryLoanTypeDoesNotExist() {
    var holdingId = createHoldingRecord();

    var response = await(doPost(client, ITEMS,
      itemRequestForLoanTypes(holdingId, loanTypeId, UUID.randomUUID().toString())));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
  }

  @Test
  @DisplayName("should return 400 when updating an item with a non-existing permanent loan type id")
  void shouldReturn400_whenUpdatingItemWithNonExistingPermanentLoanTypeId() {
    var holdingId = createHoldingRecord();
    var item = createItem(itemRequestForLoanTypes(holdingId, loanTypeId, loanTypeId));

    var response = updateItem(item.put("permanentLoanTypeId", UUID.randomUUID().toString()));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should return 400 when updating an item with a non-existing temporary loan type id")
  void shouldReturn400_whenUpdatingItemWithNonExistingTemporaryLoanTypeId() {
    var holdingId = createHoldingRecord();
    var item = createItem(itemRequestForLoanTypes(holdingId, loanTypeId, loanTypeId));

    var response = updateItem(item.put("temporaryLoanTypeId", UUID.randomUUID().toString()));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should return 400 when deleting a loan type permanently associated with an item")
  void shouldReturn400_whenDeletingLoanTypePermanentlyAssociatedWithItem() {
    var holdingId = createHoldingRecord();
    createItem(itemRequestForLoanTypes(holdingId, loanTypeId, null));

    var response = await(doDelete(client, LOAN_TYPES + "/" + loanTypeId));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should return 400 when deleting a loan type temporarily associated with an item")
  void shouldReturn400_whenDeletingLoanTypeTemporarilyAssociatedWithItem() {
    var holdingId = createHoldingRecord();
    createItem(itemRequestForLoanTypes(holdingId, secondLoanTypeId, loanTypeId));

    var response = await(doDelete(client, LOAN_TYPES + "/" + loanTypeId));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should return 400 when deleting a material type associated with an item")
  void shouldReturn400_whenDeletingMaterialTypeAssociatedWithItem() {
    var holdingId = createHoldingRecord();
    createItem(itemRequestForLoanTypes(holdingId, loanTypeId, null));

    var response = await(doDelete(client, MATERIAL_TYPES + "/" + materialTypeId));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should return 400 when deleting a location associated with an item")
  void shouldReturn400_whenDeletingLocationAssociatedWithItem() {
    var holdingId = createHoldingRecord();
    createItem(smallAngryPlanet(UUID.randomUUID(), holdingId));

    var response = await(doDelete(client, LOCATIONS + "/" + annexLibraryLocationId));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @ValueSource(strings = {"", "?query=", "?query=%20%20"})
  @ParameterizedTest
  @DisplayName("should return 400 when deleting items without a CQL query")
  void shouldReturn400_whenDeletingItemsWithoutCql(String query) {
    var response = await(doDelete(client, ITEMS + query));

    assertThat(response.body()).hasToString("Expected CQL but query parameter is empty");
    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should return 400 when deleting items with an invalid CQL query")
  void shouldReturn400_whenDeletingItemsWithInvalidCql() {
    var response = await(doDelete(client, ITEMS + "?query=\""));

    assertThat(response.body().toString().toLowerCase()).contains("parse");
    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  private static JsonObject itemRequestForLoanTypes(String holdingId, String permanentLoanTypeId,
                                                    String temporaryLoanTypeId) {
    var item = new JsonObject().put(STATUS_KEY, new JsonObject().put("name", "Available"))
      .put("holdingsRecordId", holdingId).put("barcode", UUID.randomUUID().toString())
      .put("materialTypeId", materialTypeId);
    if (permanentLoanTypeId != null) {
      item.put("permanentLoanTypeId", permanentLoanTypeId);
    }
    if (temporaryLoanTypeId != null) {
      item.put("temporaryLoanTypeId", temporaryLoanTypeId);
    }
    return item;
  }
}
