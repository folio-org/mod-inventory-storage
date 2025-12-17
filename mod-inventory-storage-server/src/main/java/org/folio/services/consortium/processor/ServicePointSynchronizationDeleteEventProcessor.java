package org.folio.services.consortium.processor;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.ServicePoint;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.domainevent.DomainEvent;
import org.folio.services.domainevent.ServicePointEventType;
import org.folio.services.servicepoint.ServicePointService;

public class ServicePointSynchronizationDeleteEventProcessor
  extends ServicePointSynchronizationEventProcessor {

  private static final Logger log = LogManager.getLogger(
    ServicePointSynchronizationDeleteEventProcessor.class);

  public ServicePointSynchronizationDeleteEventProcessor(DomainEvent<ServicePoint> domainEvent) {
    super(ServicePointEventType.SERVICE_POINT_DELETED, domainEvent);
  }

  @Override
  protected Future<String> processEvent(ServicePointService servicePointService, String servicePointId) {
    return servicePointService.deleteServicePoint(servicePointId).map(servicePointId);
  }

  @Override
  protected boolean validateEventEntity() {
    try {
      var servicePoint = PostgresClient.pojo2JsonObject(domainEvent.getOldEntity())
        .mapTo(ServicePoint.class);
      if (servicePoint == null) {
        log.warn("validateEventEntity:: service point is null");
        return false;
      }
      return true;
    } catch (Exception e) {
      log.error("validateEventEntity:: failed to {}", e.getMessage(), e);
    }
    return false;
  }
}
