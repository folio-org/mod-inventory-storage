package org.folio.validator;

import static io.vertx.core.Future.failedFuture;
import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.util.Set;
import java.util.regex.Pattern;
import org.folio.rest.exceptions.ValidationException;

public final class HoldingsValidators {
  private static final Pattern UUID_REGEX =
    Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

  private HoldingsValidators() { }

  public static Future<JsonObject> checkRequiredFieldsIfPresent(JsonObject patchJson) {
    for (String fieldName : Set.of("sourceId", "instanceId", "permanentLocationId")) {
      if (patchJson.containsKey(fieldName)) {
        String value = patchJson.getString(fieldName);
        if (!isValidUuid(value)) {
          return failedFuture(new ValidationException(
            createValidationErrorMessage(
              fieldName, value, "%s value is not valid UUID: %s".formatted(fieldName, value))));
        }
      }
    }
    return Future.succeededFuture(patchJson);
  }

  private static boolean isValidUuid(String value) {
    return value != null && UUID_REGEX.matcher(value).matches();
  }
}
