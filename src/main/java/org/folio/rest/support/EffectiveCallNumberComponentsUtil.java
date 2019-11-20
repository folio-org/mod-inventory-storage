package org.folio.rest.support;

import org.apache.commons.lang.StringUtils;
import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;

/**
 * Utility methods for Effective Call Number Components object.
 */
public final class EffectiveCallNumberComponentsUtil {

  private EffectiveCallNumberComponentsUtil() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  public static EffectiveCallNumberComponents buildComponents(HoldingsRecord holdings, Item item) {
    return updateComponents(new EffectiveCallNumberComponents(), holdings, item);
  }

  private static EffectiveCallNumberComponents updateComponents(
    EffectiveCallNumberComponents components, HoldingsRecord holdings, Item item) {

    String updatedCallNumber = null;
    if (StringUtils.isNotBlank(item.getItemLevelCallNumber())) {
      updatedCallNumber = item.getItemLevelCallNumber();
    } else if (StringUtils.isNotBlank(holdings.getCallNumber())) {
      updatedCallNumber = holdings.getCallNumber();
    }


    String updatedCallNumberPrefix = null;
    if (StringUtils.isNotBlank(item.getItemLevelCallNumberPrefix())) {
      updatedCallNumberPrefix = item.getItemLevelCallNumberPrefix();
    } else if (StringUtils.isNotBlank(holdings.getCallNumberPrefix())) {
      updatedCallNumberPrefix = holdings.getCallNumberPrefix();
    }

    String updatedCallNumberSuffix = null;
    if (StringUtils.isNotBlank(item.getItemLevelCallNumberSuffix())) {
      updatedCallNumberSuffix = item.getItemLevelCallNumberSuffix();
    } else if (StringUtils.isNotBlank(holdings.getCallNumberSuffix())) {
      updatedCallNumberSuffix = holdings.getCallNumberSuffix();
    }

    components.setCallNumber(updatedCallNumber);
    components.setPrefix(updatedCallNumberPrefix);
    components.setSuffix(updatedCallNumberSuffix);
    return components;
  }
}
