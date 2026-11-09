package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.jaxrs.resource.ItemStorageDereferenced;
import org.folio.services.dereferenceditem.DereferencedItemService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * CRUD for Dereferenced Items.
 * This is an experimental endpoint.
 * Aim is to determine if dereferencing item records
 * results in performance improvement when performing
 * checkout/checkin operations.
 */
@SuppressWarnings("java:S6813")
public class ItemStorageDereferencedApi implements ItemStorageDereferenced {

  @Autowired
  private DereferencedItemService dereferencedItemService;

  public ItemStorageDereferencedApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void getItemStorageDereferencedItems(String totalRecords, int offset, int limit, String query,
                                              Map<String, String> headers,
                                              Handler<AsyncResult<Response>> asyncResultHandler,
                                              Context vertxContext) {
    dereferencedItemService.find(query, offset, limit, headers)
      .<Response>map(GetItemStorageDereferencedItemsResponse::respond200WithApplicationJson)
      .otherwise(this::failureResponse)
      .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void getItemStorageDereferencedItemsByItemId(String itemId, Map<String, String> headers,
                                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                                      Context vertxContext) {
    dereferencedItemService.getById(itemId, headers)
      .<Response>map(GetItemStorageDereferencedItemsByItemIdResponse::respond200WithApplicationJson)
      .otherwise(this::failureResponse)
      .onComplete(asyncResultHandler);
  }

  private Response failureResponse(Throwable failure) {
    if (failure instanceof BadRequestException) {
      return GetItemStorageDereferencedItemsResponse.respond400WithTextPlain(failure.getMessage());
    }
    if (failure instanceof NotFoundException) {
      return GetItemStorageDereferencedItemsResponse.respond404WithTextPlain(failure.getMessage());
    }
    return GetItemStorageDereferencedItemsResponse.respond500WithTextPlain(
      "Can't retrieve item records: " + failure.getMessage());
  }
}
