package org.folio.services.domainevent;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.InventoryKafkaTopic.HOLDINGS_RECORD;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import org.folio.persist.HoldingsRepository;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.PublishReindexRecords;

public class HoldingDomainEventPublisher
  extends AbstractDomainEventPublisher<HoldingsRecord, HoldingsRecord> {
  private static final Logger log = getLogger(HoldingDomainEventPublisher.class);

  public HoldingDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new HoldingsRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders,
        HOLDINGS_RECORD.fullTopicName(tenantId(okapiHeaders))));
  }

  public Future<Void> publishReindexHoldings(String key, List<HoldingsRecord> holdings) {
    if (CollectionUtils.isEmpty(holdings) || StringUtils.isBlank(key)) {
      return succeededFuture();
    }

    try {
      return domainEventService.publishReindexRecords(key, PublishReindexRecords.RecordType.HOLDING, holdings);
    } catch (JsonProcessingException e) {
      log.error("Publishing {} holdings has failed: {}",
        holdings.size(), e.getMessage());
      return failedFuture(e);
    }
  }

  @Override
  protected Future<List<Pair<String, HoldingsRecord>>> getRecordIds(
    Collection<HoldingsRecord> holdingsRecords) {

    return succeededFuture(holdingsRecords.stream()
      .map(hr -> pair(hr.getInstanceId(), hr))
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
