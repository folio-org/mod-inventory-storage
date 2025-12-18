package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.BulkUpsertRequest;
import org.folio.rest.jaxrs.resource.InstanceStorageInstancesBulk;
import org.folio.rest.support.EndpointFailureHandler;
import org.folio.services.bulkprocessing.InstanceS3Service;
import org.folio.services.s3storage.FolioS3ClientFactory;

public class InstanceStorageInstancesBulkApi implements InstanceStorageInstancesBulk {

  @Override
  public void postInstanceStorageInstancesBulk(BulkUpsertRequest bulkRequest, Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    new InstanceS3Service(new FolioS3ClientFactory(), vertxContext.owner(), okapiHeaders)
      .processBulkUpsert(bulkRequest)
      .map(PostInstanceStorageInstancesBulkResponse::respond201WithApplicationJson)
      .map(Response.class::cast)
      .otherwise(EndpointFailureHandler::failureResponse)
      .onComplete(asyncResultHandler);
  }
}
