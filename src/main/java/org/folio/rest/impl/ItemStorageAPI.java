package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.ObjectUtils.firstNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNoneBlank;
import static org.folio.rest.jaxrs.resource.ItemStorage.PutItemStorageItemsByItemIdResponse.respond400WithTextPlain;
import static org.folio.rest.jaxrs.resource.ItemStorage.PutItemStorageItemsByItemIdResponse.respond404WithTextPlain;
import static org.folio.rest.jaxrs.resource.ItemStorage.PutItemStorageItemsByItemIdResponse.respond500WithTextPlain;
import static org.folio.rest.support.ResponseUtil.copyResponseWithNewEntity;
import static org.folio.rest.support.ResponseUtil.hasCreatedStatus;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.resource.ItemStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.EffectiveCallNumberComponentsUtil;
import org.folio.rest.support.HridManager;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

/**
 * CRUD for Item.
 */
public class ItemStorageAPI implements ItemStorage {

  static final String ITEM_TABLE = "item";
  static final String HOLDINGS_RECORD_TABLE = "holdings_record";

  private static final Logger log = LoggerFactory.getLogger(ItemStorageAPI.class);

  @Validate
  @Override
  public void getItemStorageItems(
    int offset, int limit, String query, String lang,
    RoutingContext routingContext, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.streamGet(ITEM_TABLE, Item.class, query, offset, limit, null, "items",
      routingContext, okapiHeaders, vertxContext);
  }

  @Validate
  @Override
  public void postItemStorageItems(
      String lang, Item entity,
      RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    final Future<String> hridFuture;
    if (isBlank(entity.getHrid())) {
      final HridManager hridManager = new HridManager(vertxContext,
          StorageHelper.postgresClient(vertxContext, okapiHeaders));
      hridFuture = hridManager.getNextItemHrid();
    } else {
      hridFuture = StorageHelper.completeFuture(entity.getHrid());
    }

    hridFuture.map(hrid -> {
      entity.setHrid(hrid);
      return setEffectiveCallNumber(okapiHeaders, vertxContext, entity).thenAccept(i ->
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
            }));
    })
    .otherwise(error -> {
      log.error(error.getMessage(), error);
      asyncResultHandler.handle(Future.succeededFuture(
          PostItemStorageItemsResponse.respond500WithTextPlain(error.getMessage())));
      return null;
    });
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
  public void deleteItemStorageItems(String lang,
    RoutingContext routingContext, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    String tenantId = TenantTool.tenantId(okapiHeaders);
    PostgresClient postgresClient = StorageHelper.postgresClient(vertxContext, okapiHeaders);

    postgresClient.execute(String.format("DELETE FROM %s_%s.item", tenantId, "mod_inventory_storage"),
        reply -> {
          if (reply.succeeded()) {
            asyncResultHandler.handle(Future.succeededFuture(
                DeleteItemStorageItemsResponse.respond204()));
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
    PgUtil.getById(ITEM_TABLE, Item.class, itemId, okapiHeaders, vertxContext, GetItemStorageItemsByItemIdResponse.class, response -> {
      if (response.succeeded()) {
        if (response.result().getStatus() == 404) {
          asyncResultHandler.handle(succeededFuture(
              respond404WithTextPlain(response.result().getEntity())));
        } else if (response.result().getStatus() == 500) {
          asyncResultHandler.handle(succeededFuture(
              respond500WithTextPlain(response.result().getEntity())));
        } else {
          final Item existingItem = (Item) response.result().getEntity();
          if (Objects.equals(entity.getHrid(), existingItem.getHrid())) {
            setEffectiveCallNumber(okapiHeaders, vertxContext, entity).thenAccept(
                item -> PgUtil.put(ITEM_TABLE, item, itemId, okapiHeaders, vertxContext,
                  PutItemStorageItemsByItemIdResponse.class, asyncResultHandler));
          } else {
            asyncResultHandler.handle(succeededFuture(
                respond400WithTextPlain(
                    "The hrid field cannot be changed: new="
                        + entity.getHrid()
                        + ", old="
                        + existingItem.getHrid())));

          }
        }
      } else {
        asyncResultHandler.handle(succeededFuture(
            respond500WithTextPlain(response.cause().getMessage())));
      }
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
    if (shouldNotRetrieveHoldingsRecord(item)) {
      item.setEffectiveCallNumberComponents(EffectiveCallNumberComponentsUtil.buildComponents(null, item));
      completableFuture = CompletableFuture.completedFuture(item);
    } else {
      completableFuture = getHoldingsRecordById(okapiHeaders, vertxContext, item.getHoldingsRecordId()).thenApplyAsync(hr ->
      {
        item.setEffectiveCallNumberComponents(EffectiveCallNumberComponentsUtil.buildComponents(hr, item));
        return item;
      });
    }

    return completableFuture;
  }

  private CompletableFuture<HoldingsRecord> getHoldingsRecordById(Map<String, String> okapiHeaders, Context vertxContext, String holdingsRecordId) {
    final CompletableFuture<HoldingsRecord> readHoldingsRecordFuture = new CompletableFuture<>();
    PgUtil.postgresClient(vertxContext, okapiHeaders)
    .getById(HOLDINGS_RECORD_TABLE, holdingsRecordId, HoldingsRecord.class,
      response -> readHoldingsRecordFuture.complete(response.result()));
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

  private boolean shouldNotRetrieveHoldingsRecord(Item item) {
    return isNoneBlank(item.getItemLevelCallNumber(),
      item.getItemLevelCallNumberPrefix(),
      item.getItemLevelCallNumberSuffix(),
      item.getItemLevelCallNumberTypeId()
    );
  }
}
