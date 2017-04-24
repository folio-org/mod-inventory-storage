package org.folio.inventory.support;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.stream.Collectors;

public class JsonArrayHelper {
  public static List<JsonObject> toList(JsonArray array) {
    return array
      .stream()
      .map(loan -> {
        if(loan instanceof JsonObject) {
          return (JsonObject)loan;
        }
        else {
          return null;
        }
      })
      .filter(loan -> loan != null)
      .collect(Collectors.toList());
  }
}
