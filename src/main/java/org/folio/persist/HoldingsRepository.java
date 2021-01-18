package org.folio.persist;

import static org.folio.rest.impl.HoldingsStorageAPI.HOLDINGS_RECORD_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;

import java.util.List;
import java.util.Map;

import org.folio.rest.jaxrs.model.HoldingsRecord;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class HoldingsRepository extends AbstractRepository<HoldingsRecord> {
  public HoldingsRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), HOLDINGS_RECORD_TABLE, HoldingsRecord.class);
  }

  public Future<List<HoldingsRecord>> getAll() {
    return postgresClientFuturized.get(tableName, new HoldingsRecord());
  }
}
