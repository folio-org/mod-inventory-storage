package org.folio.services.consortium.processor;

import io.vertx.core.Future;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.helper.EntityJson;
import org.folio.rest.jaxrs.model.ServicePoint;
import org.folio.services.domainevent.model.DomainEvent;
import org.folio.services.domainevent.model.ServicePointEventType;
import org.folio.services.servicepoint.ServicePointService;

public class ServicePointSynchronizationDeleteEventProcessor
  extends ServicePointSynchronizationEventProcessor {

  private static final Logger log = LogManager.getLogger(ServicePointSynchronizationDeleteEventProcessor.class);

  public ServicePointSynchronizationDeleteEventProcessor(DomainEvent<ServicePoint> domainEvent,
                                                         ServicePointService servicePointService) {
    super(ServicePointEventType.SERVICE_POINT_DELETED, domainEvent, servicePointService);
  }

  @Override
  protected Future<String> processEvent(ServicePointService servicePointService, String servicePointId,
                                        Map<String, String> headers) {
    return servicePointService.deleteServicePoint(servicePointId, headers).map(servicePointId);
  }

  @Override
  protected boolean validateEventEntity() {
    try {
      var servicePoint = EntityJson.toJsonObject(domainEvent.getOldEntity())
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
