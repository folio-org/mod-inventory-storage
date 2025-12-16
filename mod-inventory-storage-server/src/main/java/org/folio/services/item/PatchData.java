package org.folio.services.item;

import java.util.Objects;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.ItemPatch;

public class PatchData {
  private Item oldItem;
  private Item newItem;
  private HoldingsRecord oldHoldings;
  private HoldingsRecord newHoldings;
  private ItemPatch patchRequest;

  public Item getOldItem() {
    return oldItem;
  }

  public Item getNewItem() {
    return newItem;
  }

  public HoldingsRecord getOldHoldings() {
    return oldHoldings;
  }

  public HoldingsRecord getNewHoldings() {
    return newHoldings;
  }

  public ItemPatch getPatchRequest() {
    return patchRequest;
  }

  public void setOldItem(Item oldItem) {
    this.oldItem = oldItem;
  }

  public void setNewItem(Item newItem) {
    this.newItem = newItem;
  }

  public void setOldHoldings(HoldingsRecord oldHoldings) {
    this.oldHoldings = oldHoldings;
  }

  public void setNewHoldings(HoldingsRecord newHoldings) {
    this.newHoldings = newHoldings;
  }

  public void setPatchRequest(ItemPatch patchRequest) {
    this.patchRequest = patchRequest;
  }

  public boolean hasChanges() {
    var additionalProperties = patchRequest.getAdditionalProperties();

    // If no additional properties beyond id and _version, means no changes for patch request
    if (additionalProperties == null || additionalProperties.isEmpty()) {
      return false;
    }

    // If only holdingsRecordId is present, check if it's different from old item
    if (additionalProperties.size() == 1 && additionalProperties.containsKey("holdingsRecordId")) {
      var patchHoldingsId = (String) additionalProperties.get("holdingsRecordId");
      var oldHoldingsId = oldItem.getHoldingsRecordId();
      return !Objects.equals(patchHoldingsId, oldHoldingsId);
    }

    // Any other additional properties mean there's a change
    return true;
  }
}
