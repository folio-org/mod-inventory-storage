package org.folio.services.consortium.processor;

import static io.vertx.core.Future.failedFuture;

import io.vertx.core.Future;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.helper.EntityJson;
import org.folio.rest.impl.ServicePointApi;
import org.folio.rest.jaxrs.model.ServicePoint;
import org.folio.services.domainevent.model.DomainEvent;
import org.folio.services.domainevent.model.ServicePointEventType;
import org.folio.services.servicepoint.ServicePointService;

public class ServicePointSynchronizationUpdateEventProcessor
  extends ServicePointSynchronizationEventProcessor {

  private static final Logger log = LogManager.getLogger(ServicePointSynchronizationUpdateEventProcessor.class);

  public ServicePointSynchronizationUpdateEventProcessor(DomainEvent<ServicePoint> domainEvent,
                                                         ServicePointService servicePointService) {
    super(ServicePointEventType.SERVICE_POINT_UPDATED, domainEvent, servicePointService);
  }

  @Override
  protected Future<String> processEvent(ServicePointService servicePointService, String servicePointId,
                                        Map<String, String> headers) {
    try {
      var servicepoint = EntityJson.toJsonObject(domainEvent.getNewEntity()).mapTo(ServicePoint.class);
      return servicePointService.updateServicePoint(servicePointId, servicepoint, headers)
        .map(servicePointId);
    } catch (Exception e) {
      log.warn("processEvent:: failed due to {}", e.getMessage(), e);
      return failedFuture(e);
    }
  }

  @Override
  protected boolean validateEventEntity() {
    try {
      var oldServicePoint = EntityJson.toJsonObject(domainEvent.getOldEntity())
        .mapTo(ServicePoint.class);
      var newServicePoint = EntityJson.toJsonObject(domainEvent.getNewEntity())
        .mapTo(ServicePoint.class);

      return validateServicePoints(oldServicePoint, newServicePoint);
    } catch (Exception e) {
      log.error("validateEventEntity:: failed to {}", e.getMessage(), e);
    }
    return false;
  }

  private boolean validateServicePoints(ServicePoint oldServicePoint, ServicePoint newServicePoint) {
    if (oldServicePoint == null || newServicePoint == null) {
      log.warn("validateEventEntity:: failed due to oldServicePoint {} newServicePoint {}",
        oldServicePoint, newServicePoint);
      return false;
    }
    if (newServicePoint.equals(oldServicePoint)) {
      log.warn("validateEventEntity:: old/new service points are identical");
      return false;
    }
    var validationMessage = ServicePointApi.validateServicePoint(newServicePoint);
    if (validationMessage != null) {
      log.warn("validateEventEntity:: {}", validationMessage);
      return false;
    }
    return true;
  }
}
