package org.folio.services.domainevent;

import io.vertx.core.Context;
import io.vertx.core.Future;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.LibraryRepository;
import org.folio.rest.jaxrs.model.Loclib;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.folio.InventoryKafkaTopic.LIBRARY;
import static org.folio.rest.tools.utils.TenantTool.tenantId;


public class LibraryDomainEventPublisher extends AbstractDomainEventPublisher<Loclib, Loclib> {

  public LibraryDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new LibraryRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders, LIBRARY.fullTopicName(tenantId(okapiHeaders))));
  }

  @Override
  protected Future<List<Pair<String, Loclib>>> getRecordIds(Collection<Loclib> loclibs) {
    return Future.succeededFuture(loclibs.stream()
      .map(library -> pair(library.getId(), library))
      .toList());
  }

  @Override
  protected Loclib convertDomainToEvent(String instanceId, Loclib loclib) {
    return loclib;
  }

  @Override
  protected String getId(Loclib loclib) {
    return loclib.getId();
  }
}
