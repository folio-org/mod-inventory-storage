package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.INSTANCE_DATE_TYPE;

import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.AbstractRepository;
import org.folio.persist.InstanceDateTypeRepository;
import org.folio.rest.jaxrs.model.InstanceDateType;
import org.springframework.stereotype.Component;

@Component
public class InstanceDateTypeDomainEventPublisher
  extends AbstractDomainEventPublisher<InstanceDateType, InstanceDateType> {

  private final InstanceDateTypeRepository instanceDateTypeRepository;

  public InstanceDateTypeDomainEventPublisher(DomainEventPublisherFactory publisherFactory,
                                              InstanceDateTypeRepository instanceDateTypeRepository) {
    super(publisherFactory.publisher(INSTANCE_DATE_TYPE));
    this.instanceDateTypeRepository = instanceDateTypeRepository;
  }

  @Override
  protected AbstractRepository<InstanceDateType> repository() {
    return instanceDateTypeRepository;
  }

  @Override
  protected Future<List<Pair<String, InstanceDateType>>> getRecordIds(Collection<InstanceDateType> dateTypes,
                                                                      Map<String, String> headers) {
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
