package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.ResponseHandlerUtil.firstErrorMessage;
import static org.folio.rest.support.ResponseHandlerUtil.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.BoundWithPart;
import org.folio.services.instance.BoundWithPartService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class BoundWithPartApi implements org.folio.rest.jaxrs.resource.InventoryStorageBoundWithParts {
  public static final String BOUND_WITH_TABLE = "bound_with_part";

  @Autowired
  private BoundWithPartService service;

  public BoundWithPartApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void getInventoryStorageBoundWithParts(String query, String totalRecords, int offset, int limit,
                                                Map<String, String> headers,
                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                Context vertxContext) {
    service.getByQuery(query, offset, limit, totalRecords, headers)
      .map(GetInventoryStorageBoundWithPartsResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postInventoryStorageBoundWithParts(BoundWithPart entity, Map<String, String> headers,
                                                 Handler<AsyncResult<Response>> asyncResultHandler,
                                                 Context vertxContext) {
    service.create(entity, headers)
      .map(created -> PostInventoryStorageBoundWithPartsResponse.respond201WithApplicationJson(created,
        PostInventoryStorageBoundWithPartsResponse.headersFor201().withLocation(created.getId())))
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getInventoryStorageBoundWithPartsById(String id, Map<String, String> headers,
                                                    Handler<AsyncResult<Response>> asyncResultHandler,
                                                    Context vertxContext) {
    service.getById(id, headers)
      .map(GetInventoryStorageBoundWithPartsByIdResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        GetInventoryStorageBoundWithPartsByIdResponse.respond404WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void deleteInventoryStorageBoundWithPartsById(String id, Map<String, String> headers,
                                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                                       Context vertxContext) {
    service.delete(id, headers)
      .map(notUsed -> DeleteInventoryStorageBoundWithPartsByIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        DeleteInventoryStorageBoundWithPartsByIdResponse.respond400WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void putInventoryStorageBoundWithPartsById(String id, BoundWithPart entity,
                                                    Map<String, String> headers,
                                                    Handler<AsyncResult<Response>> asyncResultHandler,
                                                    Context vertxContext) {
    service.update(entity, id, headers)
      .map(notUsed -> PutInventoryStorageBoundWithPartsByIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        PutInventoryStorageBoundWithPartsByIdResponse.respond400WithTextPlain(firstErrorMessage(errors))));
  }
}
