package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.ResponseHandlerUtil.firstErrorMessage;
import static org.folio.rest.support.ResponseHandlerUtil.handleFailure;
import static org.folio.rest.support.ResponseHandlerUtil.handleFailureWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.PatchRequest;
import org.folio.rest.jaxrs.model.RetrieveEntitiesRequest;
import org.folio.rest.jaxrs.resource.HoldingsStorage;
import org.folio.rest.support.ResponseHandlerUtil;
import org.folio.rest.support.StreamingJsonResponse;
import org.folio.services.holding.HoldingsService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class HoldingsStorageApi implements HoldingsStorage {

  @Autowired
  private HoldingsService holdingsService;

  public HoldingsStorageApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void getHoldingsStorageHoldings(String totalRecords, int offset, int limit, String query,
                                         RoutingContext routingContext, Map<String, String> headers,
                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                         Context vertxContext) {
    holdingsService.getByInstanceId(offset, limit, query, headers)
      .onSuccess(json -> {
        if (json != null) {
          asyncResultHandler.handle(succeededFuture(Response.ok(json, MediaType.APPLICATION_JSON).build()));
          return;
        }
        holdingsService.streamHoldings(query, offset, limit, headers)
          .onSuccess(result -> StreamingJsonResponse.write(result, "holdingsRecords", routingContext.response()))
          .onFailure(e -> StreamingJsonResponse.writeFailure(e, routingContext.response()));
      })
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postHoldingsStorageHoldings(HoldingsRecord entity,
                                          RoutingContext routingContext, Map<String, String> headers,
                                          Handler<AsyncResult<Response>> asyncResultHandler,
                                          Context vertxContext) {
    holdingsService.createHolding(entity, headers)
      .map(created -> PostHoldingsStorageHoldingsResponse.respond201WithApplicationJson(created,
        PostHoldingsStorageHoldingsResponse.headersFor201().withLocation(created.getId())))
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailureWith(asyncResultHandler, ResponseHandlerUtil::handleHridError));
  }

  @Validate
  @Override
  public void deleteHoldingsStorageHoldings(String query,
                                            RoutingContext routingContext, Map<String, String> headers,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {
    holdingsService.deleteHoldings(query, headers)
      .map(notUsed -> DeleteHoldingsStorageHoldingsResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getHoldingsStorageHoldingsByHoldingsRecordId(String holdingsRecordId,
                                                           Map<String, String> headers,
                                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                                           Context vertxContext) {

    holdingsService.getHoldings(holdingsRecordId, headers)
      .map(GetHoldingsStorageHoldingsByHoldingsRecordIdResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        GetHoldingsStorageHoldingsByHoldingsRecordIdResponse.respond404WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void putHoldingsStorageHoldingsByHoldingsRecordId(String holdingsRecordId,
                                                           HoldingsRecord entity,
                                                           Map<String, String> headers,
                                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                                           Context vertxContext) {

    holdingsService.updateHoldingRecord(holdingsRecordId, entity, headers)
      .onSuccess(notUsed -> asyncResultHandler.handle(Future.succeededFuture(
        PutHoldingsStorageHoldingsByHoldingsRecordIdResponse.respond204())))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void patchHoldingsStorageHoldingsByHoldingsRecordId(String holdingsRecordId,
                                                             PatchRequest entity,
                                                             Map<String, String> headers,
                                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                                             Context vertxContext) {

    holdingsService.patchHoldingRecord(holdingsRecordId, entity, headers)
      .onSuccess(notUsed -> asyncResultHandler.handle(Future.succeededFuture(
        PutHoldingsStorageHoldingsByHoldingsRecordIdResponse.respond204())))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteHoldingsStorageHoldingsByHoldingsRecordId(String holdingsRecordId,
                                                              Map<String, String> headers,
                                                              Handler<AsyncResult<Response>> asyncResultHandler,
                                                              Context vertxContext) {

    holdingsService.deleteHolding(holdingsRecordId, headers)
      .map(notUsed -> DeleteHoldingsStorageHoldingsByHoldingsRecordIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postHoldingsStorageHoldingsRetrieve(RetrieveEntitiesRequest entity,
                                                  RoutingContext routingContext,
                                                  Map<String, String> headers,
                                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                                  Context vertxContext) {
    holdingsService.streamHoldings(entity.getQuery(), entity.getOffset(), entity.getLimit(), headers)
      .onSuccess(result -> StreamingJsonResponse.write(result, "holdingsRecords", routingContext.response()))
      .onFailure(e -> StreamingJsonResponse.writeFailure(e, routingContext.response()));
  }
}
