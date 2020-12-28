package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.rest.support.ResponseUtil.isCreateSuccessResponse;
import static org.folio.rest.support.ResponseUtil.isDeleteSuccessResponse;
import static org.folio.rest.support.ResponseUtil.isUpdateSuccessResponse;
import static org.folio.services.kafka.topic.KafkaTopic.ITEM_INSTANCE;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Logger;
import org.folio.persist.HoldingsRepository;
import org.folio.persist.ItemRepository;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.services.batch.BatchOperation;
import org.folio.services.item.effectivevalues.ItemWithHolding;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class ItemDomainEventService {
  private static final Logger log = getLogger(ItemDomainEventService.class);

  private final ItemRepository itemRepository;
  private final HoldingsRepository holdingsRepository;
  private final BaseDomainEventService<Item> domainEventService;

  public ItemDomainEventService(Context context, Map<String, String> okapiHeaders) {
    itemRepository = new ItemRepository(context, okapiHeaders);
    holdingsRepository = new HoldingsRepository(context, okapiHeaders);
    domainEventService = new BaseDomainEventService<>(context, okapiHeaders,
      ITEM_INSTANCE);
  }

  public Function<Response, Future<Response>> itemUpdated(String instanceId, Item oldItem) {
    return response -> {
      if (!isUpdateSuccessResponse(response)) {
        log.warn("Item update failed, skipping event publishing");
        return succeededFuture(response);
      }

      return itemRepository.getById(oldItem.getId())
        .compose(updatedItem -> domainEventService.recordUpdated(instanceId, oldItem, updatedItem))
        .map(response);
    };
  }

  public Function<Response, Future<Response>> itemCreated(ItemWithHolding itemWithHolding) {
    return response -> {
      if (!isCreateSuccessResponse(response)) {
        log.warn("Item create failed, skipping event publishing");
        return succeededFuture(response);
      }

      return domainEventService.recordCreated(itemWithHolding.getInstanceId(),
        itemWithHolding.getItem())
        .map(response);
    };
  }

  public Function<Response, Future<Response>> itemsCreatedOrUpdated(
    BatchOperation<ItemWithHolding> batchOperation) {

    return response -> {
      if (!isCreateSuccessResponse(response)) {
        log.warn("Items create/update failed, skipping event publishing");
        return succeededFuture(response);
      }

      final var createdItemsPairs = batchOperation
        .getRecordsToBeCreated().stream()
        .map(itemAndHolding -> new ImmutablePair<>(itemAndHolding.getInstanceId(),
          itemAndHolding.getItem()))
        .collect(Collectors.<Pair<String, Item>>toList());

      log.info("Items created {}, items updated {}", createdItemsPairs.size(),
        batchOperation.getExistingRecordsBeforeUpdate().size());

      return domainEventService.recordsCreated(createdItemsPairs)
        .compose(notUsed -> itemsUpdated(batchOperation.getExistingRecordsBeforeUpdate()))
        .map(response);
    };
  }

  public Function<Response, Future<Response>> itemRemoved(Item item) {
    return response -> {
      if (!isDeleteSuccessResponse(response)) {
        log.warn("Item removal failed, no event will be sent");
        return succeededFuture(response);
      }

      return holdingsRepository.getById(item.getHoldingsRecordId())
        .compose(holding -> domainEventService.recordRemoved(holding.getInstanceId(), item))
        .map(response);
    };
  }

  public Future<Void> itemsRemoved(List<Item> items) {
    if (items.isEmpty()) {
      log.info("No records were removed, skipping event sending");
      return succeededFuture();
    }

    return holdingsRepository.getById(items, Item::getHoldingsRecordId)
      .compose(holdingsMap -> {
        final var instanceIdToItemPairs = items.stream()
          .map(item -> {
            final HoldingsRecord hr = holdingsMap.get(item.getHoldingsRecordId());
            return new ImmutablePair<>(hr.getInstanceId(), item);
          }).collect(Collectors.<Pair<String, Item>>toList());

        return domainEventService.recordsRemoved(instanceIdToItemPairs);
      });
  }

  public Future<Void> itemsUpdated(HoldingsRecord holdingsRecord, List<Item> oldItems) {
    final var oldItemWithHoldings = oldItems.stream()
      .map(item -> new ItemWithHolding(item, holdingsRecord))
      .collect(Collectors.toList());

    log.info("[{}] items were updated, sending events for them", oldItems.size());

    return itemRepository.getById(oldItemWithHoldings, ItemWithHolding::getItemId)
      .map(updatedItems -> mapOldItemsToUpdatedItems(updatedItems, oldItemWithHoldings))
      .compose(domainEventService::recordsUpdated);
  }

  private Future<Void> itemsUpdated(List<ItemWithHolding> oldItems) {
    if (oldItems.isEmpty()) {
      log.info("No items were updated, skipping event sending");
      return succeededFuture();
    }

    log.info("[{}] items were updated, sending events for them", oldItems.size());

    return itemRepository.getById(oldItems, ItemWithHolding::getItemId)
      .map(updatedItems -> mapOldItemsToUpdatedItems(updatedItems, oldItems))
      .compose(domainEventService::recordsUpdated);
  }

  private List<Triple<String, Item, Item>> mapOldItemsToUpdatedItems(
    Map<String, Item> updatedItemsMap, List<ItemWithHolding> oldItems) {

    return oldItems.stream()
      .map(itemWithHolding -> {
        final String instanceId = itemWithHolding.getInstanceId();
        final Item oldItem = itemWithHolding.getItem();
        final Item newItem = updatedItemsMap.get(itemWithHolding.getItemId());

        return new ImmutableTriple<>(instanceId, oldItem, newItem);
      }).collect(Collectors.toList());
  }
}
