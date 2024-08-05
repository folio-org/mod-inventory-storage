package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.PublishReindexRecords;
import org.folio.rest.jaxrs.resource.InventoryReindexRecordsPublish;
import org.folio.services.holding.HoldingsService;
import org.folio.services.instance.InstanceService;
import org.folio.services.item.ItemService;

public class InventoryReindexRecordsPublishApi implements InventoryReindexRecordsPublish {

  @Override
  public void postInventoryReindexRecordsPublish(PublishReindexRecords entity,
                                                 Map<String, String> okapiHeaders,
                                                 Handler<AsyncResult<Response>> asyncResultHandler,
                                                 Context vertxContext) {
    var fromId = entity.getRecordIdsRange().getFrom();
    var toId = entity.getRecordIdsRange().getTo();
    var rangeId = entity.getId();

    Future<Void> publishFuture;
    switch (entity.getRecordType()) {
      case INSTANCE ->
        publishFuture = new InstanceService(vertxContext, okapiHeaders)
          .publishReindexInstanceRecords(rangeId, fromId, toId);
      case ITEM ->
        publishFuture = new ItemService(vertxContext, okapiHeaders)
          .publishReindexItemRecords(rangeId, fromId, toId);
      case HOLDING ->
        publishFuture = new HoldingsService(vertxContext, okapiHeaders)
          .publishReindexHoldingsRecords(rangeId, fromId, toId);
      default -> publishFuture = Future.failedFuture(
        "Not supported record type is provided: %s"
          .formatted(entity.getRecordType().value()));
    }

    publishFuture
      .<Response>map(x -> PostInventoryReindexRecordsPublishResponse.respond201())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }
}
