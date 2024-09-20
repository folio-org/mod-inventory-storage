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

public class ServicePointSynchronizationUpdateEventProcessor
  extends ServicePointSynchronizationEventProcessor {

  private static final Logger LOG = LogManager.getLogger(
    ServicePointSynchronizationUpdateEventProcessor.class);

  public ServicePointSynchronizationUpdateEventProcessor(DomainEvent<Servicepoint> domainEvent) {
    super(ServicePointEventType.INVENTORY_SERVICE_POINT_UPDATED, domainEvent);
  }

  @SneakyThrows
  @Override
  protected Future<?> processEvent(ServicePointService servicePointService, String servicePointId) {
    var servicepoint = PostgresClient.pojo2JsonObject(domainEvent.getNewEntity()).mapTo(
      Servicepoint.class);
    return servicePointService.updateServicePoint(servicePointId, servicepoint);
  }

  @SneakyThrows
  @Override
  protected boolean validateEventEntity() {
    var oldServicePoint = domainEvent.getOldEntity();
    var newServicePoint = PostgresClient.pojo2JsonObject(domainEvent.getNewEntity()).mapTo(
      Servicepoint.class);

    if (oldServicePoint == null || newServicePoint == null) {
      LOG.warn("validateEventEntity:: failed due to oldServicePoint {} newServicePoint {}",
        oldServicePoint, newServicePoint);
      return false;
    }
    return true;
  }
}
