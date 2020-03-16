package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNoneBlank;
import static org.folio.rest.jaxrs.resource.ItemStorage.PutItemStorageItemsByItemIdResponse.respond400WithTextPlain;
import static org.folio.rest.jaxrs.resource.ItemStorage.PutItemStorageItemsByItemIdResponse.respond404WithTextPlain;
import static org.folio.rest.jaxrs.resource.ItemStorage.PutItemStorageItemsByItemIdResponse.respond500WithTextPlain;
import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;

import java.util.Map;
import java.util.Objects;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;
import org.folio.rest.jaxrs.model.Errors;
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
import io.vertx.core.Promise;
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

    hridFuture.map(entity::withHrid)
      .compose(item -> getEffectiveCallNumberComponents(okapiHeaders, vertxContext, item))
      .map(entity::withEffectiveCallNumberComponents)
      .map(item -> {
        PgUtil.post(ITEM_TABLE, item, okapiHeaders, vertxContext,
          PostItemStorageItemsResponse.class, asyncResultHandler);
        return item;
      }).otherwise(error -> {
      log.error(error.getMessage(), error);

      if (error instanceof NotFoundException) {
        handleHoldingsRecordNotFoundException(entity, asyncResultHandler);
      } else {
        asyncResultHandler.handle(Future.succeededFuture(
          PostItemStorageItemsResponse.respond500WithTextPlain(error.getMessage())));
      }
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
            getEffectiveCallNumberComponents(okapiHeaders, vertxContext, entity)
              .map(entity::withEffectiveCallNumberComponents)
              .map(item -> {
                PgUtil.put(ITEM_TABLE, item, itemId, okapiHeaders, vertxContext,
                  PutItemStorageItemsByItemIdResponse.class, asyncResultHandler);
                return item;
              });
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

  private Future<EffectiveCallNumberComponents> getEffectiveCallNumberComponents(
    Map<String, String> okapiHeaders, Context vertxContext, Item item) {

    Promise<EffectiveCallNumberComponents> promise = Promise.promise();
    if (shouldNotRetrieveHoldingsRecord(item)) {
      promise.complete(EffectiveCallNumberComponentsUtil.buildComponents(null, item));
    } else {
      getHoldingsRecordById(okapiHeaders, vertxContext, item.getHoldingsRecordId())
        .setHandler(result -> {
          if (result.failed()) {
            promise.fail(result.cause());
            return;
          }

          if (result.result() == null) {
            promise.fail(new NotFoundException("Holdings record does not exist"));
            return;
          }

          promise.complete(
            EffectiveCallNumberComponentsUtil.buildComponents(result.result(), item));
        });
    }

    return promise.future();
  }

  private Future<HoldingsRecord> getHoldingsRecordById(
    Map<String, String> okapiHeaders, Context vertxContext, String holdingsRecordId) {

    final Promise<HoldingsRecord> readHoldingsRecordFuture = Promise.promise();

    PgUtil.postgresClient(vertxContext, okapiHeaders)
    .getById(HOLDINGS_RECORD_TABLE, holdingsRecordId, HoldingsRecord.class,
      readHoldingsRecordFuture);

    return readHoldingsRecordFuture.future();
  }

  private boolean shouldNotRetrieveHoldingsRecord(Item item) {
    return isNoneBlank(item.getItemLevelCallNumber(),
      item.getItemLevelCallNumberPrefix(),
      item.getItemLevelCallNumberSuffix(),
      item.getItemLevelCallNumberTypeId()
    );
  }

  private void handleHoldingsRecordNotFoundException(Item entity,
    Handler<AsyncResult<Response>> asyncResultHandler) {

    final Errors errors = createValidationErrorMessage("holdingsRecordId",
      entity.getHoldingsRecordId(), "Holdings record does not exist");

    asyncResultHandler.handle(Future.succeededFuture(
      PostItemStorageItemsResponse.respond422WithApplicationJson(errors)));
  }
}
