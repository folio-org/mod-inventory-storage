package org.folio.services.holding;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.services.batch.BatchOperationContext;
import org.folio.services.domainevent.HoldingDomainEventPublisher;
import org.folio.services.domainevent.ItemDomainEventPublisher;

class HoldingsEventPublisher {
  private final HoldingDomainEventPublisher holdingEventPublisher;
  private final ItemDomainEventPublisher itemEventPublisher;

  HoldingsEventPublisher(Context context, Map<String, String> okapiHeaders) {
    this.holdingEventPublisher = new HoldingDomainEventPublisher(context, okapiHeaders);
    this.itemEventPublisher = new ItemDomainEventPublisher(context, okapiHeaders);
  }

  void publishHoldingsAndItemEvents(List<HoldingsRecord> newHoldings,
                                    Map<String, HoldingsRecord> oldHoldings,
                                    Map<String, List<Item>> itemsBeforeUpdate) {

    publishItemEvents(newHoldings, oldHoldings, itemsBeforeUpdate);
    publishHoldingsEvents(newHoldings, oldHoldings);
  }

  void publishUpdatedItems(HoldingsRecord oldHoldings, HoldingsRecord newHoldings,
                           List<Item> itemsBeforeUpdate) {
    itemEventPublisher.publishUpdated(oldHoldings, newHoldings, itemsBeforeUpdate);
  }

  void publishAllRemoved() {
    holdingEventPublisher.publishAllRemoved();
  }

  Handler<Response> publishCreated() {
    return holdingEventPublisher.publishCreated();
  }

  Handler<Response> publishRemoved(HoldingsRecord hr) {
    return holdingEventPublisher.publishRemoved(hr);
  }

  void publishRemoved(String holdingId, String rawRemoved) {
    holdingEventPublisher.publishRemoved(holdingId, rawRemoved);
  }

  Handler<Response> publishCreatedOrUpdated(BatchOperationContext<HoldingsRecord> batchOperation) {
    return holdingEventPublisher.publishCreatedOrUpdated(batchOperation);
  }

  Future<Void> publishReindexHoldings(String rangeId, List<Map<String, Object>> holdings) {
    return holdingEventPublisher.publishReindexHoldings(rangeId, holdings);
  }

  Handler<Response> publishUpdated(HoldingsRecord oldHoldings) {
    return holdingEventPublisher.publishUpdated(oldHoldings);
  }

  private void publishItemEvents(List<HoldingsRecord> newHoldings,
                                 Map<String, HoldingsRecord> oldHoldings,
                                 Map<String, List<Item>> itemsBeforeUpdate) {
    for (var newHolding : newHoldings) {
      var holdingsId = newHolding.getId();
      var oldHolding = oldHoldings.get(holdingsId);
      var itemsBefore = itemsBeforeUpdate.get(holdingsId);

      if (oldHolding != null && itemsBefore != null) {
        itemEventPublisher.publishUpdated(oldHolding, newHolding, itemsBefore);
      }
    }
  }

  private void publishHoldingsEvents(List<HoldingsRecord> newHoldings, Map<String, HoldingsRecord> oldHoldingsMap) {
    var oldHoldings = new ArrayList<>(oldHoldingsMap.values());
    var createdHoldings = newHoldings.stream()
      .filter(entity -> !oldHoldingsMap.containsKey(entity.getId()))
      .toList();

    var batchContext = new BatchOperationContext<>(createdHoldings, oldHoldings, true);
    holdingEventPublisher.publishCreatedOrUpdated(batchContext)
      .handle(Response.status(201).build());
  }
}
