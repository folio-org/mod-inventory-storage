package org.folio.services.consortium.processor;

import static io.vertx.core.Future.failedFuture;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.impl.ServicePointApi;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.domainevent.DomainEvent;
import org.folio.services.domainevent.ServicePointEventType;
import org.folio.services.servicepoint.ServicePointService;

public class ServicePointSynchronizationUpdateEventProcessor
  extends ServicePointSynchronizationEventProcessor {

  private static final Logger log = LogManager.getLogger(
    ServicePointSynchronizationUpdateEventProcessor.class);

  public ServicePointSynchronizationUpdateEventProcessor(DomainEvent<Servicepoint> domainEvent) {
    super(ServicePointEventType.SERVICE_POINT_UPDATED, domainEvent);
  }

  @Override
  protected Future<String> processEvent(ServicePointService servicePointService, String servicePointId) {
    try {
      Servicepoint servicepoint = PostgresClient.pojo2JsonObject(domainEvent.getNewEntity())
        .mapTo(Servicepoint.class);
      return servicePointService.updateServicePoint(servicePointId, servicepoint)
        .map(servicePointId);
    } catch (Exception e) {
      log.warn("processEvent:: failed due to {}", e.getMessage(), e);
      return failedFuture(e);
    }
  }

  @Override
  protected boolean validateEventEntity() {
    try {
      var oldServicePoint = PostgresClient.pojo2JsonObject(domainEvent.getOldEntity())
        .mapTo(Servicepoint.class);
      Servicepoint newServicePoint = PostgresClient.pojo2JsonObject(domainEvent.getNewEntity())
        .mapTo(Servicepoint.class);

      if (oldServicePoint == null || newServicePoint == null) {
        log.warn("validateEventEntity:: failed due to oldServicePoint {} newServicePoint {}",
          oldServicePoint, newServicePoint);
        return false;
      }
      if (newServicePoint.equals(oldServicePoint)) {
        log.warn("validateEventEntity:: old/new service points are identical");
        return false;
      }
      String validationMessage = ServicePointApi.validateServicePoint(newServicePoint);
      if (validationMessage != null) {
        log.warn("validateEventEntity:: {}", validationMessage);
        return false;
      }
      return true;
    } catch (Exception e) {
      log.error("validateEventEntity:: failed to {}", e.getMessage(), e);
    }
    return false;
  }
}
