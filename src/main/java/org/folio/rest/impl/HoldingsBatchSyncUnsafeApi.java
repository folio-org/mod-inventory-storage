package org.folio.rest.impl;

import static org.folio.rest.jaxrs.resource.HoldingsStorageBatchSynchronousUnsafe.PostHoldingsStorageBatchSynchronousUnsafeResponse.respond500WithTextPlain;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.HoldingsrecordsPost;
import org.folio.rest.jaxrs.resource.HoldingsStorageBatchSynchronousUnsafe;
import org.folio.services.holding.HoldingsService;

public class HoldingsBatchSyncUnsafeApi implements HoldingsStorageBatchSynchronousUnsafe {
  @Validate
  @Override
  public void postHoldingsStorageBatchSynchronousUnsafe(HoldingsrecordsPost entity,
                                                        RoutingContext routingContext,
                                                        Map<String, String> okapiHeaders,
                                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                                        Context vertxContext) {

    new HoldingsService(vertxContext, okapiHeaders)
      .createHoldings(entity.getHoldingsRecords(), true, false, routingContext)
      .otherwise(cause -> respond500WithTextPlain(cause.getMessage()))
      .onComplete(asyncResultHandler);
  }
}
