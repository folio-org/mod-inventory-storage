package org.folio.services.servicepoint;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.persist.ServicePointRepository;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.rest.jaxrs.resource.ItemStorage;
import org.folio.services.domainevent.ServicePointDomainEventPublisher;

public class ServicePointService {

  private final ServicePointRepository servicePointRepository;
  private final ServicePointDomainEventPublisher servicePointDomainEventPublisher;

  public ServicePointService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.servicePointRepository = new ServicePointRepository(vertxContext, okapiHeaders);
    this.servicePointDomainEventPublisher = new ServicePointDomainEventPublisher(vertxContext, okapiHeaders);
  }

  public Future<Response> updateServicePoint(String servicePointId, Servicepoint entity) {
    entity.setId(servicePointId);

    return servicePointRepository.getById(servicePointId)
      .compose(servicePoint -> {
        if (servicePoint != null) {
          return servicePointRepository.update(servicePointId, entity)
            .map(rowSet -> servicePoint);
        }
        return Future.failedFuture(new NotFoundException("ServicePoint was not found"));
      })
      .onSuccess(oldServicePoint -> servicePointDomainEventPublisher
        .publishUpdated(oldServicePoint, entity))
      .<Response>map(x -> ItemStorage.PutItemStorageItemsByItemIdResponse.respond204());
  }
}
