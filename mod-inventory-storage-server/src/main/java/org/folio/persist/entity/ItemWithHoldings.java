package org.folio.persist.entity;

import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;

/**
 * An item together with a holdings record.
 *
 * @param item     the item
 * @param holdings the holdings record, null if there is none
 */
public record ItemWithHoldings(Item item, HoldingsRecord holdings) {
}
