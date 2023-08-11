package org.folio.services.servicepoint;

import static io.vertx.core.Future.succeededFuture;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.persist.ServicePointRepository;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.rest.jaxrs.resource.ItemStorage;
import org.folio.services.domainevent.ServicePointDomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class ServicePointService {

  private static final Logger log = LoggerFactory.getLogger(ServicePointService.class);
  private final ServicePointRepository servicePointRepository;
  private final ServicePointDomainEventPublisher servicePointDomainEventPublisher;

  public ServicePointService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.servicePointRepository = new ServicePointRepository(vertxContext, okapiHeaders);
    this.servicePointDomainEventPublisher = new ServicePointDomainEventPublisher(vertxContext, okapiHeaders);
  }

  public Future<Response> updateServicePoint(String servicePointId, Servicepoint entity) {
    log.debug("updateServicePoint:: parameters servicePointId: {}, entity: "
      + "Servicepoint(id={}, name={})", servicePointId, entity.getId(), entity.getName());
    entity.setId(servicePointId);

    return servicePointRepository.getById(servicePointId)
      .compose(servicePoint -> {
        if (servicePoint != null) {
          log.info("updateServicePoint:: servicePoint is found");
          return servicePointRepository.update(servicePointId, entity)
            .map(rowSet -> servicePoint);
        }
        log.warn("updateServicePoint:: servicePoint was not found");
        return Future.failedFuture(new NotFoundException("ServicePoint was not found"));
      })
      .onSuccess(oldServicePoint -> servicePointDomainEventPublisher
        .publishUpdated(oldServicePoint, entity))
      .map(x -> ItemStorage.PutItemStorageItemsByItemIdResponse.respond204());
  }

  public Future<Boolean> deleteServicePoint(String servicePointId) {
    log.debug("deleteServicePoint:: parameters servicePointId: {}", servicePointId);

    return servicePointRepository.getById(servicePointId)
      .compose(this::deleteServicePoint);
  }

  private Future<Boolean> deleteServicePoint(Servicepoint servicePoint) {
    if (servicePoint == null) {
      log.error("deleteServicePoint:: service point was not found");
      return succeededFuture(false);
    }

    String servicePointId = servicePoint.getId();
    log.debug("deleteServicePoint:: deleting service point {}", servicePointId);

    return servicePointRepository.deleteById(servicePointId)
      .map(rowSet -> {
        if (rowSet.rowCount() == 0) {
          log.error("deleteServicePoint:: service point {} was not found", servicePointId);
          return false;
        }
        log.info("deleteServicePoint:: service point {} was deleted successfully", servicePointId);
        servicePointDomainEventPublisher.publishDeleted(servicePoint);
        return true;
      });
  }
}
