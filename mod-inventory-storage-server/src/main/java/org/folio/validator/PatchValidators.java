package org.folio.validator;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.util.stream.Collectors;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.Instance;

public final class PatchValidators {
  private PatchValidators() {
  }

  public static Future<JsonObject> checkInstanceFields(JsonObject patchJson) {
    var instanceFields = JsonObject.mapFrom(new Instance()).fieldNames();
    var rejectedFields = patchJson.fieldNames().stream()
      .filter(fieldName -> !instanceFields.contains(fieldName))
      .collect(Collectors.joining(", ", "'", "'"));
    if (!rejectedFields.isEmpty()) {
      return Future.failedFuture(
        new BadRequestException("Field(s) " + rejectedFields + " not allowed in Instance patch"));
    }
    return Future.succeededFuture(patchJson);
  }
}
