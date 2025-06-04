package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.CallNumberType;
import org.folio.services.callnumber.CallNumberTypeService;

public class CallNumberTypeApi implements org.folio.rest.jaxrs.resource.CallNumberTypes {

  @Validate
  @Override
  public void getCallNumberTypes(String query, String totalRecords, int offset, int limit,
                                 Map<String, String> okapiHeaders,
                                 Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new CallNumberTypeService(vertxContext, okapiHeaders).getByQuery(query, offset, limit)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postCallNumberTypes(CallNumberType entity, Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new CallNumberTypeService(vertxContext, okapiHeaders).create(entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getCallNumberTypesById(String id, Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new CallNumberTypeService(vertxContext, okapiHeaders).getByTypeId(id)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteCallNumberTypesById(String id, Map<String, String> okapiHeaders,
                                        Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new CallNumberTypeService(vertxContext, okapiHeaders).delete(id)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void putCallNumberTypesById(String id,
                                     CallNumberType entity,
                                     Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler,
                                     Context vertxContext) {
    new CallNumberTypeService(vertxContext, okapiHeaders).update(id, entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }
}
