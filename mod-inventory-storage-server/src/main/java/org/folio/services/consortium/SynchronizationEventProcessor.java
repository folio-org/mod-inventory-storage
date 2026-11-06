package org.folio.services.consortium;

import io.vertx.core.Future;
import org.folio.services.domainevent.model.DomainEvent;

public interface SynchronizationEventProcessor {

  Future<String> process(DomainEvent<?> domainEvent, String key, SynchronizationContext synchronizationContext);
}
