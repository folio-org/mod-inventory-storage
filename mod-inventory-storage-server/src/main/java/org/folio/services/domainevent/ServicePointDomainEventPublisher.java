package org.folio.services.domainevent;

import static org.folio.InventoryKafkaTopic.SERVICE_POINT;

import io.vertx.core.Future;
import java.util.Map;
import org.folio.rest.jaxrs.model.ServicePoint;
import org.springframework.stereotype.Component;

@Component
public class ServicePointDomainEventPublisher {

  private final CommonDomainEventPublisher<ServicePoint> publisher;

  public ServicePointDomainEventPublisher(DomainEventPublisherFactory publisherFactory) {
    this.publisher = publisherFactory.publisher(SERVICE_POINT);
  }

  public Future<Void> publishUpdated(ServicePoint servicePoint, ServicePoint updatedServicePoint,
                                     Map<String, String> headers) {
    return publisher.publishRecordUpdated(servicePoint.getId(), servicePoint, updatedServicePoint, headers);
  }

  public Future<Void> publishDeleted(ServicePoint servicePoint, Map<String, String> headers) {
    return publisher.publishRecordRemoved(servicePoint.getId(), servicePoint, headers);
  }

  public Future<Void> publishCreated(ServicePoint servicePoint, Map<String, String> headers) {
    return publisher.publishRecordCreated(servicePoint.getId(), servicePoint, headers);
  }
}
