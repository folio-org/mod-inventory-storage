package org.folio.services.item.effectivevalues;

import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;

public final class ItemWithHolding {
  private final Item item;
  private final HoldingsRecord holdingsRecord;

  public ItemWithHolding(Item item, HoldingsRecord holdingsRecord) {
    this.item = item;
    this.holdingsRecord = holdingsRecord;
  }

  public ItemWithHolding withEffectiveCallNumberComponents(EffectiveCallNumberComponents components) {
    return withItem(item.withEffectiveCallNumberComponents(components));
  }

  public String getItemId() {
    return item.getId();
  }

  public String getInstanceId() {
    return holdingsRecord.getInstanceId();
  }

  public Item getItem() {
    return this.item;
  }

  public HoldingsRecord getHoldingsRecord() {
    return this.holdingsRecord;
  }

  public ItemWithHolding withItem(Item item) {
    return new ItemWithHolding(item, this.holdingsRecord);
  }
}
