package org.folio.it.api;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.it.HoldingsStorageFixtures.createCallNumberType;
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
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.support.ResourcePaths;
import org.folio.support.builders.ItemRequestBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bulk PATCH ({@code /item-storage/items}) tests: applying a batch of partial item updates,
 * validation/error scenarios, read-only field stripping, and call-number/effective-location
 * field updates through PATCH specifically (as opposed to PUT). Split out of {@link
 * ItemStorageIT} because this is a large, cohesive concern in its own right.
 */
class ItemPatchIT extends BaseIntegrationTest {

  private static final String ITEMS_KEY = "items";
  private static final String STATUS_KEY = "status";
  // The itemLevelCallNumberTypeId this class's call-number-field patch test keys off; cannot be
  // a fresh/random id (see HoldingsStorageFixtures.createCallNumberType).
  private static final String DEWEY_CALL_NUMBER_TYPE_ID = "03dd64d0-5626-4ecd-8ece-4531e0069f35";

  private static String instanceTypeId;
  private static String materialTypeId;
  private static String loanTypeId;
  private static String mainLibraryLocationId;
  private static String annexLibraryLocationId;
  private static String deweyCallNumberTypeId;

  @BeforeAll
  static void seedReferenceData() {
    instanceTypeId = createInstanceType(client);
    materialTypeId = createMaterialType(client);
    loanTypeId = createLoanType(client);
    mainLibraryLocationId = createLocation(client);
    annexLibraryLocationId = createLocation(client);
    deweyCallNumberTypeId = createCallNumberType(client, DEWEY_CALL_NUMBER_TYPE_ID, "Dewey Decimal classification");
  }

  @BeforeEach
  void clearItems() {
    runQuery("TRUNCATE TABLE instance, holdings_record, item CASCADE");
  }

  @Test
  @DisplayName("should update an item via a bulk patch")
  void shouldUpdateItem_viaBulkPatch() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    createItem(itemRequest(itemId, holdingId));
    var original = getItemJsonById(itemId.toString());
    var patchItem = original.copy();
    patchItem.remove("hrid");
    patchItem.put("barcode", null);
    patchItem.put("order", 55);

    var response = patchItems(new JsonArray().add(patchItem));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    var updated = getItemJsonById(itemId.toString());
    assertThat(updated.getInteger("order")).isEqualTo(55);
    assertThat(updated.getString("hrid")).isEqualTo(original.getString("hrid"));
    assertThat(updated.getValue("barcode")).isNull();
  }

  @Test
  @DisplayName("should return 400 when a bulk patch has no items")
  void shouldReturn400_whenBulkPatchHasNoItems() {
    var response = patchItems(new JsonArray());

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains("Expected at least one item to update");
  }

  @Test
  @DisplayName("should return 400 when a bulk patch has an unrecognized field")
  void shouldReturn400_whenBulkPatchHasUnrecognizedField() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    createItem(itemRequest(itemId, holdingId));
    var patchItem = getItemJsonById(itemId.toString()).put("invalidField", "text");

    var response = patchItems(new JsonArray().add(patchItem));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains("Invalid item format: Unrecognized field");
  }

  @Test
  @DisplayName("should return 422 when a bulk patch references a non-existing statistical code id")
  void shouldReturn422_whenBulkPatchReferencesNonExistingStatisticalCodeId() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    createItem(itemRequest(itemId, holdingId));
    var patchItem = getItemJsonById(itemId.toString())
      .put("statisticalCodeIds", new JsonArray().add(UUID.randomUUID().toString()));

    var response = patchItems(new JsonArray().add(patchItem));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    assertThat(response.body().toString()).contains("Cannot set item.statistical_code_id")
      .contains("it does not exist in statistical_code.id");
  }

  @Test
  @DisplayName("should return 404 when a bulk patch references a non-existing holding id")
  void shouldReturn404_whenBulkPatchReferencesNonExistingHoldingId() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    createItem(itemRequest(itemId, holdingId));
    var nonExistingHoldingId = UUID.randomUUID();
    var patchItem = getItemJsonById(itemId.toString()).put("holdingsRecordId", nonExistingHoldingId.toString());

    var response = patchItems(new JsonArray().add(patchItem));

    assertThat(response.status()).isEqualTo(SC_NOT_FOUND);
    assertThat(response.body().toString()).contains("Holdings not found: " + nonExistingHoldingId);
  }

  @Test
  @DisplayName("should return 404 when a bulk patch references a non-existing item id")
  void shouldReturn404_whenBulkPatchReferencesNonExistingItemId() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    createItem(itemRequest(itemId, holdingId));
    var nonExistingItemId = UUID.randomUUID();
    var patchItem = getItemJsonById(itemId.toString()).put("id", nonExistingItemId.toString());

    var response = patchItems(new JsonArray().add(patchItem));

    assertThat(response.status()).isEqualTo(SC_NOT_FOUND);
    assertThat(response.body().toString()).contains("Item not found in database: %s".formatted(nonExistingItemId));
  }

  @Test
  @DisplayName("should return 409 when a bulk patch has a stale optimistic-locking version")
  void shouldReturn409_whenBulkPatchHasStaleVersion() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    createItem(itemRequest(itemId, holdingId));
    var patchItem = getItemJsonById(itemId.toString()).put("_version", 5);

    var response = patchItems(new JsonArray().add(patchItem));

    assertThat(response.status()).isEqualTo(SC_CONFLICT);
    assertThat(response.body().toString())
      .contains("(optimistic locking): Stored _version is 1, _version of request is 5");
  }

  @Test
  @DisplayName("should return 422 with per-item errors when a bulk patch is missing required fields")
  void shouldReturn422_whenBulkPatchIsMissingRequiredFields() {
    var holdingId = createHoldingRecord();
    var itemId1 = UUID.randomUUID();
    var itemId2 = UUID.randomUUID();
    createItem(itemRequest(itemId1, holdingId));
    createItem(itemRequest(itemId2, holdingId));
    var patch1 = new JsonObject().put("id", itemId1.toString()).put("_version", 1)
      .put("materialTypeId", "").put("permanentLoanTypeId", "").put(STATUS_KEY, new JsonObject());
    var patch2 = new JsonObject().put("id", itemId2.toString()).put("_version", 1)
      .put("holdingsRecordId", "").putNull(STATUS_KEY);

    var response = patchItems(new JsonArray().add(patch1).add(patch2));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var errors = response.jsonBody().getJsonArray("errors");
    assertThat(errors).hasSize(2);
    assertThat(errors).containsExactlyInAnyOrder(
      requiredFieldsError(itemId1, List.of("materialTypeId", "permanentLoanTypeId", "status.name")),
      requiredFieldsError(itemId2, List.of("holdingsRecordId", STATUS_KEY)));
  }

  @Test
  @DisplayName("should update call number fields via a bulk patch")
  void shouldUpdateCallNumberFields_viaBulkPatch() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    var itemJson = itemRequest(itemId, holdingId).put("itemLevelCallNumber", "call number")
      .put("itemLevelCallNumberPrefix", "prefix").put("itemLevelCallNumberSuffix", "suffix")
      .put("itemLevelCallNumberTypeId", deweyCallNumberTypeId).put("volume", "vol").put("enumeration", "en");
    createItem(itemJson);
    var patchItem = new JsonObject().put("id", itemId.toString()).put("_version", 1)
      .put("holdingsRecordId", holdingId).put("itemLevelCallNumberPrefix", "prefix upd")
      .putNull("itemLevelCallNumberTypeId");

    var response = patchItems(new JsonArray().add(patchItem));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    var updated = getItemJsonById(itemId.toString());
    assertThat(updated.getString("itemLevelCallNumberPrefix")).isEqualTo("prefix upd");
    var components = updated.getJsonObject("effectiveCallNumberComponents");
    assertThat(components.getString("callNumber")).isEqualTo("call number");
    assertThat(components.getString("prefix")).isEqualTo("prefix upd");
    assertThat(components.getString("suffix")).isEqualTo("suffix");
    assertThat(components.getString("typeId")).isNull();
  }

  @Test
  @DisplayName("should not update the effective shelving order directly via a bulk patch")
  void shouldNotUpdateEffectiveShelvingOrder_viaBulkPatch() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    createItem(itemRequest(itemId, holdingId).put("itemLevelCallNumber", "call number")
      .put("itemLevelCallNumberSuffix", "suffix"));
    assertThat(getItemJsonById(itemId.toString()).getString("effectiveShelvingOrder"))
      .isEqualTo("call number suffix");
    var patchItem = new JsonObject().put("id", itemId.toString()).put("_version", 1)
      .put("effectiveShelvingOrder", "new shelving order");

    var response = patchItems(new JsonArray().add(patchItem));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    assertThat(getItemJsonById(itemId.toString()).getString("effectiveShelvingOrder")).isEqualTo("call number suffix");
  }

  @Test
  @DisplayName("should update the effective location from the item record via a bulk patch")
  void shouldUpdateEffectiveLocation_fromItemRecordViaBulkPatch() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    createItem(itemRequest(itemId, holdingId).put("temporaryLocationId", mainLibraryLocationId)
      .put("permanentLocationId", annexLibraryLocationId));
    var created = getItemJsonById(itemId.toString());
    assertThat(created.getString("effectiveLocationId")).isEqualTo(mainLibraryLocationId);
    var patchItem = new JsonObject().put("id", itemId.toString()).put("_version", 1)
      .putNull("temporaryLocationId");

    var response = patchItems(new JsonArray().add(patchItem));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    var updated = getItemJsonById(itemId.toString());
    assertThat(updated.getString("effectiveLocationId")).isEqualTo(annexLibraryLocationId);
    assertThat(updated.getString("permanentLocationId")).isEqualTo(annexLibraryLocationId);
    assertThat(updated.getValue("temporaryLocationId")).isNull();
  }

  @Test
  @DisplayName("should update the effective location from the holding record via a bulk patch")
  void shouldUpdateEffectiveLocation_fromHoldingRecordViaBulkPatch() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    createItem(itemRequest(itemId, holdingId).put("temporaryLocationId", mainLibraryLocationId)
      .put("permanentLocationId", annexLibraryLocationId));
    assertThat(getItemJsonById(itemId.toString()).getString("effectiveLocationId")).isEqualTo(mainLibraryLocationId);
    var patchItem = new JsonObject().put("id", itemId.toString()).put("_version", 1)
      .putNull("temporaryLocationId").putNull("permanentLocationId");

    var response = patchItems(new JsonArray().add(patchItem));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    var updated = getItemJsonById(itemId.toString());
    assertThat(updated.getValue("temporaryLocationId")).isNull();
    assertThat(updated.getValue("permanentLocationId")).isNull();
  }

  @Test
  @DisplayName("should normalize the order field to an integer when the request supplies a string")
  void shouldNormalizeOrderField_toIntegerWhenRequestSuppliesString() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    createItem(itemRequest(itemId, holdingId));
    var patchItem = getItemJsonById(itemId.toString()).put("order", "55");

    var response = patchItems(new JsonArray().add(patchItem));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    assertThat(getItemJsonById(itemId.toString()).getInteger("order")).isEqualTo(55);
  }

  @Test
  @DisplayName("should remove read-only fields from a bulk patch request")
  void shouldRemoveReadOnlyFields_fromBulkPatchRequest() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    createItem(itemRequest(itemId, holdingId));
    var patchItem = getItemJsonById(itemId.toString()).put("order", 10)
      .put("holdingsRecord2", new JsonObject()).put("permanentLocation", new JsonObject())
      .put("effectiveShelvingOrder", new JsonObject());

    var response = patchItems(new JsonArray().add(patchItem));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    var updated = getItemJsonById(itemId.toString());
    assertThat(updated.containsKey("holdingsRecord2")).isFalse();
    assertThat(updated.containsKey("permanentLocation")).isFalse();
    assertThat(updated.containsKey("order")).isTrue();
  }

  // -- shared helpers --

  private static String createHoldingRecord() {
    var instanceId = createInstance(client, "an instance " + UUID.randomUUID(), instanceTypeId);
    return HoldingsStorageFixtures.createHolding(client, instanceId, mainLibraryLocationId);
  }

  private static JsonObject itemRequest(UUID id, String holdingId) {
    return new ItemRequestBuilder().withId(id).forHolding(UUID.fromString(holdingId))
      .withMaterialType(UUID.fromString(materialTypeId)).withPermanentLoanType(UUID.fromString(loanTypeId))
      .create();
  }

  private static JsonObject createItem(JsonObject request) {
    var response = await(doPost(client, ResourcePaths.ITEMS, request));
    assertThat(response.status()).isEqualTo(SC_CREATED);
    return response.jsonBody();
  }

  private static JsonObject getItemJsonById(String id) {
    return await(doGet(client, ResourcePaths.ITEMS + "/" + id)).jsonBody();
  }

  private static TestResponse patchItems(JsonArray items) {
    return await(doPatch(client, ResourcePaths.ITEMS, new JsonObject().put(ITEMS_KEY, items)));
  }

  private static JsonObject requiredFieldsError(UUID itemId, List<String> fieldNames) {
    var parameters = fieldNames.stream().map(fieldName -> new Parameter().withKey("field").withValue(fieldName))
      .toList();
    return JsonObject.mapFrom(new Error().withMessage("Required fields cannot be removed. ItemId: " + itemId)
      .withCode("field.required").withParameters(parameters));
  }
}
