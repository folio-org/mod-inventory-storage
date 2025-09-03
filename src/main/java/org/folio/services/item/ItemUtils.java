package org.folio.services.item;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.rest.impl.ItemStorageApi.ITEM_TABLE;

import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
}
