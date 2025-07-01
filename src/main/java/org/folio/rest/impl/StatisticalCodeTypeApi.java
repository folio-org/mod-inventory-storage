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

  public static final String RESOURCE_TABLE = "statistical_code_type";

  @Validate
  @Override
  public void getStatisticalCodeTypes(String query, String totalRecords, int offset, int limit,
                                      Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, okapiHeaders, asyncResultHandler, vertxContext,
      GetStatisticalCodeTypesResponse.class);
  }

  @Validate
  @Override
  public void postStatisticalCodeTypes(StatisticalCodeType entity, Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, okapiHeaders, asyncResultHandler, vertxContext, PostStatisticalCodeTypesResponse.class);
  }

  @Validate
  @Override
  public void deleteStatisticalCodeTypes(Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    deleteEntities(okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Validate
  @Override
  public void getStatisticalCodeTypesByStatisticalCodeTypeId(String id,
                                                             Map<String, String> okapiHeaders,
                                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                                             Context vertxContext) {
    getEntityById(id, okapiHeaders, asyncResultHandler, vertxContext,
      GetStatisticalCodeTypesByStatisticalCodeTypeIdResponse.class);
  }

  @Validate
  @Override
  public void deleteStatisticalCodeTypesByStatisticalCodeTypeId(String id,
                                                                Map<String, String> okapiHeaders,
                                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                                Context vertxContext) {
    deleteEntityById(id, okapiHeaders, asyncResultHandler, vertxContext,
      DeleteStatisticalCodeTypesByStatisticalCodeTypeIdResponse.class);
  }

  @Validate
  @Override
  public void putStatisticalCodeTypesByStatisticalCodeTypeId(String id,
                                                             StatisticalCodeType entity,
                                                             Map<String, String> okapiHeaders,
                                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                                             Context vertxContext) {
    putEntityById(id, entity, okapiHeaders, asyncResultHandler, vertxContext,
      PutStatisticalCodeTypesByStatisticalCodeTypeIdResponse.class);
  }

  @Override
  protected String getReferenceTable() {
    return RESOURCE_TABLE;
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
