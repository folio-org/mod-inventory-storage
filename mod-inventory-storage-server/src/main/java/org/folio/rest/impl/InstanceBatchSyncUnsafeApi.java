package org.folio.rest.impl;

import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.InstancesPostRequest;
import org.folio.rest.jaxrs.resource.InstanceStorageBatchSynchronousUnsafe;
import org.folio.services.instance.InstanceService;

public class InstanceBatchSyncUnsafeApi implements InstanceStorageBatchSynchronousUnsafe {
  @Validate
  @Override
  public void postInstanceStorageBatchSynchronousUnsafe(InstancesPostRequest entity, Map<String, String> okapiHeaders,
                                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                                        Context vertxContext) {

    new InstanceService(vertxContext, okapiHeaders)
      .createInstances(entity.getInstances(), true, false, true)
      .onFailure(handleFailure(asyncResultHandler))
      .onComplete(asyncResultHandler);
  }
}
