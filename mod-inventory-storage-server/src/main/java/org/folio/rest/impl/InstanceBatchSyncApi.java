package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.ResponseHandlerUtil.handleFailureWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.InstancesPostRequest;
import org.folio.rest.jaxrs.resource.InstanceStorageBatchSynchronous;
import org.folio.rest.support.ResponseHandlerUtil;
import org.folio.services.instance.InstanceService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class InstanceBatchSyncApi implements InstanceStorageBatchSynchronous {

  @Autowired
  private InstanceService instanceService;

  public InstanceBatchSyncApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void postInstanceStorageBatchSynchronous(boolean upsert, InstancesPostRequest entity,
                                                  Map<String, String> headers,
                                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                                  Context vertxContext) {

    instanceService.createInstances(entity.getInstances(), upsert, true, true, headers)
      .map(notUsed -> PostInstanceStorageBatchSynchronousResponse.respond201())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailureWith(asyncResultHandler, ResponseHandlerUtil::handleHridError));
  }
}
