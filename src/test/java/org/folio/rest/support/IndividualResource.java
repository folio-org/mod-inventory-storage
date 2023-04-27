package org.folio.rest.support;

import io.vertx.core.json.JsonObject;
import java.util.UUID;

public class IndividualResource {

  private final JsonObject response;

  public IndividualResource(Response response) {
    this.response = response.getJson();
  }

  public IndividualResource(JsonObject response) {
    this.response = response.copy();
  }

  public UUID getId() {
    return UUID.fromString(response.getString("id"));
  }

  public JsonObject getJson() {
    return response.copy();
  }

  public JsonObject copyJson() {
    return response.copy();
  }
}
