package org.folio.services.subjectsource;

import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.services.SubjectUtil.sourceValidationError;
import static org.folio.services.SubjectUtil.validateSubjectSourceCreate;
import static org.folio.services.SubjectUtil.validateSubjectSourceUpdate;

import io.vertx.core.Future;
import java.util.Map;
import org.folio.persist.SubjectSourceRepository;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.jaxrs.model.SubjectSource;
import org.folio.rest.jaxrs.model.SubjectSources;
import org.folio.services.consortium.ConsortiumService;
import org.folio.services.domainevent.SubjectSourceDomainEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class SubjectSourceService {

  private final ConsortiumService consortiumService;
  private final SubjectSourceDomainEventPublisher subjectSourceDomainEventPublisher;
  private final SubjectSourceRepository subjectSourceRepository;

  public SubjectSourceService(ConsortiumService consortiumService,
                             SubjectSourceDomainEventPublisher subjectSourceDomainEventPublisher,
                             SubjectSourceRepository subjectSourceRepository) {
    this.subjectSourceRepository = subjectSourceRepository;
    this.subjectSourceDomainEventPublisher = subjectSourceDomainEventPublisher;
    this.consortiumService = consortiumService;
  }

  public Future<SubjectSources> getByQuery(String cql, int offset, int limit, String totalRecords,
                                   Map<String, String> headers) {
    return subjectSourceRepository.query(cql, offset, limit, totalRecords, tenantId(headers))
      .map(results -> new SubjectSources()
        .withSubjectSources(results.getResults())
        .withTotalRecords(results.getResultInfo().getTotalRecords()));
  }

  public Future<SubjectSource> getById(String id, Map<String, String> headers) {
    return subjectSourceRepository.findById(id, tenantId(headers));
  }

  public Future<SubjectSource> create(SubjectSource subjectSource, Map<String, String> headers) {
    return validateSubjectSourceCreate(subjectSource.getSource().value(), consortiumService, headers)
      .compose(errorsOptional -> errorsOptional.isPresent()
        ? sourceValidationError(errorsOptional.get())
        : subjectSourceRepository.create(subjectSource, tenantId(headers))
          .onSuccess(created -> subjectSourceDomainEventPublisher.publishRecordCreated(created, headers)));
  }

  public Future<Void> update(String id, SubjectSource subjectSource, Map<String, String> headers) {
    if (subjectSource.getId() == null) {
      subjectSource.setId(id);
    }

    return subjectSourceRepository.getById(id, tenantId(headers))
      .compose(oldSubjectSource -> {
        if (oldSubjectSource == null) {
          return Future.failedFuture(new NotFoundException("SubjectSource was not found"));
        }
        return validateSubjectSourceUpdate(subjectSource.getSource().value(), oldSubjectSource.getSource().value(),
          consortiumService, headers)
          .compose(errorsOptional -> errorsOptional.isPresent()
            ? sourceValidationError(errorsOptional.get())
            : subjectSourceRepository.replace(id, subjectSource, tenantId(headers))
              .onSuccess(notUsed -> subjectSourceDomainEventPublisher.publishRecordUpdated(oldSubjectSource, headers)));
      });
  }

  public Future<Void> delete(String id, Map<String, String> headers) {
    return subjectSourceRepository.getById(id, tenantId(headers))
      .compose(oldSubjectSource -> {
        if (oldSubjectSource == null) {
          return Future.failedFuture(new NotFoundException("SubjectSource was not found"));
        }
        return subjectSourceRepository.remove(id, tenantId(headers))
          .onSuccess(notUsed -> subjectSourceDomainEventPublisher.publishRecordRemoved(oldSubjectSource, headers));
      });
  }
}
