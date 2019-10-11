package org.folio.rest.support.builders;

import java.util.UUID;

import io.vertx.core.json.JsonObject;

public class HoldingRequestBuilder extends JsonRequestBuilder implements Builder {

  private final UUID id;
  private final UUID instanceId;
  private final UUID permanentLocationId;
  private JsonObject tags;
  private String callNumber;

  public HoldingRequestBuilder() {
    this(
      null,
      null,
      UUID.randomUUID(),
      null,
      null);
  }

  private HoldingRequestBuilder(
    UUID id,
    UUID instanceId,
    UUID permanentLocationId,
    JsonObject tags,
    String callNumber) {

    this.id = id;
    this.instanceId = instanceId;
    this.permanentLocationId = permanentLocationId;
    this.tags = tags;
    this.callNumber = callNumber;
  }

  @Override
  public JsonObject create() {
    JsonObject request = new JsonObject();

    put(request, "id", id);
    put(request, "instanceId", instanceId);
    put(request, "permanentLocationId", permanentLocationId);
    put(request, "tags", tags);
    put(request, "callNumber", callNumber);

    return request;
  }

  public HoldingRequestBuilder withPermanentLocation(UUID permanentLocationId) {
    return new HoldingRequestBuilder(
      this.id,
      this.instanceId,
      permanentLocationId,
      this.tags,
      this.callNumber);
  }

  public HoldingRequestBuilder forInstance(UUID instanceId) {
    return new HoldingRequestBuilder(
      this.id,
      instanceId,
      this.permanentLocationId,
      this.tags,
      this.callNumber);
  }

  public HoldingRequestBuilder withId(UUID id) {
    return new HoldingRequestBuilder(
      id,
      this.instanceId,
      this.permanentLocationId,
      this.tags,
      this.callNumber);
  }

  public HoldingRequestBuilder withTags(JsonObject tags) {
    return new HoldingRequestBuilder(
      this.id,
      this.instanceId,
      this.permanentLocationId,
      tags,
      this.callNumber);
  }

  public HoldingRequestBuilder withCallNumber(String callNumber) {
    return new HoldingRequestBuilder(
        this.id,
        this.instanceId,
        this.permanentLocationId,
        this.tags,
        callNumber);
  }
}
