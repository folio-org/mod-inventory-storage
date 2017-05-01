package org.folio.rest.support;

import io.vertx.core.json.JsonObject;

public class JsonResponse extends TextResponse {
  public JsonResponse(int statusCode, String body) {
    super(statusCode, body);
  }

  public JsonObject getJson() {
    return new JsonObject(getBody());
  }
}
