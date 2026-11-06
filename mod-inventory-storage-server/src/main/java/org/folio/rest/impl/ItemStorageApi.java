package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.ResponseHandlerUtil.firstErrorMessage;
import static org.folio.rest.support.ResponseHandlerUtil.handleFailure;
import static org.folio.rest.support.ResponseHandlerUtil.handleFailureWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.ItemsPatchRequest;
import org.folio.rest.jaxrs.model.RetrieveEntitiesRequest;
import org.folio.rest.jaxrs.resource.ItemStorage;
import org.folio.rest.support.ResponseHandlerUtil;
import org.folio.rest.support.StreamingJsonResponse;
import org.folio.services.item.ItemService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class ItemStorageApi implements ItemStorage {

  @Autowired
  private ItemService itemService;

  public ItemStorageApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void getItemStorageItems(String totalRecords, int offset, int limit, String query,
                                  RoutingContext routingContext, Map<String, String> headers,
                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                  Context vertxContext) {

    itemService.streamItems(query, offset, limit, headers)
      .onSuccess(result -> StreamingJsonResponse.write(result, "items", routingContext.response()))
      .onFailure(e -> StreamingJsonResponse.writeFailure(e, routingContext.response()));
  }

  @Validate
  @Override
  public void postItemStorageItems(Item entity, RoutingContext routingContext, Map<String, String> headers,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    itemService.createItem(entity, headers)
      .map(created -> PostItemStorageItemsResponse.respond201WithApplicationJson(created,
        PostItemStorageItemsResponse.headersFor201().withLocation(created.getId())))
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailureWith(asyncResultHandler, ResponseHandlerUtil::handleHridError));
  }

  @Override
  public void patchItemStorageItems(ItemsPatchRequest entity, RoutingContext routingContext,
                                    Map<String, String> headers,
                                    Handler<AsyncResult<Response>> asyncResultHandler,
                                    Context vertxContext) {
    itemService.updateItems(entity.getItems(), headers)
      .map(notUsed -> PatchItemStorageItemsResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteItemStorageItems(String query, RoutingContext routingContext, Map<String, String> headers,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    itemService.deleteItems(query, headers)
      .map(notUsed -> DeleteItemStorageItemsResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getItemStorageItemsByItemId(String itemId, Map<String, String> headers,
                                          Handler<AsyncResult<Response>> asyncResultHandler,
                                          Context vertxContext) {

    itemService.getItem(itemId, headers)
      .map(GetItemStorageItemsByItemIdResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        GetItemStorageItemsByItemIdResponse.respond404WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void deleteItemStorageItemsByItemId(String itemId, Map<String, String> headers,
                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                             Context vertxContext) {

    itemService.deleteItem(itemId, headers)
      .map(notUsed -> DeleteItemStorageItemsByItemIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        DeleteItemStorageItemsByItemIdResponse.respond400WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void putItemStorageItemsByItemId(String itemId, Item entity, Map<String, String> headers,
                                          Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    itemService.updateItem(itemId, entity, headers)
      .map(notUsed -> PutItemStorageItemsByItemIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postItemStorageItemsRetrieve(RetrieveEntitiesRequest entity, RoutingContext routingContext,
                                           Map<String, String> headers,
                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                           Context vertxContext) {
    String query = entity.getQuery();
    Integer offset = entity.getOffset();
    Integer limit = entity.getLimit();

    itemService.streamItems(query, offset, limit, headers)
      .onSuccess(result -> StreamingJsonResponse.write(result, "items", routingContext.response()))
      .onFailure(e -> StreamingJsonResponse.writeFailure(e, routingContext.response()));
  }
}
