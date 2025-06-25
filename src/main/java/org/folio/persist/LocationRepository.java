package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.services.location.LocationService.LOCATION_TABLE;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.jaxrs.model.Location;

public class LocationRepository extends AbstractRepository<Location> {

  public LocationRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), LOCATION_TABLE, Location.class);
  }
}
