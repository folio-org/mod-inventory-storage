package org.folio.services.domainevent;

import static org.folio.services.kafka.topic.KafkaTopic.INVENTORY_INSTANCE;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.jaxrs.model.Instance;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class InstanceDomainEventService {
  private final CommonDomainEventService<Instance> domainEventService;

  public InstanceDomainEventService(Context context, Map<String, String> okapiHeaders) {
    domainEventService = new CommonDomainEventService<>(context, okapiHeaders,
      INVENTORY_INSTANCE);
  }

  public Future<Void> instanceUpdated(Instance oldRecord, Instance newRecord) {
    return domainEventService.recordUpdated(oldRecord.getId(), oldRecord, newRecord);
  }

  public Future<Void> instanceCreated(Instance newInstance) {
    return domainEventService.recordCreated(newInstance.getId(), newInstance);
  }

  public Future<Void> instanceRemoved(Instance oldEntity) {
    return domainEventService.recordRemoved(oldEntity.getId(), oldEntity);
  }

  public Future<Void> instancesRemoved(List<Instance> records) {
    final List<Pair<String, Instance>> instancesWithIdsList = records.stream()
      .map(instance -> new ImmutablePair<>(instance.getId(), instance))
      .collect(Collectors.toList());

    return domainEventService.recordsRemoved(instancesWithIdsList);
  }
}
