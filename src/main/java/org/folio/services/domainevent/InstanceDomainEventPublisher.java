package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.services.kafka.topic.KafkaTopic.INVENTORY_INSTANCE;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Logger;
import org.folio.persist.InstanceRepository;
import org.folio.rest.jaxrs.model.Instance;

public class InstanceDomainEventPublisher extends AbstractDomainEventPublisher<Instance, Instance> {
  private static final Logger log = getLogger(InstanceDomainEventPublisher.class);

  public InstanceDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new InstanceRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders, INVENTORY_INSTANCE));
  }

  public Future<Void> publishInstancesCreated(Collection<Instance> instances) {
    if (instances.isEmpty()) {
      log.info("No instances were created, skipping event sending");
      return succeededFuture();
    }

    log.info("[{}] instances were created, sending events for them", instances.size());

    return domainEventService.publishRecordsCreated(instances.stream()
      .map(instance -> pair(instance.getId(), instance))
      .collect(toList()));
  }

  @Override
  protected Future<List<Pair<String, Instance>>> toInstanceIdEventTypePairs(Collection<Instance> records) {
    return succeededFuture(records.stream()
      .map(instance -> pair(instance.getId(), instance))
      .collect(toList()));
  }

  @Override
  protected Future<List<Triple<String, Instance, Instance>>> toInstanceIdEventTypeTriples(
    Collection<Pair<Instance, Instance>> oldToNewRecordPairs) {

    return succeededFuture(oldToNewRecordPairs.stream()
      .map(pair -> triple(pair.getLeft().getId(), pair.getLeft(), pair.getRight()))
      .collect(toList()));
  }

  @Override
  protected String getId(Instance record) {
    return record.getId();
  }
}
