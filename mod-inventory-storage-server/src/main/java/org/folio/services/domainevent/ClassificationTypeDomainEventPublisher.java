package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.CLASSIFICATION_TYPE;

import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.AbstractRepository;
import org.folio.persist.ClassificationTypeRepository;
import org.folio.rest.jaxrs.model.ClassificationType;
import org.springframework.stereotype.Component;

@Component
public class ClassificationTypeDomainEventPublisher
  extends AbstractDomainEventPublisher<ClassificationType, ClassificationType> {

  private final ClassificationTypeRepository classificationTypeRepository;

  public ClassificationTypeDomainEventPublisher(DomainEventPublisherFactory publisherFactory,
                                                ClassificationTypeRepository classificationTypeRepository) {
    super(publisherFactory.publisher(CLASSIFICATION_TYPE));
    this.classificationTypeRepository = classificationTypeRepository;
  }

  @Override
  protected AbstractRepository<ClassificationType> repository() {
    return classificationTypeRepository;
  }

  @Override
  protected Future<List<Pair<String, ClassificationType>>> getRecordIds(Collection<ClassificationType> types,
                                                                        Map<String, String> headers) {
    return succeededFuture(types.stream()
      .map(type -> pair(type.getId(), type))
      .toList()
    );
  }

  @Override
  protected ClassificationType convertDomainToEvent(String instanceId, ClassificationType type) {
    return type;
  }

  @Override
  protected String getId(ClassificationType type) {
    return type.getId();
  }
}
