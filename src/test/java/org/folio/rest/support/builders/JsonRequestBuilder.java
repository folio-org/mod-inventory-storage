package org.folio.rest.support.builders;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.UUID;

public class JsonRequestBuilder {
  protected void put(JsonObject request, String property, String value) {
    if (value != null) {
      request.put(property, value);
    }
  }

  protected void put(JsonObject request, String property, Boolean value) {
    if (value != null) {
      request.put(property, value);
    }
  }

  protected void put(JsonObject request, String property, UUID value) {
    if (value != null) {
      request.put(property, value.toString());
    }
  }

  protected void put(JsonObject request, String property, JsonObject value) {
    if (value != null) {
      request.put(property, value);
    }
  }

  protected void put(JsonObject request, String property, JsonArray value) {
    if (value != null) {
      request.put(property, value);
    }
  }
}
