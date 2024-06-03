package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.services.locationunit.InstitutionService.INSTITUTION_TABLE;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.jaxrs.model.Locinst;

public class InstitutionRepository extends AbstractRepository<Locinst> {

  public InstitutionRepository(Context context,
                               Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), INSTITUTION_TABLE,
      Locinst.class);
  }
}
