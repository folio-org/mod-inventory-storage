package org.folio.services.consortium.handler;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import org.folio.rest.jaxrs.model.ServicePoint;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.consortium.processor.ServicePointSynchronizationDeleteEventProcessor;
import org.folio.services.consortium.processor.ServicePointSynchronizationEventProcessor;
import org.folio.services.domainevent.model.DomainEvent;
import org.folio.services.servicepoint.ServicePointService;

public class ServicePointSynchronizationDeleteHandler extends ServicePointSynchronizationHandler {

  public ServicePointSynchronizationDeleteHandler(ConsortiumDataCache consortiumDataCache,
                                                  HttpClient httpClient, Vertx vertx,
                                                  ServicePointService servicePointService) {
    super(consortiumDataCache, httpClient, vertx, servicePointService);
  }

  @Override
  protected ServicePointSynchronizationEventProcessor getServicePointSynchronizationProcessor(
    DomainEvent<ServicePoint> domainEvent) {

    return new ServicePointSynchronizationDeleteEventProcessor(domainEvent, servicePointService);
  }
}
