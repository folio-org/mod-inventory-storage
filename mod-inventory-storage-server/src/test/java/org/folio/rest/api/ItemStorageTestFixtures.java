package org.folio.rest.api;

import io.vertx.core.json.JsonObject;
import java.util.UUID;

/**
 * {@code nodWithNoBarcode} and its dependencies, extracted out of {@code ItemStorageTest} (now
 * migrated to {@code org.folio.rest.impl.ItemStorageIT}) because {@link InventoryViewTest} and
 * {@link ItemEffectiveCallNumberComponentsTest} still depend on it on the legacy stack.
 */
final class ItemStorageTestFixtures {

  private ItemStorageTestFixtures() {
  }

  static JsonObject nod(UUID itemId, UUID holdingsRecordId) {
    return createItemRequest(itemId, holdingsRecordId, "565578437802");
  }

  static JsonObject nod(UUID holdingsRecordId) {
    return nod(UUID.randomUUID(), holdingsRecordId);
  }

  static JsonObject nodWithNoBarcode(UUID holdingsRecordId) {
    return removeBarcode(nod(holdingsRecordId));
  }

  static JsonObject removeBarcode(JsonObject item) {
    item.remove("barcode");
    return item;
  }

  private static JsonObject createItemRequest(UUID id, UUID holdingsRecordId, String barcode) {
    var itemToCreate = new JsonObject();

    if (id != null) {
      itemToCreate.put("id", id.toString());
    }

    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("barcode", barcode);
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("materialTypeId", TestBaseWithInventoryUtil.journalMaterialTypeID);
    itemToCreate.put("permanentLoanTypeId", TestBaseWithInventoryUtil.canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", TestBaseWithInventoryUtil.ANNEX_LIBRARY_LOCATION_ID.toString());
    itemToCreate.put("_version", 1);

    return itemToCreate;
  }
}
