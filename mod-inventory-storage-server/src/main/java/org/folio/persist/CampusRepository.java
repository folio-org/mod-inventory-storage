package org.folio.persist;

import static org.folio.rest.impl.LocationUnitApi.CAMPUS_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.jaxrs.model.LocationCampus;
import org.folio.rest.persist.interfaces.Results;

public class CampusRepository extends AbstractRepository<LocationCampus> {

  public CampusRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), CAMPUS_TABLE, LocationCampus.class);
  }

  public Future<Results<LocationCampus>> getByQuery(String cql, int offset, int limit, String totalRecords,
                                            boolean includeShadow) throws FieldException {
    var cqlForIsShadowField = Boolean.FALSE.equals(includeShadow) ? "isShadow=false" : null;
    var cqlWrapper = getFetchCqlWrapper(cql, offset, limit, totalRecords, cqlForIsShadowField);
    return postgresClient.get(CAMPUS_TABLE, recordType, cqlWrapper, true);
  }
}
