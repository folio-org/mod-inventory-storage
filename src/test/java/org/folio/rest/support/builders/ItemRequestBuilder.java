package org.folio.rest.support.builders;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;

public class ItemRequestBuilder extends JsonRequestBuilder implements Builder {

  private static final String AVAILABLE_STATUS = "Available";
  private static final String CHECKED_OUT_STATUS = "Checked out";

  private final UUID id;
  private final UUID holdingId;
  private final String barcode;
  private final String status;
  private final UUID materialTypeId;
  private final UUID temporaryLocationId;
  private final UUID permanentLoanTypeId;
  private final UUID temporaryLoanTypeId;
  private final String itemLevelCallNumberPrefix;
  private final String itemLevelCallNumber;
  private final String itemLevelCallNumberSuffix;
  private final String itemLevelCallNumberTypeId;
  private final List<EffectiveCallNumberComponents> itemLevelAdditionalCallNumbers;
  private final boolean discoverySuppress;
  private final List<UUID> statisticalCodeIds;

  public ItemRequestBuilder() {
    this(UUID.randomUUID(), null, null, AVAILABLE_STATUS,
      null, null, null, null, null, null, null, null, new ArrayList<>(), false, new ArrayList<>());
  }

  private ItemRequestBuilder(
    UUID id,
    UUID holdingId,
    String barcode,
    String status,
    UUID temporaryLocationId,
    UUID materialTypeId,
    UUID permanentLoanTypeId,
    UUID temporaryLoanTypeId,
    String itemLevelCallNumberPrefix,
    String itemLevelCallNumber,
    String itemLevelCallNumberSuffix,
    String itemLevelCallNumberTypeId,
    List<EffectiveCallNumberComponents> itemLevelAdditionalCallNumbers,
    boolean discoverySuppress,
    List<UUID> statisticalCodeIds) {

    this.id = id;
    this.holdingId = holdingId;
    this.barcode = barcode;
    this.status = status;
    this.temporaryLocationId = temporaryLocationId;
    this.materialTypeId = materialTypeId;
    this.permanentLoanTypeId = permanentLoanTypeId;
    this.temporaryLoanTypeId = temporaryLoanTypeId;
    this.itemLevelCallNumberPrefix = itemLevelCallNumberPrefix;
    this.itemLevelCallNumber = itemLevelCallNumber;
    this.itemLevelCallNumberSuffix = itemLevelCallNumberSuffix;
    this.itemLevelCallNumberTypeId = itemLevelCallNumberTypeId;
    this.itemLevelAdditionalCallNumbers = itemLevelAdditionalCallNumbers;
    this.discoverySuppress = discoverySuppress;
    this.statisticalCodeIds = statisticalCodeIds;
  }

  public JsonObject create() {
    JsonObject itemRequest = new JsonObject();

    put(itemRequest, "id", id);
    put(itemRequest, "barcode", barcode);
    put(itemRequest, "holdingsRecordId", holdingId);
    put(itemRequest, "materialTypeId", materialTypeId);

    if (status != null) {
      itemRequest.put("status", new JsonObject().put("name", status));
    }

    put(itemRequest, "permanentLoanTypeId", permanentLoanTypeId);
    put(itemRequest, "temporaryLoanTypeId", temporaryLoanTypeId);

    put(itemRequest, "temporaryLocationId", temporaryLocationId);
    put(itemRequest, "itemLevelCallNumberPrefix", itemLevelCallNumberPrefix);
    put(itemRequest, "itemLevelCallNumber", itemLevelCallNumber);
    put(itemRequest, "itemLevelCallNumberSuffix", itemLevelCallNumberSuffix);
    put(itemRequest, "itemLevelCallNumberTypeId", itemLevelCallNumberTypeId);
    put(itemRequest, "itemLevelAdditionalCallNumbers", new JsonArray(itemLevelAdditionalCallNumbers));
    put(itemRequest, "discoverySuppress", discoverySuppress);
    put(itemRequest, "statisticalCodeIds", new JsonArray(statisticalCodeIds));

    return itemRequest;
  }

  public ItemRequestBuilder checkOut() {
    return withStatus(CHECKED_OUT_STATUS);
  }

  public ItemRequestBuilder available() {
    return withStatus(AVAILABLE_STATUS);
  }

  public ItemRequestBuilder withId(UUID id) {
    return new ItemRequestBuilder(
      id,
      this.holdingId,
      this.barcode,
      this.status,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberSuffix,
      this.itemLevelCallNumberTypeId,
      this.itemLevelAdditionalCallNumbers,
      this.discoverySuppress,
      this.statisticalCodeIds);
  }

  public ItemRequestBuilder withStatus(String status) {
    return new ItemRequestBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      status,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberSuffix,
      this.itemLevelCallNumberTypeId,
      this.itemLevelAdditionalCallNumbers,
      false,
      this.statisticalCodeIds);
  }

  public ItemRequestBuilder withBarcode(String barcode) {
    return new ItemRequestBuilder(
      this.id,
      this.holdingId,
      barcode,
      this.status,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberSuffix,
      this.itemLevelCallNumberTypeId,
      this.itemLevelAdditionalCallNumbers,
      this.discoverySuppress,
      this.statisticalCodeIds);
  }

  public ItemRequestBuilder withNoBarcode() {
    return withBarcode(null);
  }

  public ItemRequestBuilder withNoTemporaryLocation() {
    return withTemporaryLocation(null);
  }

  public ItemRequestBuilder withTemporaryLocation(UUID temporaryLocationId) {
    return new ItemRequestBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberSuffix,
      this.itemLevelCallNumberTypeId,
      this.itemLevelAdditionalCallNumbers,
      this.discoverySuppress,
      this.statisticalCodeIds);
  }

  public ItemRequestBuilder forHolding(UUID holdingId) {
    return new ItemRequestBuilder(
      this.id,
      holdingId,
      this.barcode,
      this.status,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberSuffix,
      this.itemLevelCallNumberTypeId,
      this.itemLevelAdditionalCallNumbers,
      this.discoverySuppress,
      this.statisticalCodeIds);
  }

  public ItemRequestBuilder withMaterialType(UUID materialTypeId) {
    return new ItemRequestBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.temporaryLocationId,
      materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberSuffix,
      this.itemLevelCallNumberTypeId,
      this.itemLevelAdditionalCallNumbers,
      this.discoverySuppress,
      this.statisticalCodeIds);
  }

  public ItemRequestBuilder withPermanentLoanTypeId(UUID permanentLoanTypeId) {
    return new ItemRequestBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.temporaryLocationId,
      this.materialTypeId,
      permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberSuffix,
      this.itemLevelCallNumberTypeId,
      this.itemLevelAdditionalCallNumbers,
      this.discoverySuppress,
      this.statisticalCodeIds);
  }

  public ItemRequestBuilder withTemporaryLoanType(UUID loanTypeId) {
    return new ItemRequestBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      loanTypeId,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberSuffix,
      this.itemLevelCallNumberTypeId,
      this.itemLevelAdditionalCallNumbers,
      this.discoverySuppress,
      this.statisticalCodeIds);
  }

  public ItemRequestBuilder withPermanentLoanType(UUID loanTypeId) {
    return new ItemRequestBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.temporaryLocationId,
      this.materialTypeId,
      loanTypeId,
      this.temporaryLoanTypeId,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberSuffix,
      this.itemLevelCallNumberTypeId,
      this.itemLevelAdditionalCallNumbers,
      this.discoverySuppress,
      this.statisticalCodeIds);
  }

  public ItemRequestBuilder withItemLevelCallNumberTypeId(String itemLevelCallNumberTypeId) {
    return new ItemRequestBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberSuffix,
      itemLevelCallNumberTypeId,
      this.itemLevelAdditionalCallNumbers,
      this.discoverySuppress,
      this.statisticalCodeIds);
  }

  public ItemRequestBuilder withItemLevelCallNumberPrefix(String prefix) {
    return new ItemRequestBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      prefix,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberSuffix,
      this.itemLevelCallNumberTypeId,
      this.itemLevelAdditionalCallNumbers,
      this.discoverySuppress,
      this.statisticalCodeIds);
  }

  public ItemRequestBuilder withItemLevelCallNumber(String callNumber) {
    return new ItemRequestBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.itemLevelCallNumberPrefix,
      callNumber,
      this.itemLevelCallNumberSuffix,
      this.itemLevelCallNumberTypeId,
      this.itemLevelAdditionalCallNumbers,
      this.discoverySuppress,
      this.statisticalCodeIds);
  }

  public ItemRequestBuilder withItemLevelCallNumberSuffix(String suffix) {
    return new ItemRequestBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumber,
      suffix,
      this.itemLevelCallNumberTypeId,
      this.itemLevelAdditionalCallNumbers,
      this.discoverySuppress,
      this.statisticalCodeIds);
  }

  public ItemRequestBuilder withItemLevelAdditionalCallNumbers(
    List<EffectiveCallNumberComponents> itemLevelAdditionalCallNumbers) {
    return new ItemRequestBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberSuffix,
      this.itemLevelCallNumberTypeId,
      itemLevelAdditionalCallNumbers,
      this.discoverySuppress,
      this.statisticalCodeIds);

  }

  public ItemRequestBuilder withDiscoverySuppress(boolean discoverySuppress) {
    return new ItemRequestBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberSuffix,
      this.itemLevelCallNumberTypeId,
      this.itemLevelAdditionalCallNumbers,
      discoverySuppress,
      this.statisticalCodeIds);
  }

  public ItemRequestBuilder withStatisticalCodeIds(List<UUID> statisticalCodeIds) {
    return new ItemRequestBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberSuffix,
      this.itemLevelCallNumberTypeId,
      this.itemLevelAdditionalCallNumbers,
      this.discoverySuppress,
      statisticalCodeIds);
  }
}
