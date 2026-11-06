package org.folio.services.servicepoint;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Future;
import java.util.Map;
import org.folio.persist.ServicePointRepository;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.jaxrs.model.ServicePoint;
import org.folio.rest.jaxrs.model.ServicePoints;
import org.folio.services.domainevent.ServicePointDomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ServicePointService {

  private static final Logger log = LoggerFactory.getLogger(ServicePointService.class);

  private final ServicePointDomainEventPublisher servicePointDomainEventPublisher;
  private final ServicePointRepository servicePointRepository;

  public ServicePointService(ServicePointDomainEventPublisher servicePointDomainEventPublisher,
                             ServicePointRepository servicePointRepository) {
    this.servicePointRepository = servicePointRepository;
    this.servicePointDomainEventPublisher = servicePointDomainEventPublisher;
  }

  public Future<ServicePoints> getByQuery(String cql, int offset, int limit, String totalRecords,
                                          Map<String, String> headers) {
    return servicePointRepository.query(cql, offset, limit, totalRecords, tenantId(headers))
      .map(results -> new ServicePoints()
        .withServicepoints(results.getResults())
        .withTotalRecords(results.getResultInfo().getTotalRecords()));
  }

  public Future<ServicePoint> getById(String servicePointId, Map<String, String> headers) {
    return servicePointRepository.findById(servicePointId, tenantId(headers));
  }

  public Future<Void> deleteAll(Map<String, String> headers) {
    return servicePointRepository.deleteAll(tenantId(headers)).mapEmpty();
  }

  public Future<Void> updateServicePoint(String servicePointId, ServicePoint entity,
                                             Map<String, String> headers) {
    log.debug("updateServicePoint:: parameters servicePointId: {}, entity: "
              + "ServicePoint(id={}, name={})", servicePointId, entity.getId(), entity.getName());
    entity.setId(servicePointId);

    return servicePointRepository.getById(servicePointId, tenantId(headers))
      .compose(servicePoint -> {
        if (servicePoint != null) {
          log.info("updateServicePoint:: servicePoint is found");
          return servicePointRepository.update(servicePointId, entity, tenantId(headers))
            .map(rowSet -> servicePoint);
        }
        log.warn("updateServicePoint:: servicePoint was not found");
        return Future.failedFuture(new NotFoundException("ServicePoint was not found"));
      })
      .onSuccess(oldServicePoint -> servicePointDomainEventPublisher
        .publishUpdated(oldServicePoint, entity, headers))
      .mapEmpty();
  }

  public Future<ServicePoint> createServicePoint(String servicePointId, ServicePoint servicePoint,
                                             Map<String, String> headers) {
    servicePoint.setId(servicePointId);
    return servicePointRepository.save(servicePointId, servicePoint, tenantId(headers))
      .compose(notUsed -> servicePointDomainEventPublisher.publishCreated(servicePoint, headers)
        .map(notUsedEither -> servicePoint));
  }

  public Future<Boolean> deleteServicePoint(String servicePointId, Map<String, String> headers) {
    log.debug("deleteServicePoint:: parameters servicePointId: {}", servicePointId);

    return servicePointRepository.getById(servicePointId, tenantId(headers))
      .compose(servicePoint -> deleteServicePoint(servicePoint, headers));
  }

  private Future<Boolean> deleteServicePoint(ServicePoint servicePoint, Map<String, String> headers) {
    if (servicePoint == null) {
      log.error("deleteServicePoint:: service point was not found");
      return succeededFuture(false);
    }

    String servicePointId = servicePoint.getId();
    log.debug("deleteServicePoint:: deleting service point {}", servicePointId);

    return servicePointRepository.deleteById(servicePointId, tenantId(headers))
      .map(deleted -> {
        if (Boolean.FALSE.equals(deleted)) {
          log.error("deleteServicePoint:: service point {} was not deleted", servicePointId);
          return false;
        }
        log.info("deleteServicePoint:: service point {} was deleted successfully", servicePointId);
        servicePointDomainEventPublisher.publishDeleted(servicePoint, headers);
        return true;
      });
  }
}
