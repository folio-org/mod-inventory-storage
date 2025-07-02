package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.InstanceStatus;
import org.folio.rest.jaxrs.model.InstanceStatuses;

public class InstanceStatusApi extends BaseApi<InstanceStatus, InstanceStatuses>
  implements org.folio.rest.jaxrs.resource.InstanceStatuses {

  public static final String RESOURCE_TABLE = "instance_status";

  @Validate
  @Override
  public void getInstanceStatuses(String query, String totalRecords, int offset, int limit,
                                  Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, okapiHeaders, asyncResultHandler, vertxContext,
      GetInstanceStatusesResponse.class);
  }

  @Validate
  @Override
  public void postInstanceStatuses(InstanceStatus entity, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, okapiHeaders, asyncResultHandler, vertxContext, PostInstanceStatusesResponse.class);
  }

  @Validate
  @Override
  public void deleteInstanceStatuses(Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    deleteEntities(okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Validate
  @Override
  public void getInstanceStatusesByInstanceStatusId(String instanceStatusId,
                                                    Map<String, String> okapiHeaders,
                                                    Handler<AsyncResult<Response>> asyncResultHandler,
                                                    Context vertxContext) {
    getEntityById(instanceStatusId, okapiHeaders, asyncResultHandler, vertxContext,
      GetInstanceStatusesByInstanceStatusIdResponse.class);
  }

  @Validate
  @Override
  public void deleteInstanceStatusesByInstanceStatusId(String instanceStatusId,
                                                       Map<String, String> okapiHeaders,
                                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                                       Context vertxContext) {
    deleteEntityById(instanceStatusId, okapiHeaders, asyncResultHandler, vertxContext,
      DeleteInstanceStatusesByInstanceStatusIdResponse.class);
  }

  @Validate
  @Override
  public void putInstanceStatusesByInstanceStatusId(String instanceStatusId, InstanceStatus entity,
                                                    Map<String, String> okapiHeaders,
                                                    Handler<AsyncResult<Response>> asyncResultHandler,
                                                    Context vertxContext) {
    putEntityById(instanceStatusId, entity, okapiHeaders, asyncResultHandler, vertxContext,
      PutInstanceStatusesByInstanceStatusIdResponse.class);
  }

  @Override
  protected String getReferenceTable() {
    return RESOURCE_TABLE;
  }

  @Override
  protected Class<InstanceStatus> getEntityClass() {
    return InstanceStatus.class;
  }

  @Override
  protected Class<InstanceStatuses> getEntityCollectionClass() {
    return InstanceStatuses.class;
  }
}
