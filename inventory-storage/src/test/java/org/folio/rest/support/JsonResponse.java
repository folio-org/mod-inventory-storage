package org.folio.rest.support;

import io.vertx.core.json.JsonObject;

public class JsonResponse extends Response {
  private final JsonObject body;

  public JsonResponse(int statusCode, JsonObject body) {
    super(statusCode);
    this.body = body;
  }

  public JsonObject getBody() {
    return body;
  }
}
