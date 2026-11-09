package org.folio.services.callnumber;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Future;
import java.util.Map;
import org.folio.persist.CallNumberTypeRepository;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.CallNumberType;
import org.folio.rest.jaxrs.model.CallNumberTypes;
import org.folio.services.domainevent.CallNumberTypeDomainEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class CallNumberTypeService {

  private static final String SYSTEM_CALL_NUMBER_TYPE_SOURCE = "system";

  private final CallNumberTypeDomainEventPublisher callNumberTypeDomainEventPublisher;
  private final CallNumberTypeRepository callNumberTypeRepository;

  public CallNumberTypeService(CallNumberTypeDomainEventPublisher callNumberTypeDomainEventPublisher,
                               CallNumberTypeRepository callNumberTypeRepository) {
    this.callNumberTypeRepository = callNumberTypeRepository;
    this.callNumberTypeDomainEventPublisher = callNumberTypeDomainEventPublisher;
  }

  public Future<CallNumberTypes> getByQuery(String cql, int offset, int limit, String totalRecords,
                                            Map<String, String> headers) {
    return callNumberTypeRepository.query(cql, offset, limit, totalRecords, tenantId(headers))
      .map(results -> new CallNumberTypes()
        .withCallNumberTypes(results.getResults())
        .withTotalRecords(results.getResultInfo().getTotalRecords()));
  }

  public Future<CallNumberType> getByTypeId(String id, Map<String, String> headers) {
    return callNumberTypeRepository.findById(id, tenantId(headers));
  }

  public Future<CallNumberType> create(CallNumberType type, Map<String, String> headers) {
    return callNumberTypeRepository.create(type, tenantId(headers))
      .onSuccess(created -> callNumberTypeDomainEventPublisher.publishRecordCreated(created, headers));
  }

  public Future<Void> update(String id, CallNumberType type, Map<String, String> headers) {
    return getIfNotSystemCallNumberType(id, "System call number type couldn't be updated", headers)
      .compose(oldType -> callNumberTypeRepository.replace(id, type, tenantId(headers))
        .onSuccess(notUsed -> callNumberTypeDomainEventPublisher.publishRecordUpdated(oldType, headers)));
  }

  public Future<Void> delete(String id, Map<String, String> headers) {
    return getIfNotSystemCallNumberType(id, "System call number type couldn't be deleted", headers)
      .compose(oldType -> callNumberTypeRepository.remove(id, tenantId(headers))
        .onSuccess(notUsed -> callNumberTypeDomainEventPublisher.publishRecordRemoved(oldType, headers)));
  }

  private Future<CallNumberType> getIfNotSystemCallNumberType(String id, String errorMessage,
                                                            Map<String, String> headers) {
    return callNumberTypeRepository.getById(id, tenantId(headers))
      .compose(callNumberType -> {
        if (isSystemSource(callNumberType)) {
          return Future.failedFuture(new BadRequestException(errorMessage));
        }
        return Future.succeededFuture(callNumberType);
      });
  }

  private boolean isSystemSource(CallNumberType callNumberType) {
    return SYSTEM_CALL_NUMBER_TYPE_SOURCE.equals(callNumberType.getSource());
  }
}
