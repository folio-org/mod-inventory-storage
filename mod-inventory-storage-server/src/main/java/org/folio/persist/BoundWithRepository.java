package org.folio.persist;

import static org.folio.rest.impl.BoundWithPartApi.BOUND_WITH_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.jaxrs.model.BoundWithPart;

public class BoundWithRepository extends AbstractRepository<BoundWithPart> {
  public BoundWithRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), BOUND_WITH_TABLE, BoundWithPart.class);
  }
}
