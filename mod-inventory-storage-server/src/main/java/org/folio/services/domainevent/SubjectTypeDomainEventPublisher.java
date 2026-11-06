package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.SUBJECT_TYPE;

import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.AbstractRepository;
import org.folio.persist.SubjectTypeRepository;
import org.folio.rest.jaxrs.model.SubjectType;
import org.springframework.stereotype.Component;

@Component
public class SubjectTypeDomainEventPublisher extends AbstractDomainEventPublisher<SubjectType, SubjectType> {

  private final SubjectTypeRepository subjectTypeRepository;

  public SubjectTypeDomainEventPublisher(DomainEventPublisherFactory publisherFactory,
                                         SubjectTypeRepository subjectTypeRepository) {
    super(publisherFactory.publisher(SUBJECT_TYPE));
    this.subjectTypeRepository = subjectTypeRepository;
  }

  @Override
  protected AbstractRepository<SubjectType> repository() {
    return subjectTypeRepository;
  }

  @Override
  protected Future<List<Pair<String, SubjectType>>> getRecordIds(Collection<SubjectType> subjectTypes,
                                                                 Map<String, String> headers) {
    return succeededFuture(subjectTypes.stream()
      .map(subjectType -> pair(subjectType.getId(), subjectType))
      .toList()
    );
  }

  @Override
  protected SubjectType convertDomainToEvent(String instanceId, SubjectType subjectType) {
    return subjectType;
  }

  @Override
  protected String getId(SubjectType subjectType) {
    return subjectType.getId();
  }
}
