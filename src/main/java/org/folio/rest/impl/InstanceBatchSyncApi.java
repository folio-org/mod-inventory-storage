package org.folio.rest.impl;

import static org.folio.rest.jaxrs.resource.InstanceStorageBatchSynchronous.PostInstanceStorageBatchSynchronousResponse.respond500WithTextPlain;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.InstancesPost;
import org.folio.rest.jaxrs.resource.InstanceStorageBatchSynchronous;
import org.folio.services.instance.InstanceService;

public class InstanceBatchSyncApi implements InstanceStorageBatchSynchronous {
  @Validate
  @Override
  public void postInstanceStorageBatchSynchronous(boolean upsert, InstancesPost entity,
                                                  Map<String, String> okapiHeaders,
                                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                                  Context vertxContext) {

    new InstanceService(vertxContext, okapiHeaders)
      .createInstances(entity.getInstances(), upsert, true)
      .otherwise(cause -> respond500WithTextPlain(cause.getMessage()))
      .onComplete(asyncResultHandler);
  }
}
