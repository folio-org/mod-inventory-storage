package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.ReindexSyncRecords;
import org.folio.rest.jaxrs.resource.InventoryReindexRecordsSync;
import org.folio.services.holding.HoldingsService;
import org.folio.services.instance.InstanceService;
import org.folio.services.item.ItemService;

public class InventoryReindexRecordsSyncApi implements InventoryReindexRecordsSync {

  @Override
  public void postInventoryReindexRecordsSync(ReindexSyncRecords entity,
                                              Map<String, String> okapiHeaders,
                                              Handler<AsyncResult<Response>> asyncResultHandler,
                                              Context vertxContext) {
    var fromId = entity.getRecordIdsRange().getFrom();
    var toId = entity.getRecordIdsRange().getTo();

    Future<Void> publishFuture;
    switch (entity.getRecordType()) {
      case INSTANCE ->
        publishFuture = new InstanceService(vertxContext, okapiHeaders)
          .publishReindexInstanceRecords(fromId, toId);
      case ITEM ->
        publishFuture = new ItemService(vertxContext, okapiHeaders)
          .publishReindexItemRecords(fromId, toId);
      case HOLDING ->
        publishFuture = new HoldingsService(vertxContext, okapiHeaders)
          .publishReindexHoldingsRecords(fromId, toId);
      default -> publishFuture = Future.failedFuture(
        "Not supported record type is provided: %s"
          .formatted(entity.getRecordType().value()));
    }

    publishFuture
      .<Response>map(x -> PostInventoryReindexRecordsSyncResponse.respond201())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));

  }
}
