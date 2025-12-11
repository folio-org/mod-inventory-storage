package org.folio.rest.support;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.services.CallNumberUtils;
import org.folio.services.item.PatchData;

public final class EffectiveCallNumberComponentsUtil {

  private EffectiveCallNumberComponentsUtil() { }

  public static void setCallNumberComponents(Item item, HoldingsRecord hr) {
    item.setEffectiveCallNumberComponents(buildComponents(item, hr));
  }

  public static void setCallNumberComponents(Item newItem, PatchData patchData) {
    newItem.setEffectiveCallNumberComponents(buildComponents(patchData));
  }

  public static Item calculateAndSetEffectiveShelvingOrder(Item item) {
    if (isNotBlank(item.getEffectiveCallNumberComponents().getCallNumber())) {
      var shelfKey = calculateShelfKey(item);
      var suffixValue = extractSuffixValue(item);
      var nonNullableSuffixValue = suffixValue.isEmpty() ? "" : " " + suffixValue;

      item.setEffectiveShelvingOrder(
        shelfKey.map(shelfKeyValue -> shelfKeyValue + nonNullableSuffixValue)
          .orElse(nonNullableSuffixValue));
    } else {
      item.setEffectiveShelvingOrder(null);
    }

    return item;
  }

  private static Optional<String> calculateShelfKey(Item item) {
    return CallNumberUtils.getShelfKeyFromCallNumber(
      item.getEffectiveCallNumberComponents().getTypeId(),
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
  }

  private static String extractSuffixValue(Item item) {
    return Objects.toString(Optional.ofNullable(item.getEffectiveCallNumberComponents())
        .orElse(new EffectiveCallNumberComponents()).getSuffix(), "")
      .trim();
  }

  private static EffectiveCallNumberComponents buildComponents(Item item, HoldingsRecord holdings) {
    var updatedCallNumber = resolveCallNumberField(
      item.getItemLevelCallNumber(), holdings, HoldingsRecord::getCallNumber);
    var updatedCallNumberPrefix = resolveCallNumberField(
      item.getItemLevelCallNumberPrefix(), holdings, HoldingsRecord::getCallNumberPrefix);
    var updatedCallNumberSuffix = resolveCallNumberField(
      item.getItemLevelCallNumberSuffix(), holdings, HoldingsRecord::getCallNumberSuffix);
    var updatedCallNumberTypeId = resolveCallNumberField(
      item.getItemLevelCallNumberTypeId(), holdings, HoldingsRecord::getCallNumberTypeId);

    return new EffectiveCallNumberComponents()
      .withCallNumber(updatedCallNumber)
      .withPrefix(updatedCallNumberPrefix)
      .withSuffix(updatedCallNumberSuffix)
      .withTypeId(updatedCallNumberTypeId);
  }

  private static EffectiveCallNumberComponents buildComponents(PatchData patchData) {
    var holdings = patchData.getNewHoldings();
    var itemPatchFields = patchData.getPatchRequest().getAdditionalProperties();
    var oldCallNumberComponents = patchData.getOldItem().getEffectiveCallNumberComponents();

    return new EffectiveCallNumberComponents()
      .withCallNumber(getFieldValue(itemPatchFields, "itemLevelCallNumber",
        () -> oldCallNumberComponents != null ? oldCallNumberComponents.getCallNumber() : null,
        holdings::getCallNumber))
      .withPrefix(getFieldValue(itemPatchFields, "itemLevelCallNumberPrefix",
        () -> oldCallNumberComponents != null ? oldCallNumberComponents.getPrefix() : null,
        holdings::getCallNumberPrefix))
      .withSuffix(getFieldValue(itemPatchFields, "itemLevelCallNumberSuffix",
        () -> oldCallNumberComponents != null ? oldCallNumberComponents.getSuffix() : null,
        holdings::getCallNumberSuffix))
      .withTypeId(getFieldValue(itemPatchFields, "itemLevelCallNumberTypeId",
        () -> oldCallNumberComponents != null ? oldCallNumberComponents.getTypeId() : null,
        holdings::getCallNumberTypeId));
  }

  private static String resolveCallNumberField(String itemLevelValue, HoldingsRecord holdings,
                                               Function<HoldingsRecord, String> holdingsValueExtractor) {
    if (isNotBlank(itemLevelValue)) {
      return itemLevelValue;
    }
    if (holdings == null) {
      return null;
    }
    return StringUtils.getIfBlank(holdingsValueExtractor.apply(holdings), () -> null);
  }

  private static String getFieldValue(Map<String, Object> fields, String key, Supplier<String> oldComponentSupplier,
                                      Supplier<String> holdingsSupplier) {
    if (fields.containsKey(key)) {
      var value = fields.get(key);
      return value != null ? value.toString() : null;
    }
    var oldValue = oldComponentSupplier.get();
    if (oldValue != null) {
      return oldValue;
    }
    var holdingsValue = holdingsSupplier.get();
    return isNotBlank(holdingsValue) ? holdingsValue : null;
  }
}
