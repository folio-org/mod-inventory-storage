package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toMap;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.InventoryKafkaTopic.ITEM;
import static org.folio.InventoryKafkaTopic.REINDEX_RECORDS;
import static org.folio.rest.jaxrs.model.ReindexRecordsRequest.RecordType;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Logger;
import org.folio.persist.AbstractRepository;
import org.folio.persist.HoldingsRepository;
import org.folio.persist.ItemRepository;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.services.domainevent.model.ItemWithInstanceId;
import org.springframework.stereotype.Component;

@Component
public class ItemDomainEventPublisher extends AbstractDomainEventPublisher<Item, ItemWithInstanceId> {
  private static final Logger log = getLogger(ItemDomainEventPublisher.class);

  private final CommonDomainEventPublisher<Map<String, Object>> itemReindexPublisher;
  private final HoldingsRepository holdingsRepository;
  private final ItemRepository itemRepository;

  public ItemDomainEventPublisher(DomainEventPublisherFactory publisherFactory,
                                  HoldingsRepository holdingsRepository,
                                  ItemRepository itemRepository) {
    super(publisherFactory.publisher(ITEM));
    this.itemRepository = itemRepository;
    this.holdingsRepository = holdingsRepository;
    this.itemReindexPublisher = publisherFactory.reindexPublisher(REINDEX_RECORDS);
  }

  public Future<Void> publishUpdated(Item newItem, Item oldItem, HoldingsRecord newHoldings,
                                     HoldingsRecord oldHoldings, Map<String, String> headers) {
    ItemWithInstanceId oldItemWithId = new ItemWithInstanceId(oldItem, oldHoldings.getInstanceId());
    ItemWithInstanceId newItemWithId = new ItemWithInstanceId(newItem, newHoldings.getInstanceId());

    return domainEventService.publishRecordUpdated(newItem.getId(), oldItemWithId, newItemWithId,
      headers);
  }

  public Future<Void> publishUpdated(HoldingsRecord oldHoldings, HoldingsRecord newHoldings, List<Item> oldItems,
                                     Map<String, String> headers) {
    if (oldItems.isEmpty()) {
      log.info("No items were updated, skipping event sending");
      return succeededFuture();
    }

    log.info("[{}] items were updated, sending events for them", oldItems.size());

    return repository().getByIds(oldItems, Item::getId, tenantId(headers))
      .map(updatedItems -> mapOldItemsToNew(oldHoldings, newHoldings, oldItems, updatedItems.values()))
      .compose(events -> domainEventService.publishRecordsUpdated(events, headers));
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
    return holdingsRepository.getByIds(items, Item::getHoldingsRecordId, tenantId(headers))
      .map(holdings -> items.stream()
        .map(item -> pair(getInstanceId(holdings, item), item))
        .toList());
  }

  @Override
  protected ItemWithInstanceId convertDomainToEvent(String instanceId, Item item) {
    return new ItemWithInstanceId(item, instanceId);
  }

  @Override
  protected String getId(Item item) {
    return item.getId();
  }

  @Override
  protected List<Triple<String, ItemWithInstanceId, ItemWithInstanceId>> mapOldRecordsToNew(
    List<Pair<String, Item>> oldRecords, List<Pair<String, Item>> newRecords) {

    var idToOldRecordPairMap = oldRecords.stream().collect(toMap(pair -> getId(pair.getValue()), pair -> pair));

    return newRecords.stream().map(newRecordPair -> {
      var oldRecordPair = idToOldRecordPairMap.get(getId(newRecordPair.getValue()));
      return triple(newRecordPair.getValue().getId(), convertDomainToEvent(
          oldRecordPair.getKey(), oldRecordPair.getValue()),
        convertDomainToEvent(newRecordPair.getKey(), newRecordPair.getValue()));
    }).toList();
  }

  @Override
  protected Future<List<Pair<String, ItemWithInstanceId>>> convertDomainsToEvents(Collection<Item> domains,
                                                                                  Map<String, String> headers) {
    return getRecordIds(domains, headers)
      .map(pairs -> pairs.stream()
        .map(pair -> pair(pair.getValue().getId(), convertDomainToEvent(pair.getKey(), pair.getValue())))
        .toList());
  }

  private List<Triple<String, ItemWithInstanceId, ItemWithInstanceId>> mapOldItemsToNew(
    HoldingsRecord oldHoldings, HoldingsRecord newHoldings, Collection<Item> oldItems, Collection<Item> newItems) {

    return mapOldRecordsToNew(
      oldItems.stream().map(item -> pair(oldHoldings.getInstanceId(), item)).toList(),
      newItems.stream().map(item -> pair(newHoldings.getInstanceId(), item)).toList());
  }

  private String getInstanceId(Map<String, HoldingsRecord> holdings, Item item) {
    return holdings.get(item.getHoldingsRecordId()).getInstanceId();
  }
}
