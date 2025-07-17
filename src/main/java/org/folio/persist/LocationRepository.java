package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.services.location.LocationService.LOCATION_TABLE;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;

public class LocationRepository extends AbstractRepository<Location> {

  public LocationRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), LOCATION_TABLE, Location.class);
  }

  public Future<Results<Location>> getByQuery(String cql, int offset, int limit, String totalRecords,
                                              boolean includeShadowLocations) throws FieldException {
    var cqlWrapper = getFetchCqlWrapper(cql, offset, limit, totalRecords, includeShadowLocations);
    return postgresClient.get(LOCATION_TABLE, recordType, cqlWrapper, true);
  }

  private CQLWrapper getFetchCqlWrapper(String cql, int offset, int limit, String totalRecords, boolean includeShadows)
    throws FieldException {
    var field = new CQL2PgJSON(LOCATION_TABLE + ".jsonb");
    if (StringUtils.isBlank(cql)) {
      return new CQLWrapper(field, "isShadow=" + includeShadows, limit, offset, totalRecords);
    }

    var cqlWrapper = new CQLWrapper(field, cql, limit, offset, totalRecords);
    var cqlWrapperForShadowLocations = new CQLWrapper(field, "isShadow=" + includeShadows);
    return cqlWrapper.addWrapper(cqlWrapperForShadowLocations);
  }
}
