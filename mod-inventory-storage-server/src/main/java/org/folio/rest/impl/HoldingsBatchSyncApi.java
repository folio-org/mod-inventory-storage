package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.HoldingsRecordsPostRequest;
import org.folio.rest.jaxrs.resource.HoldingsStorageBatchSynchronous;
import org.folio.services.holding.HoldingsService;

public class HoldingsBatchSyncApi implements HoldingsStorageBatchSynchronous {
  @Validate
  @Override
  public void postHoldingsStorageBatchSynchronous(boolean upsert, HoldingsRecordsPostRequest entity,
                                                  Map<String, String> okapiHeaders,
                                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                                  Context vertxContext) {

    new HoldingsService(vertxContext, okapiHeaders)
      .createHoldings(entity.getHoldingsRecords(), upsert, true)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }
}
