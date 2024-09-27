package org.folio.services.consortium.handler;

import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.consortium.processor.ServicePointSynchronizationEventProcessor;
import org.folio.services.consortium.processor.ServicePointSynchronizationUpdateEventProcessor;
import org.folio.services.domainevent.DomainEvent;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;

public class ServicePointSynchronizationUpdateHandler extends ServicePointSynchronizationHandler {

  public ServicePointSynchronizationUpdateHandler(ConsortiumDataCache consortiumDataCache,
    HttpClient httpClient, Vertx vertx) {

    super(consortiumDataCache, httpClient, vertx);
  }

  @Override
  protected ServicePointSynchronizationEventProcessor getServicePointSynchronizationProcessor(
    DomainEvent<Servicepoint> domainEvent) {

    return new ServicePointSynchronizationUpdateEventProcessor(domainEvent);
  }

}
