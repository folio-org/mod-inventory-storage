package org.folio.persist;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.LocationCampus;
import org.springframework.stereotype.Repository;

@Repository
public class CampusRepository extends AbstractRepository<LocationCampus> {

  public static final String CAMPUS_TABLE = "loccampus";

  public CampusRepository(Context context) {
    super(context, CAMPUS_TABLE, LocationCampus.class);
  }
}
