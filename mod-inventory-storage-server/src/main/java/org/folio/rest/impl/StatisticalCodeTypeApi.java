package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.StatisticalCodeType;
import org.folio.rest.jaxrs.model.StatisticalCodeTypes;

public class StatisticalCodeTypeApi extends BaseApi<StatisticalCodeType, StatisticalCodeTypes>
  implements org.folio.rest.jaxrs.resource.StatisticalCodeTypes {

  public static final String STATISTICAL_CODE_TYPE_TABLE = "statistical_code_type";

  @Validate
  @Override
  public void getStatisticalCodeTypes(String query, String totalRecords, int offset, int limit,
                                      Map<String, String> headers,
                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void postStatisticalCodeTypes(StatisticalCodeType entity, Map<String, String> headers,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteStatisticalCodeTypes(Map<String, String> headers,
                                         Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    deleteEntities(headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void getStatisticalCodeTypesByStatisticalCodeTypeId(String id,
                                                             Map<String, String> headers,
                                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                                             Context vertxContext) {
    getEntityById(id, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteStatisticalCodeTypesByStatisticalCodeTypeId(String id,
                                                                Map<String, String> headers,
                                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                                Context vertxContext) {
    deleteEntityById(id, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void putStatisticalCodeTypesByStatisticalCodeTypeId(String id,
                                                             StatisticalCodeType entity,
                                                             Map<String, String> headers,
                                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                                             Context vertxContext) {
    putEntityById(id, entity, headers, asyncResultHandler);
  }

  @Override
  protected String getReferenceTable() {
    return STATISTICAL_CODE_TYPE_TABLE;
  }

  @Override
  protected Class<StatisticalCodeType> getEntityClass() {
    return StatisticalCodeType.class;
  }

  @Override
  protected Class<StatisticalCodeTypes> getEntityCollectionClass() {
    return StatisticalCodeTypes.class;
  }
}
