package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.LOCATION;

import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.AbstractRepository;
import org.folio.persist.LocationRepository;
import org.folio.rest.jaxrs.model.Location;
import org.springframework.stereotype.Component;

@Component
public class LocationDomainEventPublisher extends AbstractDomainEventPublisher<Location, Location> {

  private final LocationRepository locationRepository;

  public LocationDomainEventPublisher(DomainEventPublisherFactory publisherFactory,
                                      LocationRepository locationRepository) {
    super(publisherFactory.publisher(LOCATION));
    this.locationRepository = locationRepository;
  }

  @Override
  protected AbstractRepository<Location> repository() {
    return locationRepository;
  }

  @Override
  protected Future<List<Pair<String, Location>>> getRecordIds(Collection<Location> locations,
                                                              Map<String, String> headers) {
    return succeededFuture(locations.stream()
      .map(location -> pair(location.getId(), location))
      .toList()
    );
  }

  @Override
  protected Location convertDomainToEvent(String id, Location location) {
    return location;
  }

  @Override
  protected String getId(Location location) {
    return location.getId();
  }
}
