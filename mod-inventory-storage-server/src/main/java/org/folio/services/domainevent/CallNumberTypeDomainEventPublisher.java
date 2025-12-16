package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.CALL_NUMBER_TYPE;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.CallNumberTypeRepository;
import org.folio.rest.jaxrs.model.CallNumberType;

public class CallNumberTypeDomainEventPublisher
  extends AbstractDomainEventPublisher<CallNumberType, CallNumberType> {

  public CallNumberTypeDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new CallNumberTypeRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders,
        CALL_NUMBER_TYPE.fullTopicName(tenantId(okapiHeaders))));
  }

  @Override
  protected Future<List<Pair<String, CallNumberType>>> getRecordIds(Collection<CallNumberType> types) {
    return succeededFuture(types.stream()
      .map(type -> pair(type.getId(), type))
      .toList()
    );
  }

  @Override
  protected CallNumberType convertDomainToEvent(String instanceId, CallNumberType type) {
    return type;
  }

  @Override
  protected String getId(CallNumberType type) {
    return type.getId();
  }
}
