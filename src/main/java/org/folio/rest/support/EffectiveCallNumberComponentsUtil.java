package org.folio.rest.support;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.apache.commons.lang3.StringUtils;
import org.assertj.core.util.Arrays;
import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.services.CallNumberUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class EffectiveCallNumberComponentsUtil {
  private EffectiveCallNumberComponentsUtil() {}

  public static void setCallNumberComponents(Item item, HoldingsRecord hr) {
    item.setEffectiveCallNumberComponents(buildComponents(item, hr));
  }

  public static void calculateAndSetEffectiveShelvingOrder(Item item) {
    if (isNotBlank(item.getEffectiveCallNumberComponents().getCallNumber())) {
      List<String> argLIst = Stream.of(
        item.getEffectiveCallNumberComponents().getCallNumber(),
        item.getVolume(),
        item.getEnumeration(),
        item.getChronology(),
        item.getCopyNumber()
      ).collect(Collectors.toList());

      StringBuilder arg = new StringBuilder();
      for (String xs : argLIst) {
        String argValue = Objects.toString(xs, "").trim();
        if (!arg.toString().isEmpty() && !argValue.isEmpty()) {
          arg.append(" ");
        }
        arg.append(argValue);
      }

      Optional<String> shelfKey
        = CallNumberUtils.getShelfKeyFromCallNumber(arg.toString().trim());
      String suffixValue = Objects.toString(item.getEffectiveCallNumberComponents().getSuffix(), "").trim();
      if (shelfKey.isPresent()) {
        item.setEffectiveShelvingOrder(shelfKey.get()
          + (suffixValue.isEmpty() ? "" : " " + suffixValue));
      } else {
        item.setEffectiveShelvingOrder("");
      }
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
