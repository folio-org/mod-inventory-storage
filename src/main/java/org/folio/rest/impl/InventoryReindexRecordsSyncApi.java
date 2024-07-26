package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.ReindexSyncRecords;
import org.folio.rest.jaxrs.resource.InventoryReindexRecordsSync;
import org.folio.services.instance.InstanceService;

public class InventoryReindexRecordsSyncApi implements InventoryReindexRecordsSync {

  @Override
  public void postInventoryReindexRecordsSync(ReindexSyncRecords entity,
                                              Map<String, String> okapiHeaders,
                                              Handler<AsyncResult<Response>> asyncResultHandler,
                                              Context vertxContext) {
    var fromId = entity.getRecordIdsRange().getFrom();
    var toId = entity.getRecordIdsRange().getTo();
    if (entity.getRecordType() == ReindexSyncRecords.RecordType.INSTANCE) {
      new InstanceService(vertxContext, okapiHeaders)
        .publishReindexInstanceRecords(fromId, toId)
        .<Response>map(x -> PostInventoryReindexRecordsSyncResponse.respond201())
        .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
        .onFailure(handleFailure(asyncResultHandler));
    }
  }
}
