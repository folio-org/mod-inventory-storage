package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.StatisticalCode;
import org.folio.rest.jaxrs.model.StatisticalCodes;

public class StatisticalCodeApi extends BaseApi<StatisticalCode, StatisticalCodes>
  implements org.folio.rest.jaxrs.resource.StatisticalCodes {

  public static final String REFERENCE_TABLE = "statistical_code";

  @Validate
  @Override
  public void getStatisticalCodes(String query, String totalRecords, int offset, int limit,
                                  Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, okapiHeaders, asyncResultHandler, vertxContext,
      GetStatisticalCodesResponse.class);
  }

  @Validate
  @Override
  public void postStatisticalCodes(StatisticalCode entity, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, okapiHeaders, asyncResultHandler, vertxContext, PostStatisticalCodesResponse.class);
  }

  @Validate
  @Override
  public void getStatisticalCodesByStatisticalCodeId(String id, Map<String, String> okapiHeaders,
                                                     Handler<AsyncResult<Response>> asyncResultHandler,
                                                     Context vertxContext) {
    getEntityById(id, okapiHeaders, asyncResultHandler, vertxContext,
      GetStatisticalCodesByStatisticalCodeIdResponse.class);
  }

  @Validate
  @Override
  public void deleteStatisticalCodesByStatisticalCodeId(String id, Map<String, String> okapiHeaders,
                                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                                        Context vertxContext) {
    deleteEntityById(id, okapiHeaders, asyncResultHandler, vertxContext,
      DeleteStatisticalCodesByStatisticalCodeIdResponse.class);
  }

  @Validate
  @Override
  public void putStatisticalCodesByStatisticalCodeId(String id, StatisticalCode entity,
                                                     Map<String, String> okapiHeaders,
                                                     Handler<AsyncResult<Response>> asyncResultHandler,
                                                     Context vertxContext) {
    putEntityById(id, entity, okapiHeaders, asyncResultHandler, vertxContext,
      PutStatisticalCodesByStatisticalCodeIdResponse.class);
  }

  @Override
  protected String getReferenceTable() {
    return REFERENCE_TABLE;
  }

  @Override
  protected Class<StatisticalCode> getEntityClass() {
    return StatisticalCode.class;
  }

  @Override
  protected Class<StatisticalCodes> getEntityCollectionClass() {
    return StatisticalCodes.class;
  }
}
