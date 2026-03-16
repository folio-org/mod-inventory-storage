package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.MATERIAL_TYPE;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.MaterialTypeRepository;
import org.folio.rest.jaxrs.model.MaterialType;

public class MaterialTypeDomainEventPublisher extends AbstractDomainEventPublisher<MaterialType, MaterialType> {

  public MaterialTypeDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new MaterialTypeRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders, MATERIAL_TYPE.fullTopicName(tenantId(okapiHeaders))));
  }

  @Override
  protected Future<List<Pair<String, MaterialType>>> getRecordIds(Collection<MaterialType> materialTypes) {
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
