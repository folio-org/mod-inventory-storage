package org.folio.rest.support;

import io.vertx.core.json.JsonObject;

import java.util.List;

public class JsonErrorResponse extends JsonResponse {
  public JsonErrorResponse(int statusCode, String body) {
    super(statusCode, body);
  }

  public List<JsonObject> getErrors() {
    return JsonArrayHelper.toList(getJson().getJsonArray("errors"));
  }
}
