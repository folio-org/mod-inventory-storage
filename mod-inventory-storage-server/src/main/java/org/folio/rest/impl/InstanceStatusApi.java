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

  public static final String INSTANCE_STATUS_TABLE = "instance_status";

  @Validate
  @Override
  public void getInstanceStatuses(String query, String totalRecords, int offset, int limit,
                                  Map<String, String> headers,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void postInstanceStatuses(InstanceStatus entity, Map<String, String> headers,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteInstanceStatuses(Map<String, String> headers,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    deleteEntities(headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void getInstanceStatusesByInstanceStatusId(String instanceStatusId,
                                                    Map<String, String> headers,
                                                    Handler<AsyncResult<Response>> asyncResultHandler,
                                                    Context vertxContext) {
    getEntityById(instanceStatusId, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteInstanceStatusesByInstanceStatusId(String instanceStatusId,
                                                       Map<String, String> headers,
                                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                                       Context vertxContext) {
    deleteEntityById(instanceStatusId, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void putInstanceStatusesByInstanceStatusId(String instanceStatusId, InstanceStatus entity,
                                                    Map<String, String> headers,
                                                    Handler<AsyncResult<Response>> asyncResultHandler,
                                                    Context vertxContext) {
    putEntityById(instanceStatusId, entity, headers, asyncResultHandler);
  }

  @Override
  protected String getReferenceTable() {
    return INSTANCE_STATUS_TABLE;
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
