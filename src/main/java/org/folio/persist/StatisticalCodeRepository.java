package org.folio.persist;

import static org.folio.rest.impl.StatisticalCodeAPI.REFERENCE_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;

import java.util.Map;

import org.folio.rest.jaxrs.model.StatisticalCode;

import io.vertx.core.Context;

public class StatisticalCodeRepository extends AbstractRepository<StatisticalCode> {
  public StatisticalCodeRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), REFERENCE_TABLE, StatisticalCode.class);
  }
}
