package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.HOLDINGS_RECORD;
import static org.folio.InventoryKafkaTopic.REINDEX_RECORDS;
import static org.folio.rest.jaxrs.model.PublishReindexRecordsRequest.RecordType;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.utils.Environment.getKafkaProducerMaxRequestSize;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.HoldingsRepository;
import org.folio.rest.jaxrs.model.HoldingsRecord;

public class HoldingDomainEventPublisher
  extends AbstractDomainEventPublisher<HoldingsRecord, HoldingsRecord> {

  private final CommonDomainEventPublisher<Map<String, Object>> holdingsReindexPublisher;

  public HoldingDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new HoldingsRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders,
        HOLDINGS_RECORD.fullTopicName(tenantId(okapiHeaders))));
    holdingsReindexPublisher = new CommonDomainEventPublisher<>(context, okapiHeaders,
      REINDEX_RECORDS.fullTopicName(tenantId(okapiHeaders)), getKafkaProducerMaxRequestSize());
  }

  public Future<Void> publishReindexHoldings(String key, List<Map<String, Object>> holdings) {
    if (StringUtils.isBlank(key)) {
      return succeededFuture();
    }

    return holdingsReindexPublisher.publishReindexRecords(key, RecordType.HOLDINGS, holdings);
  }

  @Override
  protected Future<List<Pair<String, HoldingsRecord>>> getRecordIds(
    Collection<HoldingsRecord> holdingsRecords) {

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
