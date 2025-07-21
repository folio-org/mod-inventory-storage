package org.folio.rest.impl;

import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_NOT_FOUND;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.rest.impl.ItemDamagedStatusApi.ITEM_DAMAGED_STATUS_TABLE;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.ItemDamageStatus;
import org.folio.rest.jaxrs.model.ItemDamageStatuses;
import org.folio.rest.jaxrs.model.Metadata;
import org.junit.jupiter.api.Test;

class ItemDamagedStatusIT extends BaseReferenceDataIntegrationTest<ItemDamageStatus, ItemDamageStatuses> {

  @Override
  protected String referenceTable() {
    return ITEM_DAMAGED_STATUS_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/item-damaged-statuses";
  }

  @Override
  protected Class<ItemDamageStatus> targetClass() {
    return ItemDamageStatus.class;
  }

  @Override
  protected Class<ItemDamageStatuses> collectionClass() {
    return ItemDamageStatuses.class;
  }

  @Override
  protected ItemDamageStatus sampleRecord() {
    return new ItemDamageStatus()
      .withName("Sample-Item-Damaged-Status")
      .withSource("Sample-Source");
  }

  @Override
  protected Function<ItemDamageStatuses, List<ItemDamageStatus>> collectionRecordsExtractor() {
    return ItemDamageStatuses::getItemDamageStatuses;
  }

  @Override
  protected List<Function<ItemDamageStatus, Object>> recordFieldExtractors() {
    return List.of(
      ItemDamageStatus::getName,
      ItemDamageStatus::getSource
    );
  }

  @Override
  protected Function<ItemDamageStatus, String> idExtractor() {
    return ItemDamageStatus::getId;
  }

  @Override
  protected Function<ItemDamageStatus, Metadata> metadataExtractor() {
    return ItemDamageStatus::getMetadata;
  }

  @Override
  protected UnaryOperator<ItemDamageStatus> recordModifyingFunction() {
    return itemDamageStatus -> itemDamageStatus.withName(itemDamageStatus.getName() + " - modified");
  }

  @Override
  protected List<String> queries() {
    return List.of(
      "name==Sample-Item-Damaged-Status",
      "source==Sample-Source"
    );
  }

  @Test
  void cannotCreateItemDamagedStatusWithAdditionalProperties(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();

    var itemDamageStatus = new JsonObject()
      .put("name", "Damaged")
      .put("additional", "foo");

    doPost(client, resourceUrl(), itemDamageStatus)
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotCreateItemDamagedStatusWithSameName(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();

    var itemDamageStatus = new JsonObject()
      .put("name", "Damaged")
      .put("source", "local");

    doPost(client, resourceUrl(), itemDamageStatus)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .compose(response -> doPost(client, resourceUrl(), itemDamageStatus))
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotCreateItemDamagedStatusWithSameId(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();

    var itemDamageStatus = new JsonObject()
      .put("name", "Damaged")
      .put("source", "local");

    doPost(client, resourceUrl(), itemDamageStatus)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .compose(response -> {
        var id = response.jsonBody().getString("id");
        var another = new JsonObject()
          .put("name", "Lost")
          .put("source", "local")
          .put("id", id);

        return doPost(client, resourceUrl(), another);
      })
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotDeleteItemDamagedStatusThatDoesNotExist(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();

    doDelete(client, resourceUrlById(UUID.randomUUID().toString()))
      .onComplete(verifyStatus(ctx, HTTP_NOT_FOUND))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotUpdateItemDamagedStatusThatDoesNotExist(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();

    var itemDamageStatus = new JsonObject()
      .put("name", "Damaged")
      .put("source", "local");

    doPut(client, resourceUrlById(UUID.randomUUID().toString()), itemDamageStatus)
      .onComplete(verifyStatus(ctx, HTTP_NOT_FOUND))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }
}
