package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.services.locationunit.LibraryService.LIBRARY_TABLE;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.jaxrs.model.Loclib;

public class LibraryRepository extends AbstractRepository<Loclib> {

  public LibraryRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), LIBRARY_TABLE, Loclib.class);
  }
}
