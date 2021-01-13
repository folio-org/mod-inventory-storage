package org.folio.persist;

import static org.folio.rest.impl.HoldingsStorageAPI.HOLDINGS_RECORD_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import java.util.List;
import java.util.Map;

import org.folio.rest.jaxrs.model.HoldingsRecord;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class HoldingsRepository extends AbstractRepository<HoldingsRecord> {
  public HoldingsRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), HOLDINGS_RECORD_TABLE, HoldingsRecord.class,
      tenantId(okapiHeaders));
  }

  public Future<List<HoldingsRecord>> getAll() {
    return postgresClientFuturized.get(tableName, new HoldingsRecord());
  }
}
