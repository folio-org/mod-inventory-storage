package org.folio.services.domainevent;

import static org.folio.InventoryKafkaTopic.LIBRARY;

import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.AbstractRepository;
import org.folio.persist.LibraryRepository;
import org.folio.rest.jaxrs.model.LocationLibrary;
import org.springframework.stereotype.Component;

@Component
public class LibraryDomainEventPublisher extends AbstractDomainEventPublisher<LocationLibrary, LocationLibrary> {

  private final LibraryRepository libraryRepository;

  public LibraryDomainEventPublisher(DomainEventPublisherFactory publisherFactory,
                                     LibraryRepository libraryRepository) {
    super(publisherFactory.publisher(LIBRARY));
    this.libraryRepository = libraryRepository;
  }

  @Override
  protected AbstractRepository<LocationLibrary> repository() {
    return libraryRepository;
  }

  @Override
  protected Future<List<Pair<String, LocationLibrary>>> getRecordIds(Collection<LocationLibrary> loclibs,
                                                                     Map<String, String> headers) {
    return Future.succeededFuture(loclibs.stream()
      .map(library -> pair(library.getId(), library))
      .toList());
  }

  @Override
  protected LocationLibrary convertDomainToEvent(String instanceId, LocationLibrary loclib) {
    return loclib;
  }

  @Override
  protected String getId(LocationLibrary loclib) {
    return loclib.getId();
  }
}
