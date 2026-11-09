package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.MATERIAL_TYPE;

import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.AbstractRepository;
import org.folio.persist.MaterialTypeRepository;
import org.folio.rest.jaxrs.model.MaterialType;
import org.springframework.stereotype.Component;

@Component
public class MaterialTypeDomainEventPublisher extends AbstractDomainEventPublisher<MaterialType, MaterialType> {

  private final MaterialTypeRepository materialTypeRepository;

  public MaterialTypeDomainEventPublisher(DomainEventPublisherFactory publisherFactory,
                                          MaterialTypeRepository materialTypeRepository) {
    super(publisherFactory.publisher(MATERIAL_TYPE));
    this.materialTypeRepository = materialTypeRepository;
  }

  @Override
  protected AbstractRepository<MaterialType> repository() {
    return materialTypeRepository;
  }

  @Override
  protected Future<List<Pair<String, MaterialType>>> getRecordIds(Collection<MaterialType> materialTypes,
                                                                  Map<String, String> headers) {
    return succeededFuture(materialTypes.stream()
      .map(materialType -> pair(materialType.getId(), materialType))
      .toList()
    );
  }

  @Override
  protected MaterialType convertDomainToEvent(String instanceId, MaterialType materialType) {
    return materialType;
  }

  @Override
  protected String getId(MaterialType materialType) {
    return materialType.getId();
  }
}
