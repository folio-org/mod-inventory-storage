package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.HoldingsRecordView;
import org.folio.rest.jaxrs.resource.HoldingsStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.support.EndpointFailureHandler;
import org.folio.services.holding.HoldingsService;

public class HoldingsStorageApi implements HoldingsStorage {

  public static final String HOLDINGS_RECORD_TABLE = "holdings_record";

  @Validate
  @Override
  public void getHoldingsStorageHoldings(String totalRecords, int offset, int limit, String query,
                                         RoutingContext routingContext, Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                         Context vertxContext) {

    PgUtil.streamGet(HOLDINGS_RECORD_TABLE, HoldingsRecordView.class, query, offset,
      limit, null, "holdingsRecords", routingContext, okapiHeaders, vertxContext);
  }

  @Validate
  @Override
  public void postHoldingsStorageHoldings(
                                          HoldingsRecord entity,
                                          RoutingContext routingContext, Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler,
                                          Context vertxContext) {

    new HoldingsService(vertxContext, okapiHeaders)
      .createHolding(entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteHoldingsStorageHoldings(String query,
                                            RoutingContext routingContext, Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {

    new HoldingsService(vertxContext, okapiHeaders).deleteHoldings(query)
      .otherwise(EndpointFailureHandler::failureResponse)
      .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void getHoldingsStorageHoldingsByHoldingsRecordId(
    String holdingsRecordId,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.getById(HOLDINGS_RECORD_TABLE, HoldingsRecord.class, holdingsRecordId,
      okapiHeaders, vertxContext, GetHoldingsStorageHoldingsByHoldingsRecordIdResponse.class,
      asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteHoldingsStorageHoldingsByHoldingsRecordId(
    String holdingsRecordId,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new HoldingsService(vertxContext, okapiHeaders).deleteHolding(holdingsRecordId)
      .otherwise(EndpointFailureHandler::failureResponse)
      .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void putHoldingsStorageHoldingsByHoldingsRecordId(
    String holdingsRecordId,
    HoldingsRecord entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new HoldingsService(vertxContext, okapiHeaders)
      .updateHoldingRecord(holdingsRecordId, entity)
      .onSuccess(notUsed -> asyncResultHandler.handle(Future.succeededFuture(
        PutHoldingsStorageHoldingsByHoldingsRecordIdResponse.respond204())))
      .onFailure(handleFailure(asyncResultHandler));
  }
}
