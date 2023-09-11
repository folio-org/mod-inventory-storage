package org.folio.services.domainevent;

import static org.folio.InventoryKafkaTopic.SERVICE_POINT;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import org.folio.rest.jaxrs.model.Servicepoint;

public class ServicePointDomainEventPublisher extends CommonDomainEventPublisher<Servicepoint> {

  public ServicePointDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(context, okapiHeaders, SERVICE_POINT.fullTopicName(tenantId(okapiHeaders)));
  }

  public Future<Void> publishUpdated(Servicepoint servicePoint, Servicepoint updatedServicePoint) {
    return publishRecordUpdated(servicePoint.getId(), servicePoint, updatedServicePoint);
  }

  public Future<Void> publishDeleted(Servicepoint servicePoint) {
    return publishRecordRemoved(servicePoint.getId(), servicePoint);
  }
}
