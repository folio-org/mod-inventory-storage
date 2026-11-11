package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toMap;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.InventoryKafkaTopic.ITEM;
import static org.folio.InventoryKafkaTopic.REINDEX_RECORDS;
import static org.folio.rest.jaxrs.model.ReindexRecordsRequest.RecordType;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Logger;
import org.folio.persist.AbstractRepository;
import org.folio.persist.HoldingsRepository;
import org.folio.persist.InstanceRepository;
import org.folio.persist.ItemRepository;
import org.folio.persist.entity.HoldingsInstance;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.services.domainevent.model.ItemWithInstanceId;
import org.springframework.stereotype.Component;

/**
 * Publishes item domain events. Each event carries the id and the {@code source} of the item's instance, so every
 * path that builds events resolves the instance sources first.
 */
@Component
public class ItemDomainEventPublisher extends AbstractDomainEventPublisher<Item, ItemWithInstanceId> {
  private static final Logger log = getLogger(ItemDomainEventPublisher.class);

  private final CommonDomainEventPublisher<Map<String, Object>> itemReindexPublisher;
  private final HoldingsRepository holdingsRepository;
  private final InstanceRepository instanceRepository;
  private final ItemRepository itemRepository;

  public ItemDomainEventPublisher(DomainEventPublisherFactory publisherFactory,
                                  HoldingsRepository holdingsRepository,
                                  InstanceRepository instanceRepository,
                                  ItemRepository itemRepository) {
    super(publisherFactory.publisher(ITEM));
    this.itemRepository = itemRepository;
    this.holdingsRepository = holdingsRepository;
    this.instanceRepository = instanceRepository;
    this.itemReindexPublisher = publisherFactory.reindexPublisher(REINDEX_RECORDS);
  }

  public Future<Void> publishUpdated(Item newItem, Item oldItem, HoldingsRecord newHoldings,
                                     HoldingsRecord oldHoldings, Map<String, String> headers) {
    var oldInstanceId = oldHoldings.getInstanceId();
    var newInstanceId = newHoldings.getInstanceId();

    return getInstanceSources(List.of(oldInstanceId, newInstanceId), headers)
      .compose(sources -> domainEventService.publishRecordUpdated(newItem.getId(),
        toEvent(oldItem, oldInstanceId, sources), toEvent(newItem, newInstanceId, sources), headers));
  }

  public Future<Void> publishUpdated(HoldingsRecord oldHoldings, HoldingsRecord newHoldings, List<Item> oldItems,
                                     Map<String, String> headers) {
    if (oldItems.isEmpty()) {
      log.info("No items were updated, skipping event sending");
      return succeededFuture();
    }

    log.info("[{}] items were updated, sending events for them", oldItems.size());

    var oldInstanceId = oldHoldings.getInstanceId();
    var newInstanceId = newHoldings.getInstanceId();

    return repository().getByIds(oldItems, Item::getId, tenantId(headers))
      .compose(updatedItems -> getInstanceSources(List.of(oldInstanceId, newInstanceId), headers)
        .map(sources -> mapOldItemsToNew(oldItems, oldInstanceId, updatedItems.values(), newInstanceId, sources)))
      .compose(events -> domainEventService.publishRecordsUpdated(events, headers));
  }

  @Override
  public Future<Void> publishRecordRemoved(Item removedItem, Map<String, String> headers) {
    return toEvents(List.of(removedItem), headers)
      .compose(events -> {
        var event = events.getFirst().getValue();
        return domainEventService.publishRecordRemoved(event.getInstanceId(), event, headers);
      });
  }

  public Future<Void> publishReindexItems(String key, List<Map<String, Object>> items,
                                          Map<String, String> headers) {
    if (StringUtils.isBlank(key)) {
      return succeededFuture();
    }

    return itemReindexPublisher.publishReindexRecords(key, RecordType.ITEM, items, headers);
  }

  @Override
  protected AbstractRepository<Item> repository() {
    return itemRepository;
  }

  @Override
  protected Future<List<Pair<String, Item>>> getRecordIds(Collection<Item> items,
                                                          Map<String, String> headers) {
    return getHoldingsInstances(items, headers)
      .map(instances -> items.stream()
        .map(item -> pair(getHoldingsInstance(instances, item).instanceId(), item))
        .toList());
  }

  /**
   * Not used: every path that builds item events is overridden to add the instance source, which this signature
   * cannot provide.
   */
  @Override
  protected ItemWithInstanceId convertDomainToEvent(String instanceId, Item item) {
    throw new UnsupportedOperationException("Item events are built with the instance source");
  }

  @Override
  protected String getId(Item item) {
    return item.getId();
  }

  @Override
  protected Future<List<Pair<String, ItemWithInstanceId>>> convertDomainsToEvents(Collection<Item> domains,
                                                                                  Map<String, String> headers) {
    return toEvents(domains, headers);
  }

  @Override
  protected Future<List<Triple<String, ItemWithInstanceId, ItemWithInstanceId>>> convertDomainsToEvents(
    Collection<Item> newRecords, Collection<Item> oldRecords, Map<String, String> headers) {

    var allRecords = new ArrayList<Item>(oldRecords.size() + newRecords.size());
    allRecords.addAll(oldRecords);
    allRecords.addAll(newRecords);

    return toEvents(allRecords, headers).map(events -> {
      var oldEvents = events.subList(0, oldRecords.size()).stream()
        .collect(toMap(Pair::getKey, Pair::getValue));
      return events.subList(oldRecords.size(), events.size()).stream()
        .map(newEvent -> triple(newEvent.getKey(), oldEvents.get(newEvent.getKey()), newEvent.getValue()))
        .toList();
    });
  }

  /**
   * Builds the events of the items in their order, each paired with the item id.
   */
  private Future<List<Pair<String, ItemWithInstanceId>>> toEvents(Collection<Item> items,
                                                                  Map<String, String> headers) {
    return getHoldingsInstances(items, headers)
      .map(instances -> items.stream()
        .map(item -> {
          var instance = getHoldingsInstance(instances, item);
          return pair(item.getId(), new ItemWithInstanceId(item, instance.instanceId(), instance.instanceSource()));
        })
        .toList());
  }

  private Future<Map<String, HoldingsInstance>> getHoldingsInstances(Collection<Item> items,
                                                                     Map<String, String> headers) {
    var holdingsIds = items.stream().map(Item::getHoldingsRecordId).toList();
    return holdingsRepository.getInstancesByHoldingsIds(holdingsIds, tenantId(headers))
      .onFailure(e -> log.error("Failed to resolve the instances of items, item events are not published", e));
  }

  private Future<Map<String, String>> getInstanceSources(Collection<String> instanceIds,
                                                         Map<String, String> headers) {
    return instanceRepository.getSourcesByIds(instanceIds, tenantId(headers))
      .onFailure(e -> log.error("Failed to resolve instance sources, item events are not published", e));
  }

  private List<Triple<String, ItemWithInstanceId, ItemWithInstanceId>> mapOldItemsToNew(
    Collection<Item> oldItems, String oldInstanceId, Collection<Item> newItems, String newInstanceId,
    Map<String, String> sources) {

    var oldItemsById = oldItems.stream().collect(toMap(Item::getId, item -> item));

    return newItems.stream()
      .map(newItem -> triple(newItem.getId(),
        toEvent(oldItemsById.get(newItem.getId()), oldInstanceId, sources),
        toEvent(newItem, newInstanceId, sources)))
      .toList();
  }

  private static ItemWithInstanceId toEvent(Item item, String instanceId, Map<String, String> sources) {
    return new ItemWithInstanceId(item, instanceId, sources.get(instanceId));
  }

  private static HoldingsInstance getHoldingsInstance(Map<String, HoldingsInstance> instances, Item item) {
    return instances.get(item.getHoldingsRecordId());
  }
}
