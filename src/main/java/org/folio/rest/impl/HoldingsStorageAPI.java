package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.resource.HoldingsStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.services.holding.HoldingsService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 *
 * @author ne
 */
public class HoldingsStorageAPI implements HoldingsStorage {

  private static final Logger log = LogManager.getLogger();

  // Has to be lowercase because raml-module-builder uses case sensitive
  // lower case headers
  private static final String TENANT_HEADER = "x-okapi-tenant";
  public static final String HOLDINGS_RECORD_TABLE = "holdings_record";

  @Validate
  @Override
  public void deleteHoldingsStorageHoldings(String lang,
    RoutingContext routingContext, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new HoldingsService(vertxContext, okapiHeaders)
      .deleteAllHoldings()
      .onSuccess(notUsed -> asyncResultHandler.handle(succeededFuture(
        DeleteHoldingsStorageHoldingsResponse.respond204())))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getHoldingsStorageHoldings(
    int offset, int limit, String query, String lang,
    RoutingContext routingContext, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.streamGet(HOLDINGS_RECORD_TABLE, HoldingsRecord.class, query, offset,
      limit, null, "holdingsRecords", routingContext, okapiHeaders, vertxContext);
  }

  @Validate
  @Override
  public void postHoldingsStorageHoldings(String lang,
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
  public void getHoldingsStorageHoldingsByHoldingsRecordId(
      String holdingsRecordId, String lang,
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
    String holdingsRecordId, String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new HoldingsService(vertxContext, okapiHeaders)
      .deleteHolding(holdingsRecordId)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void putHoldingsStorageHoldingsByHoldingsRecordId(
    String holdingsRecordId, String lang,
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
