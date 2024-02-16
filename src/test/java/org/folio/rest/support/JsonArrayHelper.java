package org.folio.rest.support;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Objects;

public class JsonArrayHelper {
  public static List<JsonObject> toList(JsonArray array) {
    return array
      .stream()
      .map(item -> {
        if (item instanceof JsonObject) {
          return (JsonObject) item;
        } else {
          return null;
        }
      })
      .filter(Objects::nonNull)
      .toList();
  }
}
