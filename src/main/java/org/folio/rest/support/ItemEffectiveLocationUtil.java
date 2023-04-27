package org.folio.rest.support;

import static java.util.Objects.isNull;

import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;

public final class ItemEffectiveLocationUtil {

  private ItemEffectiveLocationUtil() { }

  public static void updateItemEffectiveLocation(Item item, HoldingsRecord holdingsRecord) {
    if (isNull(item.getPermanentLocationId()) && isNull(item.getTemporaryLocationId())) {
      item.setEffectiveLocationId(isNull(holdingsRecord.getTemporaryLocationId())
                                  ? holdingsRecord.getPermanentLocationId()
                                  : holdingsRecord.getTemporaryLocationId());
    } else {
      item.setEffectiveLocationId(isNull(item.getTemporaryLocationId())
                                  ? item.getPermanentLocationId()
                                  : item.getTemporaryLocationId());
    }
  }
}
