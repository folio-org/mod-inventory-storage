package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.ResponseHandlerUtil.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.ReindexRecordsRequest;
import org.folio.rest.jaxrs.resource.InventoryReindexRecordsExport;
import org.folio.rest.jaxrs.resource.InventoryReindexRecordsPublish;
import org.folio.services.holding.HoldingsService;
import org.folio.services.instance.InstanceService;
import org.folio.services.item.ItemService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class InventoryReindexRecordsPublishApi implements InventoryReindexRecordsPublish,
  InventoryReindexRecordsExport {

  @Autowired
  private HoldingsService holdingsService;
  @Autowired
  private ItemService itemService;
  @Autowired
  private InstanceService instanceService;

  public InventoryReindexRecordsPublishApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  public void postInventoryReindexRecordsPublish(ReindexRecordsRequest entity,
                                                 Map<String, String> headers,
                                                 Handler<AsyncResult<Response>> asyncResultHandler,
                                                 Context vertxContext) {
    var fromId = entity.getRecordIdsRange().getFrom();
    var toId = entity.getRecordIdsRange().getTo();
    var rangeId = entity.getId();

    Future<Void> publishFuture;
    switch (entity.getRecordType()) {
      case INSTANCE -> publishFuture = instanceService.publishReindexInstanceRecords(rangeId, fromId, toId, headers);
      case ITEM -> publishFuture = itemService.publishReindexItemRecords(rangeId, fromId, toId, headers);
      case HOLDINGS -> publishFuture = holdingsService.publishReindexHoldingsRecords(rangeId, fromId, toId, headers);
      default -> publishFuture = Future.failedFuture(
        "Not supported record type is provided: %s"
          .formatted(entity.getRecordType().value()));
    }

    publishFuture
      .<Response>map(x -> PostInventoryReindexRecordsPublishResponse.respond201())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void postInventoryReindexRecordsExport(ReindexRecordsRequest entity, Map<String, String> headers,
                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                Context vertxContext) {
    Future<Void> publishFuture;
    switch (entity.getRecordType()) {
      case INSTANCE -> publishFuture = instanceService.exportReindexInstanceRecords(entity, headers);
      case ITEM -> publishFuture = itemService.exportReindexItemRecords(entity, headers);
      case HOLDINGS -> publishFuture = holdingsService.exportReindexHoldingsRecords(entity, headers);
      default -> publishFuture = Future.failedFuture(
        "Not supported record type is provided: %s"
          .formatted(entity.getRecordType().value()));
    }

    publishFuture
      .<Response>map(x -> PostInventoryReindexRecordsExportResponse.respond200())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }
}
