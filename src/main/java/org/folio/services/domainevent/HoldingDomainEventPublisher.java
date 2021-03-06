package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;
import static org.folio.Environment.environmentName;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.HoldingsRepository;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.services.kafka.topic.KafkaTopic;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class HoldingDomainEventPublisher
  extends AbstractDomainEventPublisher<HoldingsRecord, HoldingsRecord> {

  public HoldingDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new HoldingsRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders,
        KafkaTopic.holdingsRecord(tenantId(okapiHeaders), environmentName())));
  }

  @Override
  protected Future<List<Pair<String, HoldingsRecord>>> getInstanceIds(
    Collection<HoldingsRecord> holdingsRecords) {

    return succeededFuture(holdingsRecords.stream()
      .map(hr -> pair(hr.getInstanceId(), hr))
      .collect(toList()));
  }

  @Override
  protected HoldingsRecord convertDomainToEvent(String instanceId, HoldingsRecord domain) {
    return domain;
  }

  @Override
  protected String getId(HoldingsRecord record) {
    return record.getId();
  }
}
