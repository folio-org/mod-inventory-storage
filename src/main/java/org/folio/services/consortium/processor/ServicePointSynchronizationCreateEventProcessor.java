package org.folio.services.consortium.processor;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.impl.ServicePointApi;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.domainevent.DomainEvent;
import org.folio.services.domainevent.ServicePointEventType;
import org.folio.services.servicepoint.ServicePointService;

public class ServicePointSynchronizationCreateEventProcessor
  extends ServicePointSynchronizationEventProcessor {

  private static final Logger log = LogManager.getLogger(
    ServicePointSynchronizationCreateEventProcessor.class);

  public ServicePointSynchronizationCreateEventProcessor(DomainEvent<Servicepoint> domainEvent) {
    super(ServicePointEventType.SERVICE_POINT_CREATED, domainEvent);
  }

  @Override
  protected Future<String> processEvent(ServicePointService servicePointService, String servicePointId) {
    try {
      Servicepoint servicePoint = PostgresClient.pojo2JsonObject(domainEvent.getNewEntity())
        .mapTo(Servicepoint.class);

      return servicePointService.createServicePoint(servicePointId, servicePoint)
        .map(servicePointId);
    } catch (Exception e) {
      log.error("processEvent:: failed due to {}", e.getMessage(), e);
      return Future.failedFuture(e);
    }
  }

  @Override
  protected boolean validateEventEntity() {
    try {
      Servicepoint servicePoint = PostgresClient.pojo2JsonObject(domainEvent.getNewEntity())
        .mapTo(Servicepoint.class);
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
