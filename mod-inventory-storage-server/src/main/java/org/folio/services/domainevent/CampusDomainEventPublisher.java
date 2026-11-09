package org.folio.services.domainevent;

import static org.folio.InventoryKafkaTopic.CAMPUS;

import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.AbstractRepository;
import org.folio.persist.CampusRepository;
import org.folio.rest.jaxrs.model.LocationCampus;
import org.springframework.stereotype.Component;

@Component
public class CampusDomainEventPublisher extends AbstractDomainEventPublisher<LocationCampus, LocationCampus> {

  private final CampusRepository campusRepository;

  public CampusDomainEventPublisher(DomainEventPublisherFactory publisherFactory,
                                    CampusRepository campusRepository) {
    super(publisherFactory.publisher(CAMPUS));
    this.campusRepository = campusRepository;
  }

  @Override
  protected AbstractRepository<LocationCampus> repository() {
    return campusRepository;
  }

  @Override
  protected Future<List<Pair<String, LocationCampus>>> getRecordIds(Collection<LocationCampus> campuses,
                                                                    Map<String, String> headers) {
    return Future.succeededFuture(campuses.stream()
      .map(campus -> pair(campus.getId(), campus))
      .toList()
    );
  }

  @Override
  protected LocationCampus convertDomainToEvent(String id, LocationCampus campus) {
    return campus;
  }

  @Override
  protected String getId(LocationCampus campus) {
    return campus.getId();
  }
}
