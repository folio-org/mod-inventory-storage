package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.resource.InventoryViewInstances;
import org.folio.rest.support.EndpointHandler;
import org.folio.services.instance.InstanceService;

public class InventoryViewApi implements InventoryViewInstances {
  @Validate
  @Override
  public void getInventoryViewInstances(boolean withBoundedItems,
    String totalRecords, int offset, int limit,
    String query, RoutingContext routingContext,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    var instanceService = new InstanceService(vertxContext, okapiHeaders);
    if (withBoundedItems) {
      instanceService
        .getInventoryViewInstancesWithBoundedItems(offset, limit, query)
        .onComplete(EndpointHandler.handle(asyncResultHandler));
    } else {
      instanceService
        .streamGetInventoryViewInstances("instance_holdings_item_view", query,
          offset, limit, null, "instances", 0, routingContext);
    }
  }
}
