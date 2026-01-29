package org.folio.validator;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.Instance;

public final class PatchValidators {
  private PatchValidators() {
  }

  public static Future<JsonObject> checkInstanceFields(JsonObject patchJson) {
    var instanceFields = Arrays.stream(FieldUtils.getFieldsWithAnnotation(Instance.class, JsonProperty.class))
      .map(field -> field.getAnnotation(JsonProperty.class).value())
      .collect(Collectors.toSet());
    var rejectedFields = patchJson.fieldNames().stream()
      .filter(fieldName -> !instanceFields.contains(fieldName))
      .collect(Collectors.joining(", "));
    if (!rejectedFields.isEmpty()) {
      return Future.failedFuture(
        new BadRequestException("Field(s) '" + rejectedFields + "' not allowed in Instance patch"));
    }
    return Future.succeededFuture(patchJson);
  }
}
