package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.services.locationunit.InstitutionService.INSTITUTION_TABLE;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.jaxrs.model.Locinst;
import org.folio.rest.persist.interfaces.Results;

public class InstitutionRepository extends AbstractRepository<Locinst> {

  public InstitutionRepository(Context context,
                               Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), INSTITUTION_TABLE, Locinst.class);
  }

  public Future<Results<Locinst>> getByQuery(String cql, int offset, int limit, String totalRecords,
                                             boolean includeShadow) throws FieldException {
    var cqlForIsShadowField = Boolean.FALSE.equals(includeShadow) ? "isShadow=false" : null;
    var cqlWrapper = getFetchCqlWrapper(cql, offset, limit, totalRecords, cqlForIsShadowField);
    return postgresClient.get(INSTITUTION_TABLE, recordType, cqlWrapper, true);
  }
}
