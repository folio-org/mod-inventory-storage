package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.LoanType;
import org.folio.rest.jaxrs.resource.LoanTypes;
import org.folio.services.loantype.LoanTypeService;

public class LoanTypeApi implements LoanTypes {

  @Validate
  @Override
  public void getLoanTypes(String query, String totalRecords, int offset, int limit,
                           Map<String, String> okapiHeaders,
                           Handler<AsyncResult<Response>> asyncResultHandler,
                           Context vertxContext) {
    new LoanTypeService(vertxContext, okapiHeaders).getByQuery(query, offset, limit)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteLoanTypes(Map<String, String> okapiHeaders,
                              Handler<AsyncResult<Response>> asyncResultHandler,
                              Context vertxContext) {
    new LoanTypeService(vertxContext, okapiHeaders).deleteAll()
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postLoanTypes(LoanType entity, Map<String, String> okapiHeaders,
                            Handler<AsyncResult<Response>> asyncResultHandler,
                            Context vertxContext) {
    new LoanTypeService(vertxContext, okapiHeaders).create(entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getLoanTypesByLoantypeId(String loantypeId,
                                       Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                       Context vertxContext) {
    new LoanTypeService(vertxContext, okapiHeaders).getById(loantypeId)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteLoanTypesByLoantypeId(String loantypeId,
                                          Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler,
                                          Context vertxContext) {
    new LoanTypeService(vertxContext, okapiHeaders).delete(loantypeId)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void putLoanTypesByLoantypeId(String loantypeId, LoanType entity,
                                       Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                       Context vertxContext) {
    new LoanTypeService(vertxContext, okapiHeaders).update(loantypeId, entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }
}
