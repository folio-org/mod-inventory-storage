package org.folio.persist;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.Location;
import org.springframework.stereotype.Repository;

@Repository
public class LocationRepository extends AbstractRepository<Location> {

  public static final String LOCATION_TABLE = "location";

  public LocationRepository(Context context) {
    super(context, LOCATION_TABLE, Location.class);
  }
}
