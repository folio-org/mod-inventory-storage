package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.HOLDINGS_RECORD;
import static org.folio.InventoryKafkaTopic.REINDEX_RECORDS;
import static org.folio.rest.jaxrs.model.ReindexRecordsRequest.RecordType;

import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.AbstractRepository;
import org.folio.persist.HoldingsRepository;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.springframework.stereotype.Component;

@Component
public class HoldingDomainEventPublisher extends AbstractDomainEventPublisher<HoldingsRecord, HoldingsRecord> {

  private final CommonDomainEventPublisher<Map<String, Object>> holdingsReindexPublisher;
  private final HoldingsRepository holdingsRepository;

  public HoldingDomainEventPublisher(DomainEventPublisherFactory publisherFactory,
                                     HoldingsRepository holdingsRepository) {
    super(publisherFactory.publisher(HOLDINGS_RECORD));
    this.holdingsRepository = holdingsRepository;
    this.holdingsReindexPublisher = publisherFactory.reindexPublisher(REINDEX_RECORDS);
  }

  public Future<Void> publishReindexHoldings(String key, List<Map<String, Object>> holdings,
                                             Map<String, String> headers) {
    if (StringUtils.isBlank(key)) {
      return succeededFuture();
    }

    return holdingsReindexPublisher.publishReindexRecords(key, RecordType.HOLDINGS, holdings, headers);
  }

  @Override
  protected AbstractRepository<HoldingsRecord> repository() {
    return holdingsRepository;
  }

  @Override
  protected Future<List<Pair<String, HoldingsRecord>>> getRecordIds(Collection<HoldingsRecord> holdingsRecords,
                                                                    Map<String, String> headers) {

    return succeededFuture(holdingsRecords.stream()
      .map(hr -> pair(hr.getId(), hr))
      .toList());
  }

  @Override
  protected HoldingsRecord convertDomainToEvent(String instanceId, HoldingsRecord domain) {
    return domain;
  }

  @Override
  protected String getId(HoldingsRecord holdings) {
    return holdings.getId();
  }
}
