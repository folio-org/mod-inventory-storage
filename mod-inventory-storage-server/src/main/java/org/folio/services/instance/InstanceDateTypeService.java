package org.folio.services.instance;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Future;
import java.util.Map;
import org.folio.persist.InstanceDateTypeRepository;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.InstanceDateType;
import org.folio.rest.jaxrs.model.InstanceDateTypePatchRequest;
import org.folio.rest.jaxrs.model.InstanceDateTypes;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.domainevent.InstanceDateTypeDomainEventPublisher;
import org.folio.utils.ConsortiumUtils;
import org.springframework.stereotype.Service;

@Service
public class InstanceDateTypeService {

  private final ConsortiumDataCache consortiumDataCache;
  private final InstanceDateTypeDomainEventPublisher instanceDateTypeDomainEventPublisher;
  private final InstanceDateTypeRepository instanceDateTypeRepository;

  public InstanceDateTypeService(ConsortiumDataCache consortiumDataCache,
                                 InstanceDateTypeDomainEventPublisher instanceDateTypeDomainEventPublisher,
                                 InstanceDateTypeRepository instanceDateTypeRepository) {
    this.instanceDateTypeRepository = instanceDateTypeRepository;
    this.instanceDateTypeDomainEventPublisher = instanceDateTypeDomainEventPublisher;
    this.consortiumDataCache = consortiumDataCache;
  }

  public Future<InstanceDateTypes> getInstanceDateTypes(String query, int offset, int limit,
                                                        Map<String, String> headers) {
    return instanceDateTypeRepository.query(query, offset, limit, "exact", tenantId(headers))
      .map(results -> new InstanceDateTypes()
        .withInstanceDateTypes(results.getResults())
        .withTotalRecords(results.getResultInfo().getTotalRecords()));
  }

  public Future<Void> patchInstanceDateTypes(String id, InstanceDateTypePatchRequest entity,
                                             Map<String, String> headers) {
    return consortiumDataCache.getConsortiumData(headers)
      .compose(consortiumData -> {
        if (consortiumData.isEmpty() || ConsortiumUtils.isCentralTenant(headers, consortiumData.get())) {
          return doUpdate(id, entity, headers);
        } else {
          throw new BadRequestException("Action ‘UPDATE' is not supported for consortium member tenant.");
        }
      });
  }

  public Future<Void> putInstanceDateType(String id, InstanceDateType entity, Map<String, String> headers) {
    return instanceDateTypeRepository.getById(id, tenantId(headers))
      .compose(oldDateType -> replace(id, entity, oldDateType, headers));
  }

  private Future<Void> doUpdate(String id, InstanceDateTypePatchRequest entity, Map<String, String> headers) {
    return instanceDateTypeRepository.getById(id, tenantId(headers))
      .compose(oldDateType -> replace(id, oldDateType.withName(entity.getName()), oldDateType, headers));
  }

  private Future<Void> replace(String id, InstanceDateType entity, InstanceDateType oldDateType,
                               Map<String, String> headers) {
    return instanceDateTypeRepository.replace(id, entity, tenantId(headers))
      .onSuccess(notUsed -> instanceDateTypeDomainEventPublisher.publishRecordUpdated(oldDateType, headers));
  }
}
