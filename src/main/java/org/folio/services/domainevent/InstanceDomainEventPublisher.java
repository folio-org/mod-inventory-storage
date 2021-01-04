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

public class InstanceDomainEventPublisher {
  private final CommonDomainEventPublisher<Instance> domainEventService;

  public InstanceDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    domainEventService = new CommonDomainEventPublisher<>(context, okapiHeaders,
      INVENTORY_INSTANCE);
  }

  public Future<Void> publishInstanceUpdated(Instance oldRecord, Instance newRecord) {
    return domainEventService.publishRecordUpdated(oldRecord.getId(), oldRecord, newRecord);
  }

  public Future<Void> publishInstanceCreated(Instance newInstance) {
    return domainEventService.publishRecordCreated(newInstance.getId(), newInstance);
  }

  public Future<Void> publishInstanceRemoved(Instance oldEntity) {
    return domainEventService.publishRecordRemoved(oldEntity.getId(), oldEntity);
  }

  public Future<Void> publishInstancesRemoved(List<Instance> records) {
    final List<Pair<String, Instance>> instancesWithIdsList = records.stream()
      .map(instance -> new ImmutablePair<>(instance.getId(), instance))
      .collect(Collectors.toList());

    return domainEventService.publishRecordsRemoved(instancesWithIdsList);
  }
}
