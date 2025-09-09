package org.folio.rest.support;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.services.item.PatchData;

public final class ItemEffectiveLocationUtil {

  private ItemEffectiveLocationUtil() { }

  public static void updateItemEffectiveLocation(Item item, HoldingsRecord holdingsRecord) {
    if (isNull(item.getPermanentLocationId()) && isNull(item.getTemporaryLocationId())) {
      setEffectiveLocationIdFromHolding(item, holdingsRecord);
    } else {
      setEffectiveLocationIdFromItem(item);
    }
  }

  public static void updateItemEffectiveLocation(Item newItem, PatchData patchData) {
    var holdingsRecord = patchData.getNewHoldings();
    var oldItem = patchData.getOldItem();
    var itemPatchFields = patchData.getPatchRequest().getAdditionalProperties();
    boolean allItemLocationsNull = isNull(newItem.getPermanentLocationId())
      && isNull(newItem.getTemporaryLocationId())
      && isNull(oldItem.getPermanentLocationId())
      && isNull(oldItem.getTemporaryLocationId());

    if (allItemLocationsNull) {
      setEffectiveLocationIdFromHolding(newItem, holdingsRecord);
    } else {
      if (nonNull(newItem.getTemporaryLocationId())) {
        newItem.setEffectiveLocationId(newItem.getTemporaryLocationId());
      } else if (nonNull(oldItem.getTemporaryLocationId()) && !itemPatchFields.containsKey("temporaryLocationId")) {
        newItem.setEffectiveLocationId(oldItem.getTemporaryLocationId());
      } else if (nonNull(newItem.getPermanentLocationId())) {
        newItem.setEffectiveLocationId(newItem.getPermanentLocationId());
      } else if (nonNull(oldItem.getPermanentLocationId()) && !itemPatchFields.containsKey("permanentLocationId")) {
        newItem.setEffectiveLocationId(oldItem.getPermanentLocationId());
      } else {
        setEffectiveLocationIdFromHolding(newItem, holdingsRecord);
      }
    }
  }

  private static void setEffectiveLocationIdFromHolding(Item newItem, HoldingsRecord holdingsRecord) {
    newItem.setEffectiveLocationId(isNull(holdingsRecord.getTemporaryLocationId())
      ? holdingsRecord.getPermanentLocationId()
      : holdingsRecord.getTemporaryLocationId());
  }

  private static void setEffectiveLocationIdFromItem(Item item) {
    item.setEffectiveLocationId(isNull(item.getTemporaryLocationId())
      ? item.getPermanentLocationId()
      : item.getTemporaryLocationId());
  }
}
