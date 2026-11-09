package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.ResponseHandlerUtil.handleFailureWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.HoldingsRecordsPostRequest;
import org.folio.rest.jaxrs.resource.HoldingsStorageBatchSynchronous;
import org.folio.rest.support.ResponseHandlerUtil;
import org.folio.services.holding.HoldingsService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class HoldingsBatchSyncApi implements HoldingsStorageBatchSynchronous {

  @Autowired
  private HoldingsService holdingsService;

  public HoldingsBatchSyncApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void postHoldingsStorageBatchSynchronous(boolean upsert, HoldingsRecordsPostRequest entity,
                                                  Map<String, String> headers,
                                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                                  Context vertxContext) {

    holdingsService.createHoldings(entity.getHoldingsRecords(), upsert, true, headers)
      .map(notUsed -> PostHoldingsStorageBatchSynchronousResponse.respond201())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailureWith(asyncResultHandler, ResponseHandlerUtil::handleHridError));
  }
}
