package org.folio.services.materialtype;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Future;
import java.util.Map;
import org.folio.persist.MaterialTypeRepository;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.jaxrs.model.MaterialType;
import org.folio.rest.jaxrs.model.MaterialTypes;
import org.folio.services.domainevent.MaterialTypeDomainEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class MaterialTypeService {

  private final MaterialTypeDomainEventPublisher materialTypeDomainEventPublisher;
  private final MaterialTypeRepository materialTypeRepository;

  public MaterialTypeService(MaterialTypeDomainEventPublisher materialTypeDomainEventPublisher,
                             MaterialTypeRepository materialTypeRepository) {
    this.materialTypeRepository = materialTypeRepository;
    this.materialTypeDomainEventPublisher = materialTypeDomainEventPublisher;
  }

  public Future<MaterialTypes> getByQuery(String cql, int offset, int limit, String totalRecords,
                                          Map<String, String> headers) {
    return materialTypeRepository.query(cql, offset, limit, totalRecords, tenantId(headers))
      .map(results -> new MaterialTypes()
        .withMtypes(results.getResults())
        .withTotalRecords(results.getResultInfo().getTotalRecords()));
  }

  public Future<MaterialType> getById(String id, Map<String, String> headers) {
    return materialTypeRepository.findById(id, tenantId(headers));
  }

  public Future<MaterialType> create(MaterialType materialType, Map<String, String> headers) {
    return materialTypeRepository.create(materialType, tenantId(headers))
      .onSuccess(created -> materialTypeDomainEventPublisher.publishRecordCreated(created, headers));
  }

  public Future<Void> update(String id, MaterialType materialType, Map<String, String> headers) {
    if (materialType.getId() == null) {
      materialType.setId(id);
    }

    return materialTypeRepository.getById(id, tenantId(headers))
      .compose(oldMaterialType -> {
        if (oldMaterialType == null) {
          return Future.failedFuture(new NotFoundException("MaterialType was not found"));
        }
        return materialTypeRepository.replace(id, materialType, tenantId(headers))
          .onSuccess(notUsed -> materialTypeDomainEventPublisher.publishRecordUpdated(oldMaterialType, headers));
      });
  }

  public Future<Void> delete(String id, Map<String, String> headers) {
    return materialTypeRepository.getById(id, tenantId(headers))
      .compose(oldMaterialType -> {
        if (oldMaterialType == null) {
          return Future.failedFuture(new NotFoundException("MaterialType was not found"));
        }
        return materialTypeRepository.remove(id, tenantId(headers))
          .onSuccess(notUsed -> materialTypeDomainEventPublisher.publishRecordRemoved(oldMaterialType, headers));
      });
  }

  public Future<Void> deleteAll(Map<String, String> headers) {
    return materialTypeRepository.deleteAll(tenantId(headers))
      .compose(notUsed -> materialTypeDomainEventPublisher.publishAllRemoved(headers));
  }
}
