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

public class ServicePointSynchronizationCreateEventProcessor
  extends ServicePointSynchronizationEventProcessor {

  private static final Logger log = LogManager.getLogger(ServicePointSynchronizationCreateEventProcessor.class);

  public ServicePointSynchronizationCreateEventProcessor(DomainEvent<ServicePoint> domainEvent,
                                                         ServicePointService servicePointService) {
    super(ServicePointEventType.SERVICE_POINT_CREATED, domainEvent, servicePointService);
  }

  @Override
  protected Future<String> processEvent(ServicePointService servicePointService, String servicePointId,
                                        Map<String, String> headers) {
    try {
      var servicePoint = EntityJson.toJsonObject(domainEvent.getNewEntity()).mapTo(ServicePoint.class);

      return servicePointService.createServicePoint(servicePointId, servicePoint, headers)
        .map(servicePointId);
    } catch (Exception e) {
      log.error("processEvent:: failed due to {}", e.getMessage(), e);
      return failedFuture(e);
    }
  }

  @Override
  protected boolean validateEventEntity() {
    try {
      ServicePoint servicePoint = EntityJson.toJsonObject(domainEvent.getNewEntity())
        .mapTo(ServicePoint.class);
      if (servicePoint == null) {
        log.warn("validateEventEntity:: failed to find new service point entity");
        return false;
      }
      String validationMessage = ServicePointApi.validateServicePoint(servicePoint);
      if (validationMessage != null) {
        log.warn("validateEventEntity:: {}", validationMessage);
        return false;
      }
      return true;
    } catch (Exception e) {
      log.error("validateEventEntity:: failed due to {}", e.getMessage(), e);
    }
    return false;
  }
}
