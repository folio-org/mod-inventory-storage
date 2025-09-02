package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.services.locationunit.LibraryService.LIBRARY_TABLE;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.jaxrs.model.Loclib;
import org.folio.rest.persist.interfaces.Results;

public class LibraryRepository extends AbstractRepository<Loclib> {

  public LibraryRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), LIBRARY_TABLE, Loclib.class);
  }

  public Future<Results<Loclib>> getByQuery(String cql, int offset, int limit, String totalRecords,
                                            boolean includeShadow) throws FieldException {
    var cqlForIsShadowField = Boolean.FALSE.equals(includeShadow) ? "isShadow=false" : null;
    var cqlWrapper = getFetchCqlWrapper(cql, offset, limit, totalRecords, cqlForIsShadowField);
    return postgresClient.get(LIBRARY_TABLE, recordType, cqlWrapper, true);
  }
}
