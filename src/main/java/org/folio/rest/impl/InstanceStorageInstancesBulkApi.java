package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.InstanceBulkRequest;
import org.folio.rest.jaxrs.resource.InstanceStorageInstancesBulk;
import org.folio.rest.support.EndpointFailureHandler;
import org.folio.services.instance.InstanceS3Service;
import org.folio.services.s3storage.FolioS3ClientFactory;

public class InstanceStorageInstancesBulkApi implements InstanceStorageInstancesBulk {

  @Override
  public void postInstanceStorageInstancesBulk(InstanceBulkRequest bulkRequest, Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    new InstanceS3Service(new FolioS3ClientFactory(), vertxContext.owner(), okapiHeaders)
      .processInstances(bulkRequest)
      .map(PostInstanceStorageInstancesBulkResponse::respond201WithApplicationJson)
      .map(Response.class::cast)
      .onFailure(EndpointFailureHandler::failureResponse)
      .onComplete(asyncResultHandler);
  }

}
