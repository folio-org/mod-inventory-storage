package org.folio.rest.support.builders;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;

public class HoldingRequestBuilder extends JsonRequestBuilder implements Builder {

  private final UUID id;
  private final UUID instanceId;
  private final UUID permanentLocationId;
  private final UUID temporaryLocationId;
  private final String hrid;
  private final Boolean discoverySuppress;
  private final UUID sourceId;
  private final JsonObject tags;
  private final String callNumber;
  private final String callNumberPrefix;
  private final String callNumberSuffix;
  private final String callNumberTypeId;
  private final List<EffectiveCallNumberComponents> additionalCallNumbers;
  private final JsonArray holdingsStatements;
  private final JsonArray holdingsStatementsForIndexes;
  private final JsonArray holdingsStatementsForSupplements;
  private final JsonArray electronicAccess;
  private final List<UUID> statisticalCodeIds;
  private final List<String> administrativeNotes;

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
      new ArrayList<>(),
      null,
      null,
      null,
      null,
      new ArrayList<>(),
      null,
      new ArrayList<>());
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
    List<EffectiveCallNumberComponents> additionalCallNumbers,
    JsonArray holdingsStatements,
    JsonArray holdingsStatementsForIndexes,
    JsonArray holdingsStatementsForSupplements,
    JsonArray electronicAccess,
    List<UUID> statsticalCodeIds,
    UUID sourceId,
    List<String> administrativeNotes) {

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
    this.additionalCallNumbers = additionalCallNumbers;
    this.holdingsStatements = holdingsStatements;
    this.holdingsStatementsForIndexes = holdingsStatementsForIndexes;
    this.holdingsStatementsForSupplements = holdingsStatementsForSupplements;
    this.electronicAccess = electronicAccess;
    this.statisticalCodeIds = statsticalCodeIds;
    this.sourceId = sourceId;
    this.administrativeNotes = administrativeNotes;
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
    put(request, "additionalCallNumbers", new JsonArray(additionalCallNumbers));
    put(request, "hrid", hrid);
    put(request, "discoverySuppress", discoverySuppress);
    put(request, "holdingsStatements", holdingsStatements);
    put(request, "holdingsStatementsForIndexes", holdingsStatementsForIndexes);
    put(request, "holdingsStatementsForSupplements", holdingsStatementsForSupplements);
    put(request, "electronicAccess", electronicAccess);
    put(request, "statisticalCodeIds", new JsonArray(statisticalCodeIds));
    put(request, "sourceId", sourceId);
    put(request, "administrativeNotes", new JsonArray(administrativeNotes));

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
      this.additionalCallNumbers,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements,
      this.electronicAccess,
      this.statisticalCodeIds,
      this.sourceId,
      this.administrativeNotes);
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
      this.additionalCallNumbers,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements,
      this.electronicAccess,
      this.statisticalCodeIds,
      this.sourceId,
      this.administrativeNotes);
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
      this.additionalCallNumbers,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements,
      this.electronicAccess,
      this.statisticalCodeIds,
      this.sourceId,
      this.administrativeNotes);
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
      this.additionalCallNumbers,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements,
      this.electronicAccess,
      this.statisticalCodeIds,
      this.sourceId,
      this.administrativeNotes);
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
      this.additionalCallNumbers,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements,
      this.electronicAccess,
      this.statisticalCodeIds,
      this.sourceId,
      this.administrativeNotes);
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
      this.additionalCallNumbers,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements,
      this.electronicAccess,
      this.statisticalCodeIds,
      this.sourceId,
      this.administrativeNotes);
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
      this.additionalCallNumbers,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements,
      this.electronicAccess,
      this.statisticalCodeIds,
      this.sourceId,
      this.administrativeNotes);
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
      this.additionalCallNumbers,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements,
      this.electronicAccess,
      this.statisticalCodeIds,
      this.sourceId,
      this.administrativeNotes);
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
      this.additionalCallNumbers,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements,
      this.electronicAccess,
      this.statisticalCodeIds,
      this.sourceId,
      this.administrativeNotes);
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
      this.additionalCallNumbers,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements,
      this.electronicAccess,
      this.statisticalCodeIds,
      this.sourceId,
      this.administrativeNotes);
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
      this.additionalCallNumbers,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements,
      this.electronicAccess,
      this.statisticalCodeIds,
      this.sourceId,
      this.administrativeNotes);
  }

  public HoldingRequestBuilder withAdditionalCallNumbers(List<EffectiveCallNumberComponents> additionalCallNumbers) {
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
      additionalCallNumbers,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements,
      this.electronicAccess,
      this.statisticalCodeIds,
      this.sourceId,
      this.administrativeNotes
    );
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
      this.additionalCallNumbers,
      holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements,
      this.electronicAccess,
      this.statisticalCodeIds,
      this.sourceId,
      this.administrativeNotes);
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
      this.additionalCallNumbers,
      this.holdingsStatements,
      holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements,
      this.electronicAccess,
      this.statisticalCodeIds,
      this.sourceId,
      this.administrativeNotes);
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
      this.additionalCallNumbers,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      holdingsStatementsForSupplements,
      this.electronicAccess,
      this.statisticalCodeIds,
      this.sourceId,
      this.administrativeNotes);
  }

  public HoldingRequestBuilder withElectronicAccess(JsonArray electronicAccess) {
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
      this.additionalCallNumbers,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements,
      electronicAccess,
      this.statisticalCodeIds,
      this.sourceId,
      this.administrativeNotes);
  }

  public HoldingRequestBuilder withStatisticalCodeIds(List<UUID> statisticalCodeIds) {
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
      this.additionalCallNumbers,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements,
      this.electronicAccess,
      statisticalCodeIds,
      this.sourceId,
      this.administrativeNotes);
  }

  public HoldingRequestBuilder withSource(UUID holdingsRecordsSourceId) {
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
      this.additionalCallNumbers,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements,
      this.electronicAccess,
      this.statisticalCodeIds,
      holdingsRecordsSourceId,
      this.administrativeNotes);
  }

  public HoldingRequestBuilder withAdministrativeNotes(List<String> administrativeNotes) {
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
      this.additionalCallNumbers,
      this.holdingsStatements,
      this.holdingsStatementsForIndexes,
      this.holdingsStatementsForSupplements,
      this.electronicAccess,
      this.statisticalCodeIds,
      this.sourceId,
      administrativeNotes);
  }
}
