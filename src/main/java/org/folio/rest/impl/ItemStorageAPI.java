package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Items;
import org.folio.rest.jaxrs.model.Status;
import org.folio.rest.jaxrs.resource.ItemStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;

/**
 * CRUD for Item.
 */
public class ItemStorageAPI implements ItemStorage {

  static final String ITEM_TABLE = "item";

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
    PgUtil.post(ITEM_TABLE, entity, okapiHeaders, vertxContext,
        PostItemStorageItemsResponse.class, asyncResultHandler);
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

    PgUtil.put(ITEM_TABLE, entity, itemId, okapiHeaders, vertxContext,
        PutItemStorageItemsByItemIdResponse.class, asyncResultHandler);
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
