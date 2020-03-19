package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.rest.jaxrs.resource.ItemStorage.PutItemStorageItemsByItemIdResponse.respond400WithTextPlain;
import static org.folio.rest.jaxrs.resource.ItemStorage.PutItemStorageItemsByItemIdResponse.respond404WithTextPlain;
import static org.folio.rest.jaxrs.resource.ItemStorage.PutItemStorageItemsByItemIdResponse.respond500WithTextPlain;

import java.util.Map;
import java.util.Objects;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.resource.ItemStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.EndpointFailureHandler;
import org.folio.rest.support.HridManager;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.ItemEffectiveCallNumberComponentsService;

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

    final ItemEffectiveCallNumberComponentsService effectiveCallNumbersService =
      new ItemEffectiveCallNumberComponentsService(vertxContext, okapiHeaders);

    hridFuture.map(entity::withHrid)
      .compose(effectiveCallNumbersService::populateEffectiveCallNumberComponents)
      .map(item -> {
        PgUtil.post(ITEM_TABLE, item, okapiHeaders, vertxContext,
          PostItemStorageItemsResponse.class, asyncResultHandler);
        return item;
      }).otherwise(EndpointFailureHandler.handleFailure(asyncResultHandler,
      PostItemStorageItemsResponse::respond422WithApplicationJson,
      PostItemStorageItemsResponse::respond500WithTextPlain
    ));
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

    final ItemEffectiveCallNumberComponentsService effectiveCallNumbersService =
      new ItemEffectiveCallNumberComponentsService(vertxContext, okapiHeaders);

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
            effectiveCallNumbersService.populateEffectiveCallNumberComponents(entity)
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
}
