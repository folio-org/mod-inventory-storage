package org.folio.persist;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.LocationLibrary;
import org.springframework.stereotype.Repository;

@Repository
public class LibraryRepository extends AbstractRepository<LocationLibrary> {

  public static final String LIBRARY_TABLE = "loclibrary";

  public LibraryRepository(Context context) {
    super(context, LIBRARY_TABLE, LocationLibrary.class);
  }
}
