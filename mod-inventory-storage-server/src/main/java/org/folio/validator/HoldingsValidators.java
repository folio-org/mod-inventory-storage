package org.folio.validator;

import static io.vertx.core.Future.failedFuture;
import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.util.Set;
import org.folio.rest.exceptions.ValidationException;

public final class HoldingsValidators {
  private HoldingsValidators() { }

  public static Future<JsonObject> refuseNullValueInRequiredFields(JsonObject patchJson) {
    for (String fieldName : Set.of("sourceId", "instanceId", "permanentLocationId")) {
      if (patchJson.containsKey(fieldName)) {
        String value = patchJson.getString(fieldName);
        if (value == null) {
          return failedFuture(new ValidationException(
            createValidationErrorMessage(
              fieldName, value, "'%s' value cannot be null".formatted(fieldName))));
        }
      }
    }
    return Future.succeededFuture(patchJson);
  }
}
