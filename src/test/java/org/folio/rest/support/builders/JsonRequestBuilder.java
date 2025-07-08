package org.folio.rest.support.builders;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.UUID;

public class JsonRequestBuilder {

  protected void put(JsonObject request, String property, String value) {
    putValue(request, property, value);
  }

  protected void put(JsonObject request, String property, Integer value) {
    putValue(request, property, value);
  }

  protected void put(JsonObject request, String property, Boolean value) {
    putValue(request, property, value);
  }

  protected void put(JsonObject request, String property, UUID value) {
    putValue(request, property, value);
  }

  protected void put(JsonObject request, String property, JsonObject value) {
    putValue(request, property, value);
  }

  protected void put(JsonObject request, String property, JsonArray value) {
    putValue(request, property, value);
  }

  private void putValue(JsonObject request, String property, Object value) {
    if (value != null) {
      request.put(property, value);
    }
  }
}
