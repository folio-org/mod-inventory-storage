package org.folio.services.consortium;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.InstanceDateType;
import org.folio.services.domainevent.DomainEvent;

public class InstanceDateTypeSynchronizationEventProcessor implements SynchronizationEventProcessor<InstanceDateType> {
  @Override
  public Future<String> process(DomainEvent<?> domainEvent, String key, SynchronizationContext synchronizationContext) {
    return null;
  }
}
