package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.BulkUpsertRequest;
import org.folio.rest.jaxrs.resource.InstanceStorageInstancesBulk;
import org.folio.rest.support.EndpointFailureHandler;

public class InstanceStorageInstancesBulkApi implements InstanceStorageInstancesBulk {

  @Override
  public void postInstanceStorageInstancesBulk(BulkUpsertRequest bulkRequest, Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    asyncResultHandler.handle(Future.succeededFuture(
      EndpointFailureHandler.failureResponse(new RuntimeException("Forced failure response"))
    ));
  }
}
