package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.folio.persist.HoldingsRepository;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.services.kafka.topic.KafkaTopic;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class HoldingDomainEventPublisher extends AbstractDomainEventPublisher<HoldingsRecord,
  HoldingsRecord> {

  public HoldingDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new HoldingsRepository(context, okapiHeaders),
    new CommonDomainEventPublisher<>(context, okapiHeaders, KafkaTopic.holdingsRecord()));
  }

  @Override
  protected Future<List<Pair<String, HoldingsRecord>>> toInstanceIdEventTypePairs(
    Collection<HoldingsRecord> records) {

    final List<Pair<String, HoldingsRecord>> pairs = records.stream()
      .map(hr -> new ImmutablePair<>(hr.getInstanceId(), hr))
      .collect(Collectors.toList());

    return succeededFuture(pairs);
  }

  @Override
  protected Future<List<Triple<String, HoldingsRecord, HoldingsRecord>>> toInstanceIdEventTypeTriples(
    Collection<Pair<HoldingsRecord, HoldingsRecord>> oldToNewRecordPairs) {

    final List<Triple<String, HoldingsRecord, HoldingsRecord>> triples =
      oldToNewRecordPairs.stream()
        .map(pair -> {
          final HoldingsRecord oldHr = pair.getLeft();
          final HoldingsRecord newHr = pair.getRight();

          return new ImmutableTriple<>(newHr.getInstanceId(), oldHr, newHr);
        }).collect(Collectors.toList());

    return succeededFuture(triples);
  }

  @Override
  protected String getId(HoldingsRecord record) {
    return record.getId();
  }
}
