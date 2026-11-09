package org.folio.services.locationunit;

import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;

import io.vertx.core.Future;
import java.util.Map;
import org.folio.persist.CampusRepository;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.LocationCampus;
import org.folio.rest.jaxrs.model.LocationCampuses;
import org.folio.services.domainevent.CampusDomainEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class CampusService {

  private static final String MSG_ID_NOT_MATCH = "Illegal operation: Campus ID cannot be changed";

  private final CampusDomainEventPublisher campusDomainEventPublisher;
  private final CampusRepository campusRepository;

  public CampusService(CampusDomainEventPublisher campusDomainEventPublisher,
                CampusRepository campusRepository) {
    this.campusRepository = campusRepository;
    this.campusDomainEventPublisher = campusDomainEventPublisher;
  }

  public Future<LocationCampuses> getByQuery(String cql, int offset, int limit, String totalRecords,
                          boolean includeShadow, Map<String, String> headers) {
    var additionalCql = includeShadow ? null : "isShadow=false";
    return campusRepository
      .query(cql, additionalCql, offset, limit, totalRecords, tenantId(headers))
      .map(results -> new LocationCampuses()
        .withLoccamps(results.getResults())
        .withTotalRecords(results.getResultInfo().getTotalRecords()));
  }

  public Future<LocationCampus> getById(String id, Map<String, String> headers) {
    return campusRepository.findById(id, tenantId(headers));
  }

  public Future<LocationCampus> create(LocationCampus loccamp, Map<String, String> headers) {
    return campusRepository.create(loccamp, tenantId(headers))
      .onSuccess(created -> campusDomainEventPublisher.publishRecordCreated(created, headers));
  }

  public Future<Void> update(String id, LocationCampus loccamp, Map<String, String> headers) {
    if (loccamp.getId() != null && !loccamp.getId().equals(id)) {
      return Future.failedFuture(new ValidationException(
        createValidationErrorMessage("loccamp", loccamp.getId(), MSG_ID_NOT_MATCH)));
    }
    if (loccamp.getId() == null) {
      loccamp.setId(id);
    }

    return campusRepository.getById(id, tenantId(headers))
      .compose(oldCampus -> campusRepository.replace(id, loccamp, tenantId(headers))
        .onSuccess(notUsed -> campusDomainEventPublisher.publishRecordUpdated(oldCampus, headers)));
  }

  public Future<Void> delete(String id, Map<String, String> headers) {
    return campusRepository.getById(id, tenantId(headers))
      .compose(oldCampus -> campusRepository.remove(id, tenantId(headers))
        .onSuccess(notUsed -> campusDomainEventPublisher.publishRecordRemoved(oldCampus, headers)));
  }

  public Future<Void> deleteAll(Map<String, String> headers) {
    return campusRepository.deleteAll(tenantId(headers))
      .onSuccess(notUsed -> campusDomainEventPublisher.publishAllRemoved(headers))
      .mapEmpty();
  }
}
