package org.folio.persist;

import static org.folio.rest.impl.HoldingsStorageAPI.HOLDINGS_RECORD_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;

import java.util.Map;

import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Context;

public class HoldingsRepository extends AbstractRepository<HoldingsRecord> {
  public HoldingsRepository(Context context, Map<String, String> okapiHeaders) {
    this(postgresClient(context, okapiHeaders));
  }

  public HoldingsRepository(PostgresClient postgresClient) {
    super(postgresClient, HOLDINGS_RECORD_TABLE, HoldingsRecord.class);
  }
}
