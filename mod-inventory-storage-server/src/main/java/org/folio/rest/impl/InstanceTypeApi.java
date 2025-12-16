package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.InstanceType;
import org.folio.rest.jaxrs.model.InstanceTypes;
import org.folio.rest.persist.PgUtil;

public class InstanceTypeApi extends BaseApi<InstanceType, InstanceTypes>
  implements org.folio.rest.jaxrs.resource.InstanceTypes {

  public static final String INSTANCE_TYPE_TABLE = "instance_type";

  @Validate
  @Override
  public void getInstanceTypes(String query, String totalRecords, int offset, int limit,
                               Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                               Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, okapiHeaders, asyncResultHandler, vertxContext,
      GetInstanceTypesResponse.class);
  }

  @Validate
  @Override
  public void postInstanceTypes(InstanceType entity, Map<String, String> okapiHeaders,
                                Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, okapiHeaders, asyncResultHandler, vertxContext, PostInstanceTypesResponse.class);
  }

  @Validate
  @Override
  public void getInstanceTypesByInstanceTypeId(String instanceTypeId,
                                               Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    PgUtil.getById(INSTANCE_TYPE_TABLE, InstanceType.class, instanceTypeId,
      okapiHeaders, vertxContext, GetInstanceTypesByInstanceTypeIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteInstanceTypesByInstanceTypeId(String instanceTypeId,
                                                  Map<String, String> okapiHeaders,
                                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                                  Context vertxContext) {
    deleteEntityById(instanceTypeId, okapiHeaders, asyncResultHandler, vertxContext,
      DeleteInstanceTypesByInstanceTypeIdResponse.class);
  }

  @Validate
  @Override
  public void putInstanceTypesByInstanceTypeId(String instanceTypeId, InstanceType entity,
                                               Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    putEntityById(instanceTypeId, entity, okapiHeaders, asyncResultHandler, vertxContext,
      PutInstanceTypesByInstanceTypeIdResponse.class);
  }

  @Override
  protected String getReferenceTable() {
    return INSTANCE_TYPE_TABLE;
  }

  @Override
  protected Class<InstanceType> getEntityClass() {
    return InstanceType.class;
  }

  @Override
  protected Class<InstanceTypes> getEntityCollectionClass() {
    return InstanceTypes.class;
  }
}
