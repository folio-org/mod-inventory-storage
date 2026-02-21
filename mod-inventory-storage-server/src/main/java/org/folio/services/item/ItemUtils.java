package org.folio.services.item;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.rest.impl.ItemStorageApi.ITEM_TABLE;
import static org.folio.validator.CommonValidators.normalizeProperty;

import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.Logger;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.ItemPatchRequest;
import org.folio.rest.jaxrs.resource.ItemStorage;
import org.folio.rest.persist.PgUtil;

public final class ItemUtils {

  private static final Logger log = getLogger(ItemUtils.class);
  // Constants for item field names used in normalization
  private static final String ORDER = "order";
  private static final String DISCOVERY_SUPPRESS = "discoverySuppress";
  private static final String NOTES = "notes";
  private static final String CIRCULATION_NOTES = "circulationNotes";
  private static final String STAFF_ONLY = "staffOnly";

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

  private static List<String> collectMissingFields(Map<String, Object> additionalProperties) {
    var missingFields = new ArrayList<String>();

    if (additionalProperties.containsKey("materialTypeId")
        && isNullOrBlank(additionalProperties.get("materialTypeId"))) {
      missingFields.add("materialTypeId");
    }

    if (additionalProperties.containsKey("permanentLoanTypeId")
        && isNullOrBlank(additionalProperties.get("permanentLoanTypeId"))) {
      missingFields.add("permanentLoanTypeId");
    }

    if (additionalProperties.containsKey("holdingsRecordId")
        && isNullOrBlank(additionalProperties.get("holdingsRecordId"))) {
      missingFields.add("holdingsRecordId");
    }

    // Check status object and its required name field
    if (additionalProperties.containsKey("status")) {
      var status = additionalProperties.get("status");
      if (status == null) {
        missingFields.add("status");
      } else if (status instanceof Map<?, ?> statusMap
          && isNullOrBlank(statusMap.get("name"))) {
        missingFields.add("status.name");
      }
    }

    return missingFields;
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

  public static Future<Response> handleUpdateItemsError(Throwable throwable) {
    var responseClass = ItemStorage.PatchItemStorageItemsResponse.class;
    try {
      var respond500 = responseClass.getMethod("respond500WithTextPlain", Object.class);
      return PgUtil.response(ITEM_TABLE, "", throwable, responseClass, respond500, respond500)
        .compose(response -> {
          if (response.getEntity() instanceof Errors errors) {
            return Future.failedFuture(new ValidationException(errors));
          }
          return Future.failedFuture(throwable);
        });
    } catch (NoSuchMethodException e) {
      log.error("handleUpdateItemsError:: Failed to get respond500WithTextPlain method", e);
      return Future.failedFuture(throwable);
    }
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
