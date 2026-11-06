package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.CALL_NUMBER_TYPE;

import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.AbstractRepository;
import org.folio.persist.CallNumberTypeRepository;
import org.folio.rest.jaxrs.model.CallNumberType;
import org.springframework.stereotype.Component;

@Component
public class CallNumberTypeDomainEventPublisher
  extends AbstractDomainEventPublisher<CallNumberType, CallNumberType> {

  private final CallNumberTypeRepository callNumberTypeRepository;

  public CallNumberTypeDomainEventPublisher(DomainEventPublisherFactory publisherFactory,
                                            CallNumberTypeRepository callNumberTypeRepository) {
    super(publisherFactory.publisher(CALL_NUMBER_TYPE));
    this.callNumberTypeRepository = callNumberTypeRepository;
  }

  @Override
  protected AbstractRepository<CallNumberType> repository() {
    return callNumberTypeRepository;
  }

  @Override
  protected Future<List<Pair<String, CallNumberType>>> getRecordIds(Collection<CallNumberType> types,
                                                                    Map<String, String> headers) {
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
