package org.folio.services.domainevent;

import static org.folio.InventoryKafkaTopic.SERVICE_POINT;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import org.folio.rest.jaxrs.model.Servicepoint;

public class ServicePointDomainEventPublisher {
  private final CommonDomainEventPublisher<Servicepoint> domainEventService;

  public ServicePointDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    domainEventService = new CommonDomainEventPublisher<>(context, okapiHeaders,
      SERVICE_POINT.fullTopicName(tenantId(okapiHeaders)));
  }

  public Future<Void> publishUpdated(Servicepoint servicePoint, Servicepoint updatedServicePoint) {
    return domainEventService.publishRecordUpdated(servicePoint.getId(), servicePoint, updatedServicePoint);
  }
}
