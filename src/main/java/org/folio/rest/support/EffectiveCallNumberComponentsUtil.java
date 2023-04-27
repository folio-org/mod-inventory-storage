package org.folio.rest.support;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.services.CallNumberUtils;

public final class EffectiveCallNumberComponentsUtil {

  private EffectiveCallNumberComponentsUtil() { }

  public static void setCallNumberComponents(Item item, HoldingsRecord hr) {
    item.setEffectiveCallNumberComponents(buildComponents(item, hr));
  }

  public static Item calculateAndSetEffectiveShelvingOrder(Item item) {
    if (isNotBlank(item.getEffectiveCallNumberComponents().getCallNumber())) {
      Optional<String> shelfKey
        = CallNumberUtils.getShelfKeyFromCallNumber(
        Stream.of(
            item.getEffectiveCallNumberComponents().getCallNumber(),
            item.getVolume(),
            item.getEnumeration(),
            item.getChronology(),
            item.getCopyNumber()
          ).filter(StringUtils::isNotBlank)
          .map(StringUtils::trim)
          .collect(Collectors.joining(" "))
      );
      String suffixValue =
        Objects.toString(Optional.ofNullable(item.getEffectiveCallNumberComponents())
            .orElse(new EffectiveCallNumberComponents()).getSuffix(), "")
          .trim();

      String nonNullableSuffixValue = suffixValue.isEmpty() ? "" : " " + suffixValue;

      item.setEffectiveShelvingOrder(
        shelfKey.stream()
          .map(shelfKeyValue -> shelfKeyValue + nonNullableSuffixValue)
          .findFirst()
          .orElse(nonNullableSuffixValue));
    }

    return item;
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
