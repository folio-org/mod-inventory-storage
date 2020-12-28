package org.folio.rest.support;

import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.services.item.effectivevalues.ItemWithHolding;

import static java.util.Objects.isNull;

public final class ItemEffectiveLocationUtil {

  private ItemEffectiveLocationUtil(){}

  public static ItemWithHolding updateItemEffectiveLocation(ItemWithHolding itemWithHolding) {
    final Item item = itemWithHolding.getItem();
    final HoldingsRecord holdingsRecord = itemWithHolding.getHoldingsRecord();

    if (isNull(item.getPermanentLocationId()) && isNull(item.getTemporaryLocationId())) {
      item.setEffectiveLocationId(isNull(holdingsRecord.getTemporaryLocationId()) ?
        holdingsRecord.getPermanentLocationId() : holdingsRecord.getTemporaryLocationId());
    } else {
      item.setEffectiveLocationId(isNull(item.getTemporaryLocationId()) ?
        item.getPermanentLocationId() : item.getTemporaryLocationId());
    }

    return itemWithHolding;
  }
}
