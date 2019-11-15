package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.InstancesPost;
import org.folio.rest.jaxrs.resource.InstanceStorageBatchSynchronous;
import javax.ws.rs.core.Response;
import java.util.Map;

public class InstanceBatchSyncAPI implements InstanceStorageBatchSynchronous {
  @Validate
  @Override
  public void postInstanceStorageBatchSynchronous(InstancesPost entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    StorageHelper.postSync(InstanceStorageAPI.INSTANCE_TABLE, entity.getInstances(),
        okapiHeaders, asyncResultHandler, vertxContext,
        InstanceStorageBatchSynchronous.PostInstanceStorageBatchSynchronousResponse::respond201);
  }
}
