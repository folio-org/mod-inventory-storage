package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.LOCATION;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.LocationRepository;
import org.folio.rest.jaxrs.model.Location;

public class LocationDomainEventPublisher extends AbstractDomainEventPublisher<Location, Location> {

  public LocationDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new LocationRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders, LOCATION.fullTopicName(tenantId(okapiHeaders))));
  }

  @Override
  protected Future<List<Pair<String, Location>>> getRecordIds(Collection<Location> locations) {
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
