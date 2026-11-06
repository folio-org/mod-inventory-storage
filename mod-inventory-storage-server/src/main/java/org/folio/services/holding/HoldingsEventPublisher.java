package org.folio.services.holding;

import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.services.batch.BatchOperationContext;
import org.folio.services.domainevent.HoldingDomainEventPublisher;
import org.folio.services.domainevent.ItemDomainEventPublisher;
import org.springframework.stereotype.Component;

@Component
class HoldingsEventPublisher {
  private final HoldingDomainEventPublisher holdingEventPublisher;
  private final ItemDomainEventPublisher itemEventPublisher;

  HoldingsEventPublisher(HoldingDomainEventPublisher holdingEventPublisher,
                         ItemDomainEventPublisher itemEventPublisher) {
    this.holdingEventPublisher = holdingEventPublisher;
    this.itemEventPublisher = itemEventPublisher;
  }

  void publishHoldingsAndItemEvents(List<HoldingsRecord> newHoldings,
                                    Map<String, HoldingsRecord> oldHoldings,
                                    Map<String, List<Item>> itemsBeforeUpdate,
                                    Map<String, String> headers) {

    publishItemEvents(newHoldings, oldHoldings, itemsBeforeUpdate, headers);
    publishHoldingsEvents(newHoldings, oldHoldings, headers);
  }

  void publishUpdatedItems(HoldingsRecord oldHoldings, HoldingsRecord newHoldings,
                           List<Item> itemsBeforeUpdate, Map<String, String> headers) {
    itemEventPublisher.publishUpdated(oldHoldings, newHoldings, itemsBeforeUpdate, headers);
  }

  void publishAllRemoved(Map<String, String> headers) {
    holdingEventPublisher.publishAllRemoved(headers);
  }

  Future<Void> publishRecordCreated(HoldingsRecord hr, Map<String, String> headers) {
    return holdingEventPublisher.publishRecordCreated(hr, headers);
  }

  Future<Void> publishRecordRemoved(HoldingsRecord hr, Map<String, String> headers) {
    return holdingEventPublisher.publishRecordRemoved(hr, headers);
  }

  void publishRemoved(String holdingId, String rawRemoved, Map<String, String> headers) {
    holdingEventPublisher.publishRemoved(holdingId, rawRemoved, headers);
  }

  Future<Void> publishCreatedOrUpdated(BatchOperationContext<HoldingsRecord> batchOperation,
                                       Map<String, String> headers) {
    return holdingEventPublisher.publishRecordsCreatedOrUpdated(batchOperation, headers);
  }

  Future<Void> publishReindexHoldings(String rangeId, List<Map<String, Object>> holdings,
                                      Map<String, String> headers) {
    return holdingEventPublisher.publishReindexHoldings(rangeId, holdings, headers);
  }

  Future<Void> publishRecordUpdated(HoldingsRecord oldHoldings, Map<String, String> headers) {
    return holdingEventPublisher.publishRecordUpdated(oldHoldings, headers);
  }

  private void publishItemEvents(List<HoldingsRecord> newHoldings,
                                 Map<String, HoldingsRecord> oldHoldings,
                                 Map<String, List<Item>> itemsBeforeUpdate,
                                 Map<String, String> headers) {
    for (var newHolding : newHoldings) {
      var holdingsId = newHolding.getId();
      var oldHolding = oldHoldings.get(holdingsId);
      var itemsBefore = itemsBeforeUpdate.get(holdingsId);

      if (oldHolding != null && itemsBefore != null) {
        itemEventPublisher.publishUpdated(oldHolding, newHolding, itemsBefore, headers);
      }
    }
  }

  private void publishHoldingsEvents(List<HoldingsRecord> newHoldings, Map<String, HoldingsRecord> oldHoldingsMap,
                                     Map<String, String> headers) {
    var oldHoldings = new ArrayList<>(oldHoldingsMap.values());
    var createdHoldings = newHoldings.stream()
      .filter(entity -> !oldHoldingsMap.containsKey(entity.getId()))
      .toList();

    var batchContext = new BatchOperationContext<>(createdHoldings, oldHoldings, true);
    holdingEventPublisher.publishRecordsCreatedOrUpdated(batchContext, headers);
  }
}
