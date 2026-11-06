package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.SUBJECT_SOURCE;

import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.AbstractRepository;
import org.folio.persist.SubjectSourceRepository;
import org.folio.rest.jaxrs.model.SubjectSource;
import org.springframework.stereotype.Component;

@Component
public class SubjectSourceDomainEventPublisher extends AbstractDomainEventPublisher<SubjectSource, SubjectSource> {

  private final SubjectSourceRepository subjectSourceRepository;

  public SubjectSourceDomainEventPublisher(DomainEventPublisherFactory publisherFactory,
                                           SubjectSourceRepository subjectSourceRepository) {
    super(publisherFactory.publisher(SUBJECT_SOURCE));
    this.subjectSourceRepository = subjectSourceRepository;
  }

  @Override
  protected AbstractRepository<SubjectSource> repository() {
    return subjectSourceRepository;
  }

  @Override
  protected Future<List<Pair<String, SubjectSource>>> getRecordIds(Collection<SubjectSource> subjectSources,
                                                                   Map<String, String> headers) {
    return succeededFuture(subjectSources.stream()
      .map(subjectSource -> pair(subjectSource.getId(), subjectSource))
      .toList()
    );
  }

  @Override
  protected SubjectSource convertDomainToEvent(String instanceId, SubjectSource subjectSource) {
    return subjectSource;
  }

  @Override
  protected String getId(SubjectSource subjectSource) {
    return subjectSource.getId();
  }
}
