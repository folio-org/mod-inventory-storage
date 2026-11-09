package org.folio.services.subjecttype;

import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.services.SubjectUtil.sourceValidationError;
import static org.folio.services.SubjectUtil.validateSubjectSourceCreate;
import static org.folio.services.SubjectUtil.validateSubjectSourceUpdate;

import io.vertx.core.Future;
import java.util.Map;
import org.folio.persist.SubjectTypeRepository;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.jaxrs.model.SubjectType;
import org.folio.rest.jaxrs.model.SubjectTypes;
import org.folio.services.consortium.ConsortiumService;
import org.folio.services.domainevent.SubjectTypeDomainEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class SubjectTypeService {

  private final ConsortiumService consortiumService;
  private final SubjectTypeDomainEventPublisher subjectTypeDomainEventPublisher;
  private final SubjectTypeRepository subjectTypeRepository;

  public SubjectTypeService(ConsortiumService consortiumService,
                           SubjectTypeDomainEventPublisher subjectTypeDomainEventPublisher,
                           SubjectTypeRepository subjectTypeRepository) {
    this.subjectTypeRepository = subjectTypeRepository;
    this.subjectTypeDomainEventPublisher = subjectTypeDomainEventPublisher;
    this.consortiumService = consortiumService;
  }

  public Future<SubjectTypes> getByQuery(String cql, int offset, int limit, String totalRecords,
                                   Map<String, String> headers) {
    return subjectTypeRepository.query(cql, offset, limit, totalRecords, tenantId(headers))
      .map(results -> new SubjectTypes()
        .withSubjectTypes(results.getResults())
        .withTotalRecords(results.getResultInfo().getTotalRecords()));
  }

  public Future<SubjectType> getById(String id, Map<String, String> headers) {
    return subjectTypeRepository.findById(id, tenantId(headers));
  }

  public Future<SubjectType> create(SubjectType subjectType, Map<String, String> headers) {
    return validateSubjectSourceCreate(subjectType.getSource().value(), consortiumService, headers)
      .compose(errorsOptional -> errorsOptional.isPresent()
        ? sourceValidationError(errorsOptional.get())
        : subjectTypeRepository.create(subjectType, tenantId(headers))
          .onSuccess(created -> subjectTypeDomainEventPublisher.publishRecordCreated(created, headers)));
  }

  public Future<Void> update(String id, SubjectType subjectType, Map<String, String> headers) {
    if (subjectType.getId() == null) {
      subjectType.setId(id);
    }

    return subjectTypeRepository.getById(id, tenantId(headers))
      .compose(oldSubjectType -> {
        if (oldSubjectType == null) {
          return Future.failedFuture(new NotFoundException("SubjectType was not found"));
        }
        return validateSubjectSourceUpdate(subjectType.getSource().value(), oldSubjectType.getSource().value(),
          consortiumService, headers)
          .compose(errorsOptional -> errorsOptional.isPresent()
            ? sourceValidationError(errorsOptional.get())
            : subjectTypeRepository.replace(id, subjectType, tenantId(headers))
              .onSuccess(notUsed -> subjectTypeDomainEventPublisher.publishRecordUpdated(oldSubjectType, headers)));
      });
  }

  public Future<Void> delete(String id, Map<String, String> headers) {
    return subjectTypeRepository.getById(id, tenantId(headers))
      .compose(oldSubjectType -> {
        if (oldSubjectType == null) {
          return Future.failedFuture(new NotFoundException("SubjectSource was not found"));
        }
        return subjectTypeRepository.remove(id, tenantId(headers))
          .onSuccess(notUsed -> subjectTypeDomainEventPublisher.publishRecordRemoved(oldSubjectType, headers));
      });
  }
}
