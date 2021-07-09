package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.logging.log4j.LogManager.getLogger;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import org.folio.persist.InstanceRepository;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.services.kafka.topic.KafkaTopic;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class InstanceDomainEventPublisher extends AbstractDomainEventPublisher<Instance, Instance> {
  private static final Logger log = getLogger(InstanceDomainEventPublisher.class);

  public InstanceDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new InstanceRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders, KafkaTopic.instance()));
  }

  public Future<Void> publishInstancesCreated(List<Instance> instances) {
    if (instances.isEmpty()) {
      log.info("No instances were created, skipping event sending");
      return succeededFuture();
    }

    log.info("[{}] instances were created, sending events for them", instances.size());

    return domainEventService.publishRecordsCreated(instances.stream()
      .map(instance -> pair(instance.getId(), instance))
      .collect(Collectors.toList()));
  }

  @Override
  protected Future<List<Pair<String, Instance>>> getInstanceIds(Collection<Instance> instances) {
    return succeededFuture(instances.stream()
      .map(instance -> pair(instance.getId(), instance))
      .collect(Collectors.toList()));
  }

  @Override
  protected Instance convertDomainToEvent(String instanceId, Instance domain) {
    return domain;
  }

  @Override
  protected String getId(Instance record) {
    return record.getId();
  }
}
