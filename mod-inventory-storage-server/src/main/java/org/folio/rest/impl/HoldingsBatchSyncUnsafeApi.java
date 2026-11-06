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
import org.folio.rest.jaxrs.resource.HoldingsStorageBatchSynchronousUnsafe;
import org.folio.rest.support.ResponseHandlerUtil;
import org.folio.services.holding.HoldingsService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class HoldingsBatchSyncUnsafeApi implements HoldingsStorageBatchSynchronousUnsafe {

  @Autowired
  private HoldingsService holdingsService;

  public HoldingsBatchSyncUnsafeApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void postHoldingsStorageBatchSynchronousUnsafe(HoldingsRecordsPostRequest entity,
                                                        Map<String, String> headers,
                                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                                        Context vertxContext) {

    holdingsService.createHoldings(entity.getHoldingsRecords(), true, false, headers)
      .map(notUsed -> PostHoldingsStorageBatchSynchronousUnsafeResponse.respond201())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailureWith(asyncResultHandler, ResponseHandlerUtil::handleHridError));
  }
}
