package org.folio.rest.support.builders;

import java.util.UUID;

import io.vertx.core.json.JsonObject;

public class HoldingRequestBuilder extends JsonRequestBuilder implements Builder {

  private final UUID id;
  private final UUID instanceId;
  private final UUID permanentLocationId;
  private final UUID temporaryLocationId;
  private JsonObject tags;
  private String callNumber;
  private final String hrid;

  public HoldingRequestBuilder() {
    this(
      null,
      null,
      UUID.randomUUID(),
      null,
      null,
      null,
      null);
  }

  private HoldingRequestBuilder(
    UUID id,
    UUID instanceId,
    UUID permanentLocationId,
    UUID temporaryLocationId,
    JsonObject tags,
    String callNumber,
    String hrid) {

    this.id = id;
    this.instanceId = instanceId;
    this.permanentLocationId = permanentLocationId;
    this.temporaryLocationId = temporaryLocationId;
    this.tags = tags;
    this.callNumber = callNumber;
    this.hrid = hrid;
  }

  @Override
  public JsonObject create() {
    JsonObject request = new JsonObject();

    put(request, "id", id);
    put(request, "instanceId", instanceId);
    put(request, "permanentLocationId", permanentLocationId);
    put(request, "temporaryLocationId", temporaryLocationId);
    put(request, "tags", tags);
    put(request, "callNumber", callNumber);
    put(request, "hrid", hrid);

    return request;
  }

  public HoldingRequestBuilder withPermanentLocation(UUID permanentLocationId) {
    return new HoldingRequestBuilder(
      this.id,
      this.instanceId,
      permanentLocationId,
      this.temporaryLocationId,
      this.tags,
      this.callNumber,
      this.hrid);
  }

  public HoldingRequestBuilder withTemporaryLocation(UUID temporaryLocationId) {
    return new HoldingRequestBuilder(
      this.id,
      this.instanceId,
      this.permanentLocationId,
      temporaryLocationId,
      this.tags,
      this.callNumber,
      this.hrid);
  }

  public HoldingRequestBuilder forInstance(UUID instanceId) {
    return new HoldingRequestBuilder(
      this.id,
      instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.tags,
      this.callNumber,
      this.hrid);
  }

  public HoldingRequestBuilder withId(UUID id) {
    return new HoldingRequestBuilder(
      id,
      this.instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.tags,
      this.callNumber,
      this.hrid);
  }

  public HoldingRequestBuilder withTags(JsonObject tags) {
    return new HoldingRequestBuilder(
      this.id,
      this.instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      tags,
      this.callNumber,
      this.hrid);
  }

  public HoldingRequestBuilder withCallNumber(String callNumber) {
    return new HoldingRequestBuilder(
      this.id,
      this.instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.tags,
      callNumber,
      this.hrid);
  }

  public HoldingRequestBuilder withHrid(String hrid) {
    return new HoldingRequestBuilder(
      this.id,
      this.instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.tags,
      this.callNumber,
      hrid);
  }
}
