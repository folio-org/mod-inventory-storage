package org.folio.services.consortium.handler;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.consortium.processor.ServicePointSynchronizationCreateEventProcessor;
import org.folio.services.consortium.processor.ServicePointSynchronizationEventProcessor;
import org.folio.services.domainevent.DomainEvent;

public class ServicePointSynchronizationCreateHandler extends ServicePointSynchronizationHandler {

  public ServicePointSynchronizationCreateHandler(ConsortiumDataCache consortiumDataCache,
    HttpClient httpClient, Vertx vertx) {

    super(consortiumDataCache, httpClient, vertx);
  }

  @Override
  protected ServicePointSynchronizationEventProcessor getServicePointSynchronizationProcessor(
    DomainEvent<Servicepoint> domainEvent) {

    return new ServicePointSynchronizationCreateEventProcessor(domainEvent);
  }
}
