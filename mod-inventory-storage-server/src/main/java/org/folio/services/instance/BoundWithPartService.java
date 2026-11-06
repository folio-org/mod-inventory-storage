package org.folio.services.instance;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Future;
import java.util.Map;
import org.folio.persist.BoundWithRepository;
import org.folio.rest.jaxrs.model.BoundWithPart;
import org.folio.rest.jaxrs.model.BoundWithParts;
import org.folio.services.domainevent.BoundWithDomainEventPublisher;
import org.folio.validator.CommonValidators;
import org.springframework.stereotype.Service;

@Service
public class BoundWithPartService {

  private final BoundWithDomainEventPublisher boundWithDomainEventPublisher;
  private final BoundWithRepository boundWithRepository;

  public BoundWithPartService(BoundWithDomainEventPublisher boundWithDomainEventPublisher,
                              BoundWithRepository boundWithRepository) {
    this.boundWithRepository = boundWithRepository;
    this.boundWithDomainEventPublisher = boundWithDomainEventPublisher;
  }

  public Future<BoundWithParts> getByQuery(String cql, int offset, int limit, String totalRecords,
                                           Map<String, String> headers) {
    return boundWithRepository.query(cql, offset, limit, totalRecords, tenantId(headers))
      .map(results -> new BoundWithParts()
        .withBoundWithParts(results.getResults())
        .withTotalRecords(results.getResultInfo().getTotalRecords()));
  }

  public Future<BoundWithPart> getById(String id, Map<String, String> headers) {
    return boundWithRepository.findById(id, tenantId(headers));
  }

  public Future<BoundWithPart> create(BoundWithPart entity, Map<String, String> headers) {
    return boundWithRepository.create(entity, tenantId(headers))
      .onSuccess(created -> boundWithDomainEventPublisher.publishRecordCreated(created, headers));
  }

  public Future<Void> update(BoundWithPart entity, String id, Map<String, String> headers) {
    return boundWithRepository.replace(id, entity, tenantId(headers))
      .onSuccess(notUsed -> boundWithDomainEventPublisher.publishRecordUpdated(entity, headers));
  }

  public Future<Void> delete(String id, Map<String, String> headers) {
    return boundWithRepository.getById(id, tenantId(headers))
      .compose(CommonValidators::refuseIfNotFound)
      .compose(item -> boundWithRepository.remove(id, tenantId(headers))
        .onSuccess(notUsed -> boundWithDomainEventPublisher.publishRecordRemoved(item, headers)));
  }
}
