package org.folio.services.domainevent;

import static org.folio.InventoryKafkaTopic.LIBRARY;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.LibraryRepository;
import org.folio.rest.jaxrs.model.LocationLibrary;

public class LibraryDomainEventPublisher extends AbstractDomainEventPublisher<LocationLibrary, LocationLibrary> {

  public LibraryDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new LibraryRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders, LIBRARY.fullTopicName(tenantId(okapiHeaders))));
  }

  @Override
  protected Future<List<Pair<String, LocationLibrary>>> getRecordIds(Collection<LocationLibrary> loclibs) {
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
