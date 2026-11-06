package org.folio.services.item;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.persist.ItemRepository.ITEM_TABLE;
import static org.folio.validator.CommonValidators.normalizeProperty;

import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.logging.log4j.Logger;
import org.folio.persist.helper.PgViolationMapper;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.ItemPatchRequest;

public final class ItemUtils {

  private static final Logger log = getLogger(ItemUtils.class);
  // Constants for item field names used in normalization
  private static final String ORDER = "order";
  private static final String DISCOVERY_SUPPRESS = "discoverySuppress";
  private static final String NOTES = "notes";
  private static final String CIRCULATION_NOTES = "circulationNotes";
  private static final String STAFF_ONLY = "staffOnly";
  private static final String MATERIAL_TYPE_ID = "materialTypeId";
  private static final String PERMANENT_LOAN_TYPE_ID = "permanentLoanTypeId";
  private static final String HOLDINGS_RECORD_ID = "holdingsRecordId";
  private static final String STATUS = "status";

  private ItemUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static Future<Void> validateRequiredFields(List<ItemPatchRequest> items) {
    var errors = new ArrayList<Error>();

    for (var itemPatch : items) {
      var additionalProperties = itemPatch.getAdditionalProperties();
      if (additionalProperties == null) {
        continue; // No additional properties to validate
      }

      var missingFields = collectMissingFields(additionalProperties);

      if (!missingFields.isEmpty()) {
        errors.add(requiredFieldsError(itemPatch.getId(), missingFields));
      }
    }

    if (!errors.isEmpty()) {
      return Future.failedFuture(new ValidationException(new Errors().withErrors(errors)));
    }

    return Future.succeededFuture();
  }

  public static boolean isNullOrBlank(Object value) {
    return value == null || isBlank(String.valueOf(value));
  }

  public static Error requiredFieldsError(String itemId, List<String> fieldNames) {
    var parameters = fieldNames.stream()
      .map(fieldName -> new org.folio.rest.jaxrs.model.Parameter()
        .withKey("field")
        .withValue(fieldName))
      .toList();

    return new Error()
      .withMessage("Required fields cannot be removed. ItemId: " + itemId)
      .withCode("field.required")
      .withParameters(parameters);
  }

  public static void transferEffectiveValuesToPatch(Item item, ItemPatchRequest itemPatch) {
    var additionalProperties = itemPatch.getAdditionalProperties();

    if (item.getEffectiveLocationId() != null) {
      additionalProperties.put("effectiveLocationId", item.getEffectiveLocationId());
    }

    if (item.getEffectiveCallNumberComponents() != null) {
      additionalProperties.put("effectiveCallNumberComponents", item.getEffectiveCallNumberComponents());
    }

    if (item.getEffectiveShelvingOrder() != null) {
      additionalProperties.put("effectiveShelvingOrder", item.getEffectiveShelvingOrder());
    }
  }

  public static <T> Future<T> handleUpdateItemsError(Throwable throwable) {
    return Future.failedFuture(PgViolationMapper.map(ITEM_TABLE, "", throwable));
  }

  public static void normalizeItemFields(List<ItemPatchRequest> items) {
    items.forEach(ItemUtils::normalizeItemFields);
  }

  public static void normalizeItemFields(ItemPatchRequest patchRequest) {
    var additionalProperties = patchRequest.getAdditionalProperties();
    if (nonNull(additionalProperties) && !additionalProperties.isEmpty()) {
      normalizeProperty(additionalProperties, ORDER, Integer::valueOf);
      normalizeProperty(additionalProperties, DISCOVERY_SUPPRESS, Boolean::valueOf);
      // normalize "staffOnly" property in notes and circulationNotes lists
      normalizeStaffOnlyInNestedLists(additionalProperties, NOTES, CIRCULATION_NOTES);
    }
  }

  private static List<String> collectMissingFields(Map<String, Object> additionalProperties) {
    var missingFields = new ArrayList<String>();

    if (additionalProperties.containsKey(MATERIAL_TYPE_ID)
        && isNullOrBlank(additionalProperties.get(MATERIAL_TYPE_ID))) {
      missingFields.add(MATERIAL_TYPE_ID);
    }

    if (additionalProperties.containsKey(PERMANENT_LOAN_TYPE_ID)
        && isNullOrBlank(additionalProperties.get(PERMANENT_LOAN_TYPE_ID))) {
      missingFields.add(PERMANENT_LOAN_TYPE_ID);
    }

    if (additionalProperties.containsKey(HOLDINGS_RECORD_ID)
        && isNullOrBlank(additionalProperties.get(HOLDINGS_RECORD_ID))) {
      missingFields.add(HOLDINGS_RECORD_ID);
    }

    // Check status object and its required name field
    if (additionalProperties.containsKey(STATUS)) {
      var status = additionalProperties.get(STATUS);
      if (status == null) {
        missingFields.add(STATUS);
      } else if (status instanceof Map<?, ?> statusMap
                 && isNullOrBlank(statusMap.get("name"))) {
        missingFields.add("status.name");
      }
    }

    return missingFields;
  }

  private static void normalizeStaffOnlyInNestedLists(Map<String, Object> props, String... fields) {
    Stream.of(fields)
      .map(props::get)
      .filter(value -> value instanceof List<?> list && !list.isEmpty())
      .map(value -> (List<?>) value)
      .forEach(list -> list.stream()
        .filter(Map.class::isInstance)
        .map(field -> (Map<String, Object>) field)
        .forEach(fieldMap -> normalizeProperty(fieldMap, STAFF_ONLY, Boolean::valueOf)));
  }
}
