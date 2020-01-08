package org.folio.rest.support.builders;

import java.util.UUID;

import io.vertx.core.json.JsonObject;

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
  private final String itemLevelCallNumberTypeId;

  public ItemRequestBuilder() {
    this(UUID.randomUUID(), null, "565578437802", AVAILABLE_STATUS,
      null, null, null, null, null);
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
    String itemLevelCallNumberTypeId) {

    this.id = id;
    this.holdingId = holdingId;
    this.barcode = barcode;
    this.status = status;
    this.temporaryLocationId = temporaryLocationId;
    this.materialTypeId = materialTypeId;
    this.permanentLoanTypeId = permanentLoanTypeId;
    this.temporaryLoanTypeId = temporaryLoanTypeId;
    this.itemLevelCallNumberTypeId = itemLevelCallNumberTypeId;
  }

  public JsonObject create() {
    JsonObject itemRequest = new JsonObject();

    put(itemRequest, "id", id);
    put(itemRequest, "barcode", barcode);
    put(itemRequest, "holdingsRecordId", holdingId);
    put(itemRequest, "materialTypeId", materialTypeId);

    if(status != null) {
      itemRequest.put("status", new JsonObject().put("name", status));
    }

    put(itemRequest, "permanentLoanTypeId", permanentLoanTypeId);
    put(itemRequest, "temporaryLoanTypeId", temporaryLoanTypeId);

    put(itemRequest, "temporaryLocationId", temporaryLocationId);
    put(itemRequest, "itemLevelCallNumberTypeId", itemLevelCallNumberTypeId);

    return itemRequest;
  }

  public ItemRequestBuilder checkOut() {
    return withStatus(CHECKED_OUT_STATUS);
  }

  public ItemRequestBuilder available() {
    return withStatus(AVAILABLE_STATUS);
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
      this.itemLevelCallNumberTypeId);
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
      this.itemLevelCallNumberTypeId);
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
      this.itemLevelCallNumberTypeId);
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
      this.itemLevelCallNumberTypeId);
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
      this.itemLevelCallNumberTypeId);
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
      this.itemLevelCallNumberTypeId);
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
      this.itemLevelCallNumberTypeId);
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
      itemLevelCallNumberTypeId);
  }
}
