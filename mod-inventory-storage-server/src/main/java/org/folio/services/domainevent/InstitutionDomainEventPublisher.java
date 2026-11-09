package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.INSTITUTION;

import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.AbstractRepository;
import org.folio.persist.InstitutionRepository;
import org.folio.rest.jaxrs.model.LocationInstitution;
import org.springframework.stereotype.Component;

@Component
public class InstitutionDomainEventPublisher
  extends AbstractDomainEventPublisher<LocationInstitution, LocationInstitution> {

  private final InstitutionRepository institutionRepository;

  public InstitutionDomainEventPublisher(DomainEventPublisherFactory publisherFactory,
                                         InstitutionRepository institutionRepository) {
    super(publisherFactory.publisher(INSTITUTION));
    this.institutionRepository = institutionRepository;
  }

  @Override
  protected AbstractRepository<LocationInstitution> repository() {
    return institutionRepository;
  }

  @Override
  protected Future<List<Pair<String, LocationInstitution>>> getRecordIds(Collection<LocationInstitution> institutions,
                                                                         Map<String, String> headers) {
    return succeededFuture(institutions.stream()
      .map(institution -> pair(institution.getId(), institution))
      .toList()
    );
  }

  @Override
  protected LocationInstitution convertDomainToEvent(String id, LocationInstitution institution) {
    return institution;
  }

  @Override
  protected String getId(LocationInstitution institution) {
    return institution.getId();
  }
}
