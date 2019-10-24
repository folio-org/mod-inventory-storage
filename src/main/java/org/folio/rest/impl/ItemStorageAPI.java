package org.folio.rest.impl;

import static org.apache.commons.lang3.ObjectUtils.firstNonNull;
import static org.folio.rest.support.ResponseUtil.copyResponseWithNewEntity;
import static org.folio.rest.support.ResponseUtil.hasCreatedStatus;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Items;
import org.folio.rest.jaxrs.model.Status;
import org.folio.rest.jaxrs.resource.ItemStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * CRUD for Item.
 */
public class ItemStorageAPI implements ItemStorage {

  static final String ITEM_TABLE = "item";
  static final String HOLDINGS_RECORD_TABLE = "holdings_record";

  private static final Logger log = LoggerFactory.getLogger(ItemStorageAPI.class);
  private static final String DEFAULT_STATUS_NAME = "Available";

  @Validate
  @Override
  public void getItemStorageItems(
    int offset,
    int limit,
    String query,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.get(ITEM_TABLE, Item.class, Items.class, query, offset, limit,
      okapiHeaders, vertxContext, GetItemStorageItemsResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void postItemStorageItems(
      String lang, Item entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    if (entity.getStatus() == null) {
      entity.setStatus(new Status().withName(DEFAULT_STATUS_NAME));
    }

    setEffectiveCallNumber(okapiHeaders, vertxContext, entity).thenAccept(i ->
      {
        PgUtil.post(ITEM_TABLE, i, okapiHeaders, vertxContext,
          PostItemStorageItemsResponse.class, postResponse -> {
            // Have to re-read item to get calculated fields like effectiveLocationId
            if (hasCreatedStatus(postResponse.result())) {
              readItemById(i.getId(), okapiHeaders, vertxContext)
                // copy original response to save all headers etc. and set
                // the retrieved item or set the original entity in case item is null
                .thenApply(item -> copyResponseWithNewEntity(postResponse.result(), firstNonNull(item, entity)))
                .thenAccept(respToSend -> asyncResultHandler.handle(Future.succeededFuture(respToSend)));
            } else {
              asyncResultHandler.handle(postResponse);
            }
          });
      }
    );
  }

  @Validate
  @Override
  public void getItemStorageItemsByItemId(
      String itemId, String lang, java.util.Map<String, String> okapiHeaders,
      io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.getById(ITEM_TABLE, Item.class, itemId, okapiHeaders, vertxContext,
        GetItemStorageItemsByItemIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteItemStorageItems(
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    String tenantId = TenantTool.tenantId(okapiHeaders);
    PostgresClient postgresClient = StorageHelper.postgresClient(vertxContext, okapiHeaders);

    postgresClient.execute(String.format("DELETE FROM %s_%s.item", tenantId, "mod_inventory_storage"),
        reply -> {
          if (reply.succeeded()) {
            asyncResultHandler.handle(Future.succeededFuture(
                DeleteItemStorageItemsResponse.noContent()
                .build()));
          } else {
            log.error(reply.cause().getMessage(), reply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
                DeleteItemStorageItemsResponse.
                respond500WithTextPlain(reply.cause().getMessage())));
          }
        });
  }

  @Validate
  @Override
  public void putItemStorageItemsByItemId(
      String itemId, String lang, Item entity, java.util.Map<String, String> okapiHeaders,
      io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    setEffectiveCallNumber(okapiHeaders, vertxContext, entity).thenAccept(i -> {
      PgUtil.put(ITEM_TABLE, i, itemId, okapiHeaders, vertxContext,
        PutItemStorageItemsByItemIdResponse.class, asyncResultHandler);
    });
  }

  @Validate
  @Override
  public void deleteItemStorageItemsByItemId(
      String itemId, String lang, java.util.Map<String, String> okapiHeaders,
      io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.deleteById(ITEM_TABLE, itemId, okapiHeaders, vertxContext,
        DeleteItemStorageItemsByItemIdResponse.class, asyncResultHandler);
  }

  private CompletableFuture<Item> setEffectiveCallNumber(Map<String, String> okapiHeaders, Context vertxContext, Item item) {
    CompletableFuture<Item> completableFuture = null;
    if (item.getItemLevelCallNumber() != null && !item.getItemLevelCallNumber().isEmpty()) {
      item.setEffectiveCallNumber(item.getItemLevelCallNumber());
      completableFuture = CompletableFuture.supplyAsync(() -> item);
    } else {
      if (item.getHoldingsRecordId() != null && !item.getHoldingsRecordId().isEmpty()) {
        completableFuture = getHoldingsRecordById(okapiHeaders, vertxContext, item.getHoldingsRecordId()).thenCombineAsync(CompletableFuture.supplyAsync(() -> item), (hr, i) ->
        {
          i.setEffectiveCallNumber(hr.getCallNumber());
          return i;
        });
      }
    }

    return completableFuture;
  }

  private CompletableFuture<HoldingsRecord> getHoldingsRecordById(Map<String, String> okapiHeaders, Context vertxContext, String holdingsRecordId) {
    final CompletableFuture<HoldingsRecord> readHoldingsRecordFuture = new CompletableFuture<>();
    PgUtil.postgresClient(vertxContext, okapiHeaders)
    .getById(HOLDINGS_RECORD_TABLE, holdingsRecordId, HoldingsRecord.class,
      response -> {
        readHoldingsRecordFuture.complete(response.result());
    });
    return readHoldingsRecordFuture;
  }

  private CompletableFuture<Item> readItemById(
    String itemId, Map<String, String> okapiHeaders, Context vertxContext) {
    final CompletableFuture<Item> readItemFuture = new CompletableFuture<>();

    PgUtil.postgresClient(vertxContext, okapiHeaders)
      .getById(ITEM_TABLE, itemId, Item.class,
        response -> readItemFuture.complete(response.result())
      );

    return readItemFuture;
  }
}
