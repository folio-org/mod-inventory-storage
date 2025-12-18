package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.LoanType;
import org.folio.rest.jaxrs.model.LoanTypes;
import org.folio.rest.persist.PgUtil;

public class LoanTypeApi extends BaseApi<LoanType, LoanTypes>
  implements org.folio.rest.jaxrs.resource.LoanTypes {

  public static final String LOAN_TYPE_TABLE = "loan_type";

  @Validate
  @Override
  public void getLoanTypes(String query, String totalRecords, int offset, int limit,
                           Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                           Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, okapiHeaders, asyncResultHandler, vertxContext,
      GetLoanTypesResponse.class);
  }

  @Validate
  @Override
  public void deleteLoanTypes(Map<String, String> okapiHeaders,
                              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    deleteEntities(okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Validate
  @Override
  public void postLoanTypes(LoanType entity, Map<String, String> okapiHeaders,
                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, okapiHeaders, asyncResultHandler, vertxContext, PostLoanTypesResponse.class);
  }

  @Validate
  @Override
  public void getLoanTypesByLoantypeId(String loantypeId,
                                       Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                       Context vertxContext) {
    PgUtil.getById(LOAN_TYPE_TABLE, LoanType.class, loantypeId, okapiHeaders, vertxContext,
      GetLoanTypesByLoantypeIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteLoanTypesByLoantypeId(String loantypeId,
                                          Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler,
                                          Context vertxContext) {
    deleteEntityById(loantypeId, okapiHeaders, asyncResultHandler, vertxContext,
      DeleteLoanTypesByLoantypeIdResponse.class);
  }

  @Validate
  @Override
  public void putLoanTypesByLoantypeId(String loantypeId, LoanType entity,
                                       Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                       Context vertxContext) {
    putEntityById(loantypeId, entity, okapiHeaders, asyncResultHandler, vertxContext,
      PutLoanTypesByLoantypeIdResponse.class);
  }

  @Override
  protected String getReferenceTable() {
    return LOAN_TYPE_TABLE;
  }

  @Override
  protected Class<LoanType> getEntityClass() {
    return LoanType.class;
  }

  @Override
  protected Class<LoanTypes> getEntityCollectionClass() {
    return LoanTypes.class;
  }
}
