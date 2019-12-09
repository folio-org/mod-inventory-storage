package org.folio.rest.support;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;

public final class EffectiveCallNumberComponentsUtil {

  public static EffectiveCallNumberComponents buildComponents(HoldingsRecord holdings, Item item) {
    return updateComponents(new EffectiveCallNumberComponents(), holdings, item);
  }

  private static EffectiveCallNumberComponents updateComponents(
    EffectiveCallNumberComponents components, HoldingsRecord holdings, Item item) {

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
      Optional.ofNullable(holdings).map(HoldingsRecord::getCallNumberTypeId)
        .orElse(null)
    );

    components.setCallNumber(updatedCallNumber);
    components.setPrefix(updatedCallNumberPrefix);
    components.setSuffix(updatedCallNumberSuffix);
    components.setTypeId(updatedCallNumberTypeId);

    return components;
  }
}
