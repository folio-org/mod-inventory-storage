package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.InventoryKafkaTopic.INSTANCE;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import org.folio.persist.InstanceInternalRepository;
import org.folio.rest.jaxrs.model.Instance;

import io.vertx.core.Context;
import io.vertx.core.Future;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InstanceDomainEventPublisher extends AbstractDomainEventPublisher<Instance, Instance> {
  private static final Logger log = getLogger(InstanceDomainEventPublisher.class);

  public InstanceDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new InstanceInternalRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders,
        INSTANCE.fullTopicName(tenantId(okapiHeaders))));
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
  protected String getId(Instance instance) {
    return instance.getId();
  }
}
