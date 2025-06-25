package org.folio.persist;

import static org.folio.rest.impl.LocationUnitApi.CAMPUS_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.jaxrs.model.Loccamp;

public class CampusRepository extends AbstractRepository<Loccamp> {

  public CampusRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), CAMPUS_TABLE, Loccamp.class);
  }
}
