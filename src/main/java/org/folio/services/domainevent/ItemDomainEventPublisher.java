package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.singletonList;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.rest.support.ResponseUtil.isCreateSuccessResponse;
import static org.folio.rest.support.ResponseUtil.isDeleteSuccessResponse;
import static org.folio.rest.support.ResponseUtil.isUpdateSuccessResponse;
import static org.folio.services.kafka.topic.KafkaTopic.INVENTORY_ITEM;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import org.folio.services.batch.BatchOperationContext;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class ItemDomainEventPublisher {
  private static final Logger log = getLogger(ItemDomainEventPublisher.class);

  private final ItemRepository itemRepository;
  private final HoldingsRepository holdingsRepository;
  private final CommonDomainEventPublisher<ItemWithInstanceId> domainEventService;

  public ItemDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    itemRepository = new ItemRepository(context, okapiHeaders);
    holdingsRepository = new HoldingsRepository(context, okapiHeaders);
    domainEventService = new CommonDomainEventPublisher<>(context, okapiHeaders, INVENTORY_ITEM);
  }

  public Function<Response, Future<Response>> publishItemUpdated(Item oldItem) {
    return response -> {
      if (!isUpdateSuccessResponse(response)) {
        log.warn("Item update failed, skipping event publishing");
        return succeededFuture(response);
      }

      return publishItemsUpdated(singletonList(oldItem)).map(response);
    };
  }

  public Function<Response, Future<Response>> publishItemCreated(Item item) {
    return response -> {
      if (!isCreateSuccessResponse(response)) {
        log.warn("Item create failed, skipping event publishing");
        return succeededFuture(response);
      }

      return publishItemsCreated(singletonList(item)).map(response);
    };
  }

  public Function<Response, Future<Response>> publishItemsCreatedOrUpdated(
    BatchOperationContext<Item> batchOperation) {

    return response -> {
      if (!isCreateSuccessResponse(response)) {
        log.warn("Items create/update failed, skipping event publishing");
        return succeededFuture(response);
      }

      log.info("Items created {}, items updated {}",
        batchOperation.getRecordsToBeCreated().size(),
        batchOperation.getExistingRecords().size());

      return publishItemsCreated(batchOperation.getRecordsToBeCreated())
        .compose(notUsed -> publishItemsUpdated(batchOperation.getExistingRecords()))
        .map(response);
    };
  }

  public Function<Response, Future<Response>> publishItemRemoved(Item item) {
    return response -> {
      if (!isDeleteSuccessResponse(response)) {
        log.warn("Item removal failed, no event will be sent");
        return succeededFuture(response);
      }

      return publishItemsRemoved(singletonList(item)).map(response);
    };
  }

  public Future<Void> publishItemsRemoved(List<Item> items) {
    if (items.isEmpty()) {
      log.info("No records were removed, skipping event sending");
      return succeededFuture();
    }

    return fetchAndSetInstanceIdForItems(items)
      .map(this::convertDomainStatesToPairs)
      .compose(domainEventService::publishRecordsRemoved);
  }

  public Future<Void> publishItemsUpdated(HoldingsRecord hr, List<Item> oldItems) {
    if (oldItems.isEmpty()) {
      log.info("No items were updated, skipping event sending");
      return succeededFuture();
    }

    log.info("[{}] items were updated, sending events for them", oldItems.size());

    return itemRepository.getById(oldItems, Item::getId)
      .map(updatedItems -> mapOldItemsToUpdatedItems(oldItems, updatedItems))
      .map(pairs -> setInstanceIdForDomainStatePairs(Map.of(hr.getId(), hr), pairs))
      .map(this::convertDomainStatesPairsToTriple)
      .compose(domainEventService::publishRecordsUpdated);
  }

  private Future<Void> publishItemsUpdated(Collection<Item> oldItems) {
    if (oldItems.isEmpty()) {
      log.info("No items were updated, skipping event sending");
      return succeededFuture();
    }

    log.info("[{}] items were updated, sending events for them", oldItems.size());

    return itemRepository.getById(oldItems, Item::getId)
      .map(updatedItems -> mapOldItemsToUpdatedItems(oldItems, updatedItems))
      .compose(this::fetchAndSetInstanceIdForItemPairs)
      .map(this::convertDomainStatesPairsToTriple)
      .compose(domainEventService::publishRecordsUpdated);
  }

  private Future<Void> publishItemsCreated(Collection<Item> items) {
    return fetchAndSetInstanceIdForItems(items)
      .map(this::convertDomainStatesToPairs )
      .compose(domainEventService::publishRecordsCreated);
  }

  private Future<List<Pair<ItemWithInstanceId, ItemWithInstanceId>>> fetchAndSetInstanceIdForItemPairs(
    List<Pair<Item, Item>> pairs) {

    final Set<String> holdingIdsToFetch = pairs.stream()
      .flatMap(pair -> Stream.of(pair.getLeft(), pair.getRight()))
      .map(Item::getHoldingsRecordId)
      .collect(Collectors.toSet());

    return holdingsRepository.getById(holdingIdsToFetch)
      .map(holdings -> setInstanceIdForDomainStatePairs(holdings, pairs));
  }

  private Future<List<ItemWithInstanceId>> fetchAndSetInstanceIdForItems(Collection<Item> items) {
    return holdingsRepository.getById(items, Item::getHoldingsRecordId)
      .map(holdings -> setInstanceIdForDomainState(holdings, items));
  }

  private List<Pair<ItemWithInstanceId, ItemWithInstanceId>> setInstanceIdForDomainStatePairs(
    Map<String, HoldingsRecord> holdings, List<Pair<Item, Item>> pairs) {

    return pairs.stream()
      .map(pair -> {
        final var left = pair.getLeft();
        final var right = pair.getRight();

        final var instanceIdForLeft = holdings.get(left.getHoldingsRecordId()).getInstanceId();
        final var instanceIdForRight = holdings.get(right.getHoldingsRecordId()).getInstanceId();

        return new ImmutablePair<>(new ItemWithInstanceId(left, instanceIdForLeft),
          new ItemWithInstanceId(right, instanceIdForRight));
      }).collect(Collectors.toList());
  }

  private List<ItemWithInstanceId> setInstanceIdForDomainState(
    Map<String, HoldingsRecord> holdings, Collection<Item> items) {

    return items.stream()
      .map(item -> new ItemWithInstanceId(item, holdings.get(item.getHoldingsRecordId()).getInstanceId()))
      .collect(Collectors.toList());
  }

  private List<Pair<Item, Item>> mapOldItemsToUpdatedItems(
    Collection<Item> oldItems, Map<String, Item> newItems) {

    return oldItems.stream()
      .map(oldItem -> new ImmutablePair<>(oldItem, newItems.get(oldItem.getId())))
      .collect(Collectors.toList());
  }

  private List<Triple<String, ItemWithInstanceId, ItemWithInstanceId>> convertDomainStatesPairsToTriple(
    List<Pair<ItemWithInstanceId, ItemWithInstanceId>> pairs) {

    return pairs.stream()
      .map(pair -> {
        final var oldItem = pair.getLeft();
        final var newItem = pair.getRight();

        return new ImmutableTriple<>(newItem.getInstanceId(), oldItem, newItem);
      }).collect(Collectors.toList());
  }

  private List<Pair<String, ItemWithInstanceId>> convertDomainStatesToPairs(List<ItemWithInstanceId> items) {
    return items.stream()
      .map(item -> new ImmutablePair<>(item.getInstanceId(), item))
      .collect(Collectors.toList());
  }
}
