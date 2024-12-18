package org.folio.rest.impl;

import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Instances;
import org.folio.rest.jaxrs.model.InstancesPost;
import org.folio.rest.jaxrs.resource.InstanceStorageBatchSynchronous;
import org.folio.services.instance.InstanceService;
import org.folio.utils.ObjectConverterUtils;

public class InstanceBatchSyncApi implements InstanceStorageBatchSynchronous {
  @Validate
  @Override
  public void postInstanceStorageBatchSynchronous(boolean upsert, InstancesPost entity,
                                                  Map<String, String> okapiHeaders,
                                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                                  Context vertxContext) {

    var instances = ObjectConverterUtils.convertObject(entity, Instances.class);

    new InstanceService(vertxContext, okapiHeaders)
      .createInstances(instances.getInstances(), upsert, true, true)
      .onFailure(handleFailure(asyncResultHandler))
      .onComplete(asyncResultHandler);
  }
}
