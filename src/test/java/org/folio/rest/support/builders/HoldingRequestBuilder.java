package org.folio.rest.support.builders;

import java.util.UUID;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class HoldingRequestBuilder extends JsonRequestBuilder implements Builder {

  private final UUID id;
  private final UUID instanceId;
  private final UUID permanentLocationId;
  private final UUID temporaryLocationId;
  private JsonObject tags;
  private String callNumber;
  private String callNumberPrefix;
  private String callNumberSuffix;
  private String callNumberTypeId;
  private final String hrid;
  private final Boolean discoverySuppress;
  private JsonArray holdingsStatements;
  private JsonArray holdingsStatementsForIndexes;
  private JsonArray holdingsStatementsForSupplements;

  public HoldingRequestBuilder() {
    this(
      null,
      null,
      UUID.randomUUID(),
      null,
      null,
      null,
      null,
      null,
      null,
      null,
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
    String callNumberPrefix,
    String callNumberSuffix,
    String callNumberTypeId,
    String hrid,
    Boolean discoverySuppress,
    JsonArray holdingsStatements,
    JsonArray holdingsStatementsForIndexes,
    JsonArray holdingsStatementsForSupplements) {

    this.id = id;
    this.instanceId = instanceId;
    this.permanentLocationId = permanentLocationId;
    this.temporaryLocationId = temporaryLocationId;
    this.tags = tags;
    this.callNumber = callNumber;
    this.callNumberPrefix = callNumberPrefix;
    this.callNumberSuffix = callNumberSuffix;
    this.callNumberTypeId = callNumberTypeId;
    this.hrid = hrid;
    this.discoverySuppress = discoverySuppress;
    this.holdingsStatements = holdingsStatements;
    this.holdingsStatementsForIndexes = holdingsStatementsForIndexes;
    this.holdingsStatementsForSupplements = holdingsStatementsForSupplements;
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
    put(request, "callNumberPrefix", callNumberPrefix);
    put(request, "callNumberSuffix", callNumberSuffix);
    put(request, "callNumberTypeId", callNumberTypeId);
    put(request, "hrid", hrid);
    put(request, "discoverySuppress", discoverySuppress);
    put(request, "holdingsStatements", holdingsStatements);
    put(request, "holdingsStatementsForIndexes", holdingsStatementsForIndexes);
    put(request, "holdingsStatementsForSupplements", holdingsStatementsForSupplements);

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
      this.callNumberPrefix,
      this.callNumberSuffix,
      this.callNumberTypeId,
      this.hrid,
      this.discoverySuppress,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements);
  }

  public HoldingRequestBuilder withTemporaryLocation(UUID temporaryLocationId) {
    return new HoldingRequestBuilder(
      this.id,
      this.instanceId,
      this.permanentLocationId,
      temporaryLocationId,
      this.tags,
      this.callNumber,
      this.callNumberPrefix,
      this.callNumberSuffix,
      this.callNumberTypeId,
      this.hrid,
      this.discoverySuppress,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements);
  }

  public HoldingRequestBuilder forInstance(UUID instanceId) {
    return new HoldingRequestBuilder(
      this.id,
      instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.tags,
      this.callNumber,
      this.callNumberPrefix,
      this.callNumberSuffix,
      this.callNumberTypeId,
      this.hrid,
      this.discoverySuppress,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements);
  }

  public HoldingRequestBuilder withId(UUID id) {
    return new HoldingRequestBuilder(
      id,
      this.instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.tags,
      this.callNumber,
      this.callNumberPrefix,
      this.callNumberSuffix,
      this.callNumberTypeId,
      this.hrid,
      this.discoverySuppress,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements);
  }

  public HoldingRequestBuilder withTags(JsonObject tags) {
    return new HoldingRequestBuilder(
      this.id,
      this.instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      tags,
      this.callNumber,
      this.callNumberPrefix,
      this.callNumberSuffix,
      this.callNumberTypeId,
      this.hrid,
      this.discoverySuppress,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements);
  }

  public HoldingRequestBuilder withCallNumber(String callNumber) {
    return new HoldingRequestBuilder(
      this.id,
      this.instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.tags,
      callNumber,
      this.callNumberPrefix,
      this.callNumberSuffix,
      this.callNumberTypeId,
      this.hrid,
      this.discoverySuppress,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements);
  }

  public HoldingRequestBuilder withCallNumberPrefix(String callNumberPrefix) {
    return new HoldingRequestBuilder(
      this.id,
      this.instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.tags,
      this.callNumber,
      callNumberPrefix,
      this.callNumberSuffix,
      this.callNumberTypeId,
      this.hrid,
      this.discoverySuppress,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements);
  }

  public HoldingRequestBuilder withCallNumberSuffix(String callNumberSuffix) {
    return new HoldingRequestBuilder(
      this.id,
      this.instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.tags,
      this.callNumber,
      this.callNumberPrefix,
      callNumberSuffix,
      this.callNumberTypeId,
      this.hrid,
      this.discoverySuppress,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements);
  }

  public HoldingRequestBuilder withCallNumberTypeId(String callNumberTypeId) {
    return new HoldingRequestBuilder(
      this.id,
      this.instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.tags,
      this.callNumber,
      this.callNumberPrefix,
      this.callNumberSuffix,
      callNumberTypeId,
      this.hrid,
      this.discoverySuppress,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements);
  }

  public HoldingRequestBuilder withHrid(String hrid) {
    return new HoldingRequestBuilder(
      this.id,
      this.instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.tags,
      this.callNumber,
      this.callNumberPrefix,
      this.callNumberSuffix,
      this.callNumberTypeId,
      hrid,
      this.discoverySuppress,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements);
  }

  public HoldingRequestBuilder withDiscoverySuppress(Boolean discoverySuppress) {
    return new HoldingRequestBuilder(
      this.id,
      this.instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.tags,
      this.callNumber,
      this.callNumberPrefix,
      this.callNumberSuffix,
      this.callNumberTypeId,
      this.hrid,
      discoverySuppress,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements);
  }

  public HoldingRequestBuilder withHoldingsStatements(JsonArray holdingsStatements) {
    return new HoldingRequestBuilder(
      this.id,
      this.instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.tags,
      this.callNumber,
      this.callNumberPrefix,
      this.callNumberSuffix,
      this.callNumberTypeId,
      this.hrid,
      this.discoverySuppress,
      holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements);
  }

  public HoldingRequestBuilder withHoldingsStatementsForIndexes(JsonArray holdingsStatementsForIndexes) {
    return new HoldingRequestBuilder(
      this.id,
      this.instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.tags,
      this.callNumber,
      this.callNumberPrefix,
      this.callNumberSuffix,
      this.callNumberTypeId,
      this.hrid,
      this.discoverySuppress,
      this.holdingsStatements,
      holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements);
  }

  public HoldingRequestBuilder withHoldingsStatementsForSupplements(JsonArray holdingsStatementsForSupplements) {
    return new HoldingRequestBuilder(
      this.id,
      this.instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.tags,
      this.callNumber,
      this.callNumberPrefix,
      this.callNumberSuffix,
      this.callNumberTypeId,
      this.hrid,
      this.discoverySuppress,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      holdingsStatementsForSupplements);
  }
}
