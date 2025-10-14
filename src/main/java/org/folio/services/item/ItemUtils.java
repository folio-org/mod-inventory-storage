package org.folio.services.item;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.rest.impl.ItemStorageApi.ITEM_TABLE;
import static org.folio.validator.CommonValidators.normalizeIfList;
import static org.folio.validator.CommonValidators.normalizeIfMap;
import static org.folio.validator.CommonValidators.normalizeProperty;

import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.Logger;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.ItemPatch;
import org.folio.rest.jaxrs.resource.ItemStorage;
import org.folio.rest.persist.PgUtil;

public final class ItemUtils {

  private static final Logger log = getLogger(ItemUtils.class);
  // Constants for item field names used in normalization
  private static final String ORDER = "order";
  private static final String DISCOVERY_SUPPRESS = "discoverySuppress";
  private static final String NOTES = "notes";
  private static final String CIRCULATION_NOTES = "circulationNotes";
  private static final String PERMANENT_LOCATION = "permanentLocation";
  private static final String TEMPORARY_LOCATION = "temporaryLocation";
  private static final String HOLDINGS_RECORD_2 = "holdingsRecord2";
  private static final String VERSION = "_version";
  private static final String IS_ACTIVE = "isActive";
  private static final String IS_FLOATING_COLLECTION = "isFloatingCollection";
  private static final String IS_SHADOW = "isShadow";
  private static final String INSTITUTION = "institution";
  private static final String CAMPUS = "campus";
  private static final String LIBRARY = "library";
  private static final String PRIMARY_SERVICE_POINT_OBJECT = "primaryServicePointObject";
  private static final String SERVICE_POINTS = "servicePoints";
  private static final String PICKUP_LOCATION = "pickupLocation";
  private static final String ECS_REQUEST_ROUTING = "ecsRequestRouting";
  private static final String SHELVING_LAG_TIME = "shelvingLagTime";
  private static final String HOLD_SHELF_EXPIRY_PERIOD = "holdShelfExpiryPeriod";
  private static final String DURATION = "duration";
  private static final String STAFF_SLIPS = "staffSlips";
  private static final String PRINT_BY_DEFAULT = "printByDefault";
  private static final String STAFF_ONLY = "staffOnly";

  private ItemUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static Future<Void> validateRequiredFields(List<ItemPatch> items) {
    var errors = new ArrayList<Error>();

    for (var itemPatch : items) {
      var additionalProperties = itemPatch.getAdditionalProperties();
      if (additionalProperties == null) {
        continue; // No additional properties to validate
      }

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

  public static void transferEffectiveValuesToPatch(Item item, ItemPatch itemPatch) {
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

  public static void normalizeItemFields(List<ItemPatch> items) {
    items.stream()
      .map(ItemPatch::getAdditionalProperties)
      .filter(props -> Objects.nonNull(props) && !props.isEmpty())
      .forEach(props -> {
        normalizeProperty(props, ORDER, Integer::valueOf);
        normalizeProperty(props, DISCOVERY_SUPPRESS, Boolean::valueOf);

        // normalize "staffOnly" property in notes and circulationNotes lists
        normalizeStaffOnlyInNestedLists(props, NOTES, CIRCULATION_NOTES);

        // normalize locations
        normalizeIfMap(props, PERMANENT_LOCATION, ItemUtils::normalizeLocation);
        normalizeIfMap(props, TEMPORARY_LOCATION, ItemUtils::normalizeLocation);

        // normalize holdingsRecord2
        normalizeIfMap(props, HOLDINGS_RECORD_2, holdingsProps -> {
          normalizeProperty(holdingsProps, VERSION, Integer::valueOf);
          normalizeProperty(holdingsProps, DISCOVERY_SUPPRESS, Boolean::valueOf);
          normalizeStaffOnlyInNestedLists(holdingsProps, NOTES);
        });
      });
  }

  private static void normalizeLocation(Map<String, Object> location) {
    normalizeProperty(location, IS_ACTIVE, Boolean::valueOf);
    normalizeProperty(location, IS_FLOATING_COLLECTION, Boolean::valueOf);
    normalizeProperty(location, IS_SHADOW, Boolean::valueOf);
    normalizeIsShadowInNestedObjects(location, INSTITUTION, CAMPUS, LIBRARY);

    normalizeIfMap(location, PRIMARY_SERVICE_POINT_OBJECT, ItemUtils::normalizeServicePoint);
    normalizeIfList(location, SERVICE_POINTS, ItemUtils::normalizeServicePoint);
  }

  private static void normalizeServicePoint(Map<String, Object> servicePoint) {
    normalizeProperty(servicePoint, PICKUP_LOCATION, Boolean::valueOf);
    normalizeProperty(servicePoint, ECS_REQUEST_ROUTING, Boolean::valueOf);
    normalizeProperty(servicePoint, SHELVING_LAG_TIME, Integer::valueOf);

    normalizeIfMap(servicePoint, HOLD_SHELF_EXPIRY_PERIOD, holdShelfExpiryPeriod ->
      normalizeProperty(holdShelfExpiryPeriod, DURATION, Integer::valueOf));

    normalizeIfMap(servicePoint, STAFF_SLIPS, staffSlips ->
      normalizeProperty(staffSlips, PRINT_BY_DEFAULT, Boolean::valueOf));
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

  private static void normalizeIsShadowInNestedObjects(Map<String, Object> props, String... fields) {
    Stream.of(fields)
      .map(props::get)
      .filter(Map.class::isInstance)
      .map(value -> (Map<String, Object>) value)
      .forEach(nestedMap -> normalizeProperty(nestedMap, IS_SHADOW, Boolean::valueOf));
  }
}
