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
import org.folio.rest.jaxrs.model.CallNumberType;
import org.folio.services.callnumber.CallNumberTypeService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class CallNumberTypeApi implements org.folio.rest.jaxrs.resource.CallNumberTypes {

  @Autowired
  private CallNumberTypeService service;

  public CallNumberTypeApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void getCallNumberTypes(String query, String totalRecords, int offset, int limit,
                                 Map<String, String> headers,
                                 Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    service.getByQuery(query, offset, limit, totalRecords, headers)
      .map(GetCallNumberTypesResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postCallNumberTypes(CallNumberType entity, Map<String, String> headers,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    service.create(entity, headers)
      .map(created -> PostCallNumberTypesResponse.respond201WithApplicationJson(created,
        PostCallNumberTypesResponse.headersFor201().withLocation(created.getId())))
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getCallNumberTypesById(String id, Map<String, String> headers,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    service.getByTypeId(id, headers)
      .map(GetCallNumberTypesByIdResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        GetCallNumberTypesByIdResponse.respond404WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void deleteCallNumberTypesById(String id, Map<String, String> headers,
                                        Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    service.delete(id, headers)
      .map(notUsed -> DeleteCallNumberTypesByIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        DeleteCallNumberTypesByIdResponse.respond400WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void putCallNumberTypesById(String id,
                                     CallNumberType entity,
                                     Map<String, String> headers,
                                     Handler<AsyncResult<Response>> asyncResultHandler,
                                     Context vertxContext) {
    service.update(id, entity, headers)
      .map(notUsed -> PutCallNumberTypesByIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        PutCallNumberTypesByIdResponse.respond400WithTextPlain(firstErrorMessage(errors))));
  }
}
