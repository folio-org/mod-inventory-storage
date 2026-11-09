package org.folio.services.locationunit;

import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;

import io.vertx.core.Future;
import java.util.Map;
import org.folio.persist.InstitutionRepository;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.LocationInstitution;
import org.folio.rest.jaxrs.model.LocationInstitutions;
import org.folio.services.domainevent.InstitutionDomainEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class InstitutionService {

  private static final String MSG_ID_NOT_MATCH = "Illegal operation: Institution ID cannot be changed";

  private final InstitutionDomainEventPublisher institutionDomainEventPublisher;
  private final InstitutionRepository institutionRepository;

  public InstitutionService(InstitutionDomainEventPublisher institutionDomainEventPublisher,
                     InstitutionRepository institutionRepository) {
    this.institutionRepository = institutionRepository;
    this.institutionDomainEventPublisher = institutionDomainEventPublisher;
  }

  public Future<LocationInstitutions> getByQuery(String cql, int offset, int limit, String totalRecords,
                               boolean includeShadow, Map<String, String> headers) {
    var additionalCql = includeShadow ? null : "isShadow=false";
    return institutionRepository
      .query(cql, additionalCql, offset, limit, totalRecords, tenantId(headers))
      .map(results -> new LocationInstitutions()
        .withLocinsts(results.getResults())
        .withTotalRecords(results.getResultInfo().getTotalRecords()));
  }

  public Future<LocationInstitution> getById(String id, Map<String, String> headers) {
    return institutionRepository.findById(id, tenantId(headers));
  }

  public Future<LocationInstitution> create(LocationInstitution locinst, Map<String, String> headers) {
    return institutionRepository.create(locinst, tenantId(headers))
      .onSuccess(created -> institutionDomainEventPublisher.publishRecordCreated(created, headers));
  }

  public Future<Void> update(String id, LocationInstitution locinst, Map<String, String> headers) {
    if (locinst.getId() != null && !locinst.getId().equals(id)) {
      return Future.failedFuture(new ValidationException(
        createValidationErrorMessage("locinst", locinst.getId(), MSG_ID_NOT_MATCH)));
    }
    if (locinst.getId() == null) {
      locinst.setId(id);
    }

    return institutionRepository.getById(id, tenantId(headers))
      .compose(oldInstitution -> institutionRepository.replace(id, locinst, tenantId(headers))
        .onSuccess(notUsed -> institutionDomainEventPublisher.publishRecordUpdated(oldInstitution, headers)));
  }

  public Future<Void> delete(String id, Map<String, String> headers) {
    return institutionRepository.getById(id, tenantId(headers))
      .compose(oldInstitution -> institutionRepository.remove(id, tenantId(headers))
        .onSuccess(notUsed -> institutionDomainEventPublisher.publishRecordRemoved(oldInstitution, headers)));
  }

  public Future<Void> deleteAll(Map<String, String> headers) {
    return institutionRepository.deleteAll(tenantId(headers))
      .onSuccess(notUsed -> institutionDomainEventPublisher.publishAllRemoved(headers))
      .mapEmpty();
  }
}
