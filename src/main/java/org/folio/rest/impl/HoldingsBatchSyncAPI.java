package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.resource.HoldingsStorageBatchSynchronous.PostHoldingsStorageBatchSynchronousResponse.respond500WithTextPlain;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.HoldingsrecordsPost;
import org.folio.rest.jaxrs.resource.HoldingsStorageBatchSynchronous;
import org.folio.services.holding.HoldingsService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class HoldingsBatchSyncAPI implements HoldingsStorageBatchSynchronous {
  @Validate
  @Override
  public void postHoldingsStorageBatchSynchronous(boolean upsert, HoldingsrecordsPost entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new HoldingsService(vertxContext, okapiHeaders)
      .createHoldings(entity.getHoldingsRecords(), upsert)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(cause -> asyncResultHandler.handle(succeededFuture(
        respond500WithTextPlain(cause.getMessage()))));
  }
}
