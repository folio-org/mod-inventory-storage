package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.ItemsPatch;
import org.folio.rest.jaxrs.model.RetrieveDto;
import org.folio.rest.jaxrs.resource.ItemStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.support.EndpointFailureHandler;
import org.folio.services.item.ItemService;

/**
 * CRUD for Item.
 */
public class ItemStorageApi implements ItemStorage {
  public static final String ITEM_TABLE = "item";

  @Validate
  @Override
  public void getItemStorageItems(String totalRecords, int offset, int limit, String query,
                                  RoutingContext routingContext, Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                  Context vertxContext) {

    PgUtil.streamGet(ITEM_TABLE, Item.class, query, offset, limit, null, "items",
      routingContext, okapiHeaders, vertxContext);
  }

  @Validate
  @Override
  public void postItemStorageItemsRetrieve(RetrieveDto entity, RoutingContext routingContext,
                                           Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    String query = entity.getQuery();
    Integer offset = entity.getOffset();
    Integer limit = entity.getLimit();

    PgUtil.streamGet(ITEM_TABLE, Item.class, query, offset, limit, null, "items",
            routingContext, okapiHeaders, vertxContext);
  }

  @Validate
  @Override
  public void postItemStorageItems(
    Item entity,
    RoutingContext routingContext, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new ItemService(vertxContext, okapiHeaders).createItem(entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void patchItemStorageItems(ItemsPatch entity, RoutingContext routingContext, Map<String, String> okapiHeaders,
                                    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new ItemService(vertxContext, okapiHeaders).updateItems(entity.getItems())
      .onFailure(handleFailure(asyncResultHandler))
      .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteItemStorageItems(String query,
                                     RoutingContext routingContext, Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new ItemService(vertxContext, okapiHeaders).deleteItems(query)
      .otherwise(EndpointFailureHandler::failureResponse)
      .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void getItemStorageItemsByItemId(
    String itemId, java.util.Map<String, String> okapiHeaders,
    io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.getById(ITEM_TABLE, Item.class, itemId, okapiHeaders, vertxContext,
      GetItemStorageItemsByItemIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteItemStorageItemsByItemId(
    String itemId, java.util.Map<String, String> okapiHeaders,
    io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new ItemService(vertxContext, okapiHeaders).deleteItem(itemId)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void putItemStorageItemsByItemId(
    String itemId, Item entity, java.util.Map<String, String> okapiHeaders,
    io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new ItemService(vertxContext, okapiHeaders).updateItem(itemId, entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }
}
