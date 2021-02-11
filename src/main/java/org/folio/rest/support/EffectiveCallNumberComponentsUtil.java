package org.folio.rest.support;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.services.CallNumberUtils;

import java.util.Optional;

public final class EffectiveCallNumberComponentsUtil {
  private EffectiveCallNumberComponentsUtil() {}

  public static void setCallNumberComponents(Item item, HoldingsRecord hr) {
    item.setEffectiveCallNumberComponents(buildComponents(item, hr));
    setShelvesNumber(item);
  }

  public static void setShelvesNumber(Item item) {
    if (isNotBlank(item.getEffectiveCallNumberComponents().getCallNumber())) {
      Optional<String> shelfKey = CallNumberUtils.getShelfKeyFromCallNumber(
        item.getEffectiveCallNumberComponents().getCallNumber()
      );
      if (shelfKey.isPresent()) item.setShelvingOrder(shelfKey.get());
    }
  }

  private static EffectiveCallNumberComponents buildComponents(Item item, HoldingsRecord holdings) {
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
