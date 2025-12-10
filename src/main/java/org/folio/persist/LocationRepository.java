package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.services.location.LocationService.LOCATION_TABLE;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.persist.interfaces.Results;

public class LocationRepository extends AbstractRepository<Location> {

  public LocationRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), LOCATION_TABLE, Location.class);
  }

  public Future<Results<Location>> getByQuery(String cql, int offset, int limit, String totalRecords,
                                              boolean includeShadowLocations) throws FieldException {
    var cqlForIsShadowField = Boolean.FALSE.equals(includeShadowLocations) ? "isShadow=false" : null;
    var cqlWrapper = getFetchCqlWrapper(cql, offset, limit, totalRecords, cqlForIsShadowField);
    return postgresClient.get(LOCATION_TABLE, recordType, cqlWrapper, true);
  }
}
