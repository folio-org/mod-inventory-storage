package org.folio.services.domainevent;

import io.vertx.core.Context;
import io.vertx.core.Future;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.BoundWithRepository;
import org.folio.persist.HoldingsRepository;
import org.folio.rest.jaxrs.model.BoundWithPart;
import org.folio.rest.jaxrs.model.HoldingsRecord;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.folio.InventoryKafkaTopic.BOUND_WITH;
import static org.folio.kafka.services.KafkaEnvironmentProperties.environment;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

public class BoundWithDomainEventPublisher extends AbstractDomainEventPublisher<BoundWithPart, BoundWithInstanceId> {

  private final HoldingsRepository holdingsRepository;

  public BoundWithDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new BoundWithRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders,
        BOUND_WITH.fullTopicName(environment(), tenantId(okapiHeaders))));
    holdingsRepository = new HoldingsRepository(context, okapiHeaders);
  }

  @Override
  protected Future<List<Pair<String, BoundWithPart>>> getInstanceIds(Collection<BoundWithPart> boundWithParts) {
    return holdingsRepository.getById(boundWithParts, BoundWithPart::getHoldingsRecordId)
      .map(holdings -> boundWithParts.stream()
        .map(bound -> pair(getInstanceId(holdings, bound), bound))
        .collect(toList()));
  }

  private String getInstanceId(Map<String, HoldingsRecord> holdings, BoundWithPart bound) {
    return holdings.get(bound.getHoldingsRecordId()).getInstanceId();
  }

  @Override
  protected BoundWithInstanceId convertDomainToEvent(String instanceId, BoundWithPart domain) {
    return new BoundWithInstanceId(domain, instanceId);
  }

  @Override
  protected String getId(BoundWithPart record) {
    return record.getId();
  }
}
