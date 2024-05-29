package org.folio.persist;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.Loclib;

import java.util.Map;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.services.locationunit.LibraryService.LIBRARY_TABLE;


public class LibraryRepository extends AbstractRepository<Loclib> {

  public LibraryRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), LIBRARY_TABLE, Loclib.class);
  }

}
