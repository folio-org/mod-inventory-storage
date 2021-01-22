package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.InstancesPost;
import org.folio.rest.jaxrs.resource.InstanceStorageBatchSynchronous;
import org.folio.services.instance.InstanceService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class InstanceBatchSyncAPI implements InstanceStorageBatchSynchronous {
  @Validate
  @Override
  public void postInstanceStorageBatchSynchronous(boolean upsert, InstancesPost entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new InstanceService(vertxContext, okapiHeaders).createInstances(
      entity.getInstances(), upsert)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(cause -> asyncResultHandler.handle(succeededFuture(
        PostInstanceStorageBatchSynchronousResponse.respond500WithTextPlain(
          cause.getMessage()))));
  }
}
