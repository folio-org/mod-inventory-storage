package org.folio.services.classification;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Future;
import java.util.Map;
import org.folio.persist.ClassificationTypeRepository;
import org.folio.rest.jaxrs.model.ClassificationType;
import org.folio.rest.jaxrs.model.ClassificationTypes;
import org.folio.services.domainevent.ClassificationTypeDomainEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class ClassificationTypeService {

  private final ClassificationTypeDomainEventPublisher classificationTypeDomainEventPublisher;
  private final ClassificationTypeRepository classificationTypeRepository;

  public ClassificationTypeService(ClassificationTypeDomainEventPublisher classificationTypeDomainEventPublisher,
                                   ClassificationTypeRepository classificationTypeRepository) {
    this.classificationTypeRepository = classificationTypeRepository;
    this.classificationTypeDomainEventPublisher = classificationTypeDomainEventPublisher;
  }

  public Future<ClassificationTypes> getByQuery(String cql, int offset, int limit, String totalRecords,
                                   Map<String, String> headers) {
    return classificationTypeRepository.query(cql, offset, limit, totalRecords, tenantId(headers))
      .map(results -> new ClassificationTypes()
        .withClassificationTypes(results.getResults())
        .withTotalRecords(results.getResultInfo().getTotalRecords()));
  }

  public Future<ClassificationType> getByTypeId(String id, Map<String, String> headers) {
    return classificationTypeRepository.findById(id, tenantId(headers));
  }

  public Future<ClassificationType> create(ClassificationType entity, Map<String, String> headers) {
    return classificationTypeRepository.create(entity, tenantId(headers))
      .onSuccess(created -> classificationTypeDomainEventPublisher.publishRecordCreated(created, headers));
  }

  public Future<Void> update(String id, ClassificationType entity, Map<String, String> headers) {
    return classificationTypeRepository.getById(id, tenantId(headers))
      .compose(old -> classificationTypeRepository.replace(id, entity, tenantId(headers))
        .onSuccess(notUsed -> classificationTypeDomainEventPublisher.publishRecordUpdated(old, headers)));
  }

  public Future<Void> delete(String id, Map<String, String> headers) {
    return classificationTypeRepository.getById(id, tenantId(headers))
      .compose(old -> classificationTypeRepository.remove(id, tenantId(headers))
        .onSuccess(notUsed -> classificationTypeDomainEventPublisher.publishRecordRemoved(old, headers)));
  }
}
