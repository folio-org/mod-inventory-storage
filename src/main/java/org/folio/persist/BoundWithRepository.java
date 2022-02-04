package org.folio.persist;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.BoundWithPart;

import java.util.Map;

import static org.folio.rest.impl.BoundWithPartAPI.BOUND_WITH_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;

public class BoundWithRepository extends AbstractRepository<BoundWithPart> {
  public BoundWithRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), BOUND_WITH_TABLE, BoundWithPart.class);
  }
}
