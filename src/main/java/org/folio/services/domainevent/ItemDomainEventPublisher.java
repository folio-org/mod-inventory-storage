package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.logging.log4j.LogManager.getLogger;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Logger;
import org.folio.persist.HoldingsRepository;
import org.folio.persist.ItemRepository;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.services.kafka.topic.KafkaTopic;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class ItemDomainEventPublisher extends AbstractDomainEventPublisher<Item, ItemWithInstanceId> {
  private static final Logger log = getLogger(ItemDomainEventPublisher.class);

  private final HoldingsRepository holdingsRepository;

  public ItemDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new ItemRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders, KafkaTopic.item()));
    holdingsRepository = new HoldingsRepository(context, okapiHeaders);
  }

  public Future<Void> publishUpdated(HoldingsRecord hr, List<Item> oldItems) {
    if (oldItems.isEmpty()) {
      log.info("No items were updated, skipping event sending");
      return succeededFuture();
    }

    log.info("[{}] items were updated, sending events for them", oldItems.size());

    return repository.getById(oldItems, Item::getId)
      .map(updatedItems -> mapOldRecordsToUpdatedRecords(oldItems, updatedItems))
      .map(pairs -> convertItemsToDomainStateTriples(Map.of(hr.getId(), hr), pairs))
      .compose(domainEventService::publishRecordsUpdated);
  }

  @Override
  protected Future<List<Pair<String, ItemWithInstanceId>>> toInstanceIdEventTypePairs(
    Collection<Item> records) {

    return holdingsRepository.getById(records, Item::getHoldingsRecordId)
      .map(holdings -> convertItemsToDomainStatePairs(holdings, records));
  }

  @Override
  protected Future<List<Triple<String, ItemWithInstanceId, ItemWithInstanceId>>> toInstanceIdEventTypeTriples(
    Collection<Pair<Item, Item>> oldToNewRecordPairs) {

    final Set<String> holdingIdsToFetch = oldToNewRecordPairs.stream()
      .flatMap(pair -> Stream.of(pair.getLeft(), pair.getRight()))
      .map(Item::getHoldingsRecordId)
      .collect(Collectors.toSet());

    return holdingsRepository.getById(holdingIdsToFetch)
      .map(holdings -> convertItemsToDomainStateTriples(holdings, oldToNewRecordPairs));
  }

  @Override
  protected String getId(Item record) {
    return record.getId();
  }

  private List<Triple<String, ItemWithInstanceId, ItemWithInstanceId>> convertItemsToDomainStateTriples(
    Map<String, HoldingsRecord> holdings, Collection<Pair<Item, Item>> pairs) {

    return pairs.stream()
      .map(pair -> {
        final var left = pair.getLeft();
        final var right = pair.getRight();

        final var instanceIdForLeft = holdings.get(left.getHoldingsRecordId()).getInstanceId();
        final var instanceIdForRight = holdings.get(right.getHoldingsRecordId()).getInstanceId();

        return new ImmutableTriple<>(instanceIdForRight,
          new ItemWithInstanceId(left, instanceIdForLeft),
          new ItemWithInstanceId(right, instanceIdForRight));
      }).collect(Collectors.toList());
  }

  private List<Pair<String, ItemWithInstanceId>> convertItemsToDomainStatePairs(
    Map<String, HoldingsRecord> holdings, Collection<Item> items) {

    return items.stream()
      .map(item -> new ItemWithInstanceId(item, holdings.get(item.getHoldingsRecordId()).getInstanceId()))
      .map(item -> new ImmutablePair<>(item.getInstanceId(), item))
      .collect(Collectors.toList());
  }
}
