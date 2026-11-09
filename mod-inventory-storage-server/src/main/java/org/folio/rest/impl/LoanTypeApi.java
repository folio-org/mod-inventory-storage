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
import org.folio.rest.jaxrs.model.LoanType;
import org.folio.rest.jaxrs.resource.LoanTypes;
import org.folio.services.loantype.LoanTypeService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class LoanTypeApi implements LoanTypes {

  @Autowired
  private LoanTypeService service;

  public LoanTypeApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void getLoanTypes(String query, String totalRecords, int offset, int limit,
                           Map<String, String> headers,
                           Handler<AsyncResult<Response>> asyncResultHandler,
                           Context vertxContext) {
    service.getByQuery(query, offset, limit, totalRecords, headers)
      .map(GetLoanTypesResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteLoanTypes(Map<String, String> headers,
                              Handler<AsyncResult<Response>> asyncResultHandler,
                              Context vertxContext) {
    service.deleteAll(headers)
      .map(notUsed -> DeleteLoanTypesResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postLoanTypes(LoanType entity, Map<String, String> headers,
                            Handler<AsyncResult<Response>> asyncResultHandler,
                            Context vertxContext) {
    service.create(entity, headers)
      .map(created -> PostLoanTypesResponse.respond201WithApplicationJson(created,
        PostLoanTypesResponse.headersFor201().withLocation(created.getId())))
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getLoanTypesByLoantypeId(String loantypeId,
                                       Map<String, String> headers,
                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                       Context vertxContext) {
    service.getById(loantypeId, headers)
      .map(GetLoanTypesByLoantypeIdResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        GetLoanTypesByLoantypeIdResponse.respond404WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void deleteLoanTypesByLoantypeId(String loantypeId,
                                          Map<String, String> headers,
                                          Handler<AsyncResult<Response>> asyncResultHandler,
                                          Context vertxContext) {
    service.delete(loantypeId, headers)
      .map(notUsed -> DeleteLoanTypesByLoantypeIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        DeleteLoanTypesByLoantypeIdResponse.respond400WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void putLoanTypesByLoantypeId(String loantypeId, LoanType entity,
                                       Map<String, String> headers,
                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                       Context vertxContext) {
    service.update(loantypeId, entity, headers)
      .map(notUsed -> PutLoanTypesByLoantypeIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        PutLoanTypesByLoantypeIdResponse.respond400WithTextPlain(firstErrorMessage(errors))));
  }
}
