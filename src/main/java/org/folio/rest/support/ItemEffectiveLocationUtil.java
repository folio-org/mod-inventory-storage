package org.folio.rest.support;

import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;

import static java.util.Objects.isNull;

public final class ItemEffectiveLocationUtil {

  private ItemEffectiveLocationUtil(){}

  public static Item updateItemEffectiveLocation(Item item, HoldingsRecord holdingsRecord) {

    if (isNull(item.getPermanentLocationId()) && isNull(item.getTemporaryLocationId())) {
      item.setEffectiveLocationId(isNull(holdingsRecord.getTemporaryLocationId()) ?
        holdingsRecord.getPermanentLocationId() : holdingsRecord.getTemporaryLocationId());
    } else {
      item.setEffectiveLocationId(isNull(item.getTemporaryLocationId()) ?
        item.getPermanentLocationId() : item.getTemporaryLocationId());
    }

    return item;
  }
}
