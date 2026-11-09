package org.folio.services.loantype;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Future;
import java.util.Map;
import org.folio.persist.LoanTypeRepository;
import org.folio.rest.jaxrs.model.LoanType;
import org.folio.rest.jaxrs.model.LoanTypes;
import org.folio.services.domainevent.LoanTypeDomainEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class LoanTypeService {

  private final LoanTypeDomainEventPublisher loanTypeDomainEventPublisher;
  private final LoanTypeRepository loanTypeRepository;

  public LoanTypeService(LoanTypeDomainEventPublisher loanTypeDomainEventPublisher,
                         LoanTypeRepository loanTypeRepository) {
    this.loanTypeRepository = loanTypeRepository;
    this.loanTypeDomainEventPublisher = loanTypeDomainEventPublisher;
  }

  public Future<LoanTypes> getByQuery(String cql, int offset, int limit, String totalRecords,
                                   Map<String, String> headers) {
    return loanTypeRepository.query(cql, offset, limit, totalRecords, tenantId(headers))
      .map(results -> new LoanTypes()
        .withLoantypes(results.getResults())
        .withTotalRecords(results.getResultInfo().getTotalRecords()));
  }

  public Future<LoanType> getById(String id, Map<String, String> headers) {
    return loanTypeRepository.findById(id, tenantId(headers));
  }

  public Future<LoanType> create(LoanType entity, Map<String, String> headers) {
    return loanTypeRepository.create(entity, tenantId(headers))
      .onSuccess(created -> loanTypeDomainEventPublisher.publishRecordCreated(created, headers));
  }

  public Future<Void> update(String id, LoanType entity, Map<String, String> headers) {
    return loanTypeRepository.getById(id, tenantId(headers))
      .compose(old -> loanTypeRepository.replace(id, entity, tenantId(headers))
        .onSuccess(notUsed -> loanTypeDomainEventPublisher.publishRecordUpdated(old, headers)));
  }

  public Future<Void> delete(String id, Map<String, String> headers) {
    return loanTypeRepository.getById(id, tenantId(headers))
      .compose(old -> loanTypeRepository.remove(id, tenantId(headers))
        .onSuccess(notUsed -> loanTypeDomainEventPublisher.publishRecordRemoved(old, headers)));
  }

  public Future<Void> deleteAll(Map<String, String> headers) {
    return loanTypeRepository.deleteAll(tenantId(headers))
      .compose(notUsed -> loanTypeDomainEventPublisher.publishAllRemoved(headers));
  }
}
