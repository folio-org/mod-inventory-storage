package org.folio.rest.support;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.services.item.effectivevalues.ItemWithHolding;

public final class EffectiveCallNumberComponentsUtil {
  private EffectiveCallNumberComponentsUtil() {}

  public static ItemWithHolding setCallNumberComponents(ItemWithHolding itemWithHolding) {
    return itemWithHolding.withEffectiveCallNumberComponents(buildComponents(itemWithHolding));
  }

  private static EffectiveCallNumberComponents buildComponents(ItemWithHolding itemWithHolding) {
    final Item item = itemWithHolding.getItem();
    final HoldingsRecord holdings = itemWithHolding.getHoldingsRecord();

    String updatedCallNumber = null;
    if (isNotBlank(item.getItemLevelCallNumber())) {
      updatedCallNumber = item.getItemLevelCallNumber();
    } else if (isNotBlank(holdings.getCallNumber())) {
      updatedCallNumber = holdings.getCallNumber();
    }

    String updatedCallNumberPrefix = null;
    if (isNotBlank(item.getItemLevelCallNumberPrefix())) {
      updatedCallNumberPrefix = item.getItemLevelCallNumberPrefix();
    } else if (isNotBlank(holdings.getCallNumberPrefix())) {
      updatedCallNumberPrefix = holdings.getCallNumberPrefix();
    }

    String updatedCallNumberSuffix = null;
    if (isNotBlank(item.getItemLevelCallNumberSuffix())) {
      updatedCallNumberSuffix = item.getItemLevelCallNumberSuffix();
    } else if (isNotBlank(holdings.getCallNumberSuffix())) {
      updatedCallNumberSuffix = holdings.getCallNumberSuffix();
    }

    String updatedCallNumberTypeId = StringUtils.firstNonBlank(
      item.getItemLevelCallNumberTypeId(),
      holdings != null ? holdings.getCallNumberTypeId() : null);

    return new EffectiveCallNumberComponents()
      .withCallNumber(updatedCallNumber)
      .withPrefix(updatedCallNumberPrefix)
      .withSuffix(updatedCallNumberSuffix)
      .withTypeId(updatedCallNumberTypeId);
  }
}
