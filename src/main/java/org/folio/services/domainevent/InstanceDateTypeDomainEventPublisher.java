package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.INSTANCE_DATE_TYPE;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.InstanceDateTypeRepository;
import org.folio.rest.jaxrs.model.InstanceDateType;

public class InstanceDateTypeDomainEventPublisher
  extends AbstractDomainEventPublisher<InstanceDateType, InstanceDateType> {

  public InstanceDateTypeDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new InstanceDateTypeRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders,
        INSTANCE_DATE_TYPE.fullTopicName(tenantId(okapiHeaders))));
  }

  @Override
  protected Future<List<Pair<String, InstanceDateType>>> getRecordIds(Collection<InstanceDateType> dateTypes) {
    return succeededFuture(dateTypes.stream()
      .map(subjectType -> pair(subjectType.getId(), subjectType))
      .toList()
    );
  }

  @Override
  protected InstanceDateType convertDomainToEvent(String id, InstanceDateType dateType) {
    return dateType;
  }

  @Override
  protected String getId(InstanceDateType dateType) {
    return dateType.getId();
  }
}
