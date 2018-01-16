package org.folio.rest.support.builders;

import io.vertx.core.json.JsonObject;

import java.util.UUID;

public class HoldingRequestBuilder extends JsonRequestBuilder implements Builder {

  private final UUID id;
  private final UUID instanceId;
  private final UUID permanentLocationId;

  public HoldingRequestBuilder() {
    this(
      null,
      null,
      UUID.randomUUID());
  }

  private HoldingRequestBuilder(
    UUID id,
    UUID instanceId,
    UUID permanentLocationId) {

    this.id = id;
    this.instanceId = instanceId;
    this.permanentLocationId = permanentLocationId;
  }

  @Override
  public JsonObject create() {
    JsonObject request = new JsonObject();

    put(request, "id", id);
    put(request, "instanceId", instanceId);
    put(request, "permanentLocationId", permanentLocationId);

    return request;
  }

  public HoldingRequestBuilder withPermanentLocation(UUID permanentLocationId) {
    return new HoldingRequestBuilder(
      this.id,
      this.instanceId,
      permanentLocationId);
  }

  public HoldingRequestBuilder forInstance(UUID instanceId) {
    return new HoldingRequestBuilder(
      this.id,
      instanceId,
      this.permanentLocationId);
  }

  public HoldingRequestBuilder withId(UUID id) {
    return new HoldingRequestBuilder(
      id,
      this.instanceId,
      this.permanentLocationId);
  }
}
