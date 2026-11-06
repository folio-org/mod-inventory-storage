package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.resource.InventoryViewInstances;
import org.folio.rest.support.ResponseHandlerUtil;
import org.folio.rest.support.StreamingJsonResponse;
import org.folio.services.inventoryview.InventoryViewService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class InventoryViewApi implements InventoryViewInstances {

  @Autowired
  private InventoryViewService inventoryViewService;

  public InventoryViewApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void getInventoryViewInstances(boolean withBoundedItems,
                                        String totalRecords, int offset, int limit,
                                        String query, RoutingContext routingContext,
                                        Map<String, String> headers,
                                        Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    var hrid = inventoryViewService.extractHrid(query);
    if (hrid.isPresent()) {
      inventoryViewService.getInstancesByHrid(hrid.get(), withBoundedItems, offset, limit, headers)
        .map(json -> Response.ok(json, MediaType.APPLICATION_JSON_TYPE).build())
        .onComplete(ResponseHandlerUtil.handle(asyncResultHandler));
    } else if (withBoundedItems) {
      inventoryViewService.getInstancesWithBoundedItems(offset, limit, query, headers)
        .map(json -> Response.ok(json, MediaType.APPLICATION_JSON_TYPE).build())
        .onComplete(ResponseHandlerUtil.handle(asyncResultHandler));
    } else {
      inventoryViewService.streamInstances(query, offset, limit, headers)
        .onSuccess(result -> StreamingJsonResponse.write(result, "instances", routingContext.response()))
        .onFailure(e -> StreamingJsonResponse.writeFailure(e, routingContext.response()));
    }
  }
}
