package org.folio.services.consortium.processor;

import static io.vertx.core.Future.failedFuture;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.impl.ServicePointApi;
import org.folio.rest.jaxrs.model.ServicePoint;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.domainevent.DomainEvent;
import org.folio.services.domainevent.ServicePointEventType;
import org.folio.services.servicepoint.ServicePointService;

public class ServicePointSynchronizationCreateEventProcessor
  extends ServicePointSynchronizationEventProcessor {

  private static final Logger log = LogManager.getLogger(
    ServicePointSynchronizationCreateEventProcessor.class);

  public ServicePointSynchronizationCreateEventProcessor(DomainEvent<ServicePoint> domainEvent) {
    super(ServicePointEventType.SERVICE_POINT_CREATED, domainEvent);
  }

  @Override
  protected Future<String> processEvent(ServicePointService servicePointService, String servicePointId) {
    try {
      var servicePoint = PostgresClient.pojo2JsonObject(domainEvent.getNewEntity()).mapTo(ServicePoint.class);

      return servicePointService.createServicePoint(servicePointId, servicePoint)
        .map(servicePointId);
    } catch (Exception e) {
      log.error("processEvent:: failed due to {}", e.getMessage(), e);
      return failedFuture(e);
    }
  }

  @Override
  protected boolean validateEventEntity() {
    try {
      ServicePoint servicePoint = PostgresClient.pojo2JsonObject(domainEvent.getNewEntity())
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
