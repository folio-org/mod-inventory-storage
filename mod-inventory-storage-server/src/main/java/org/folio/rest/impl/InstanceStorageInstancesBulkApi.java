package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.BulkUpsertRequest;
import org.folio.rest.jaxrs.resource.InstanceStorageInstancesBulk;
import org.folio.rest.support.ResponseHandlerUtil;
import org.folio.services.bulkprocessing.InstanceS3Service;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class InstanceStorageInstancesBulkApi implements InstanceStorageInstancesBulk {

  @Autowired
  private InstanceS3Service instanceS3Service;

  public InstanceStorageInstancesBulkApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  public void postInstanceStorageInstancesBulk(BulkUpsertRequest bulkRequest, Map<String, String> headers,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    instanceS3Service.processBulkUpsert(bulkRequest, headers)
      .map(PostInstanceStorageInstancesBulkResponse::respond201WithApplicationJson)
      .map(Response.class::cast)
      .otherwise(ResponseHandlerUtil::failureResponse)
      .onComplete(asyncResultHandler);
  }
}
