package org.folio.services.consortium.processor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.domainevent.DomainEvent;
import org.folio.services.domainevent.ServicePointEventType;
import org.folio.services.servicepoint.ServicePointService;

import io.vertx.core.Future;
import lombok.SneakyThrows;

public class ServicePointSynchronizationCreateEventProcessor
  extends ServicePointSynchronizationEventProcessor {

  private static final Logger LOG = LogManager.getLogger(
    ServicePointSynchronizationCreateEventProcessor.class);

  public ServicePointSynchronizationCreateEventProcessor(DomainEvent<Servicepoint> domainEvent) {
    super(ServicePointEventType.INVENTORY_SERVICE_POINT_CREATED, domainEvent);
  }

  @SneakyThrows
  @Override
  protected Future<?> processEvent(ServicePointService servicePointService, String servicePointId) {
    var servicePoint = PostgresClient.pojo2JsonObject(domainEvent.getNewEntity())
      .mapTo(Servicepoint.class);
    return servicePointService.createServicePoint(servicePointId, servicePoint);
  }

  @SneakyThrows
  @Override
  protected boolean validateEventEntity() {
    var servicePoint = PostgresClient.pojo2JsonObject(domainEvent.getNewEntity())
      .mapTo(Servicepoint.class);
    if (servicePoint == null) {
      LOG.warn("validateEventEntity:: failed to find new service point entity");
      return false;
    }
    return true;
  }
}
