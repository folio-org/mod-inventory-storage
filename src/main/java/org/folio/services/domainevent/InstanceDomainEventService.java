package org.folio.services.domainevent;


import static io.vertx.core.logging.LoggerFactory.getLogger;
import static java.lang.String.format;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.services.domainevent.DomainEvent.createEvent;
import static org.folio.services.domainevent.DomainEvent.deleteEvent;
import static org.folio.services.domainevent.DomainEvent.updateEvent;
import static org.folio.services.kafka.KafkaProducerServiceFactory.getKafkaProducerService;
import static org.folio.services.kafka.topic.KafkaTopic.INVENTORY_INSTANCE;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.folio.rest.jaxrs.model.Instance;
import org.folio.services.kafka.KafkaProducerService;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;

public class InstanceDomainEventService {
  private static final Logger log = getLogger(InstanceDomainEventService.class);

  private final KafkaProducerService kafkaProducerService;
  private final String tenant;

  public InstanceDomainEventService(Vertx vertx, String tenant) {
    this.kafkaProducerService = getKafkaProducerService(vertx);
    this.tenant = tenant;
  }

  public static InstanceDomainEventService createInstanceEventService(
    Context context, Map<String, String> okapiHeaders) {

    return new InstanceDomainEventService(context.owner(), tenantId(okapiHeaders));
  }

  public void instanceUpdated(Instance oldInstance, Instance newInstance) {
    final DomainEvent domainEvent = updateEvent(oldInstance, newInstance, tenant);

    sendMessageAsync(oldInstance.getId(), domainEvent);
  }

  public void instanceCreated(Instance newInstance) {
    final DomainEvent domainEvent = createEvent(newInstance, tenant);

    sendMessageAsync(newInstance.getId(), domainEvent);
  }

  public void instancesCreated(List<Instance> newInstances) {
    newInstances.forEach(this::instanceCreated);
  }

  public void instanceDeleted(Instance oldInstance) {
    final DomainEvent domainEvent = deleteEvent(oldInstance, tenant);

    sendMessageAsync(oldInstance.getId(), domainEvent);
  }

  public void instancesDeleted(Collection<Instance> oldInstances) {
    oldInstances.forEach(this::instanceDeleted);
  }

  private void sendMessageAsync(String instanceId, DomainEvent domainEvent) {
    try {
      log.debug(format("Sending domain event for instance [%s], payload [%s]",
        instanceId, domainEvent));

      kafkaProducerService.sendMessage(instanceId, domainEvent, INVENTORY_INSTANCE)
        .whenComplete((notUsed, error) -> {
          if (error != null) {
            log.error(format("Unable to send domain event for instance [%s], payload - [%s]",
              instanceId, domainEvent), error);
          }
        });
    } catch (Exception ex) {
      log.error(format("Unable to send domain event for instance [%s], payload - [%s]",
        instanceId, domainEvent), ex);
    }
  }
}
