package org.folio.persist;

import static org.folio.rest.impl.HoldingsStorageApi.HOLDINGS_RECORD_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.List;
import java.util.Map;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.persist.cql.CQLWrapper;

public class HoldingsRepository extends AbstractRepository<HoldingsRecord> {
  public HoldingsRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), HOLDINGS_RECORD_TABLE, HoldingsRecord.class);
  }

  /**
   * Upsert holdings records.
   *
   * <p>Returns
   * { "holdingsRecords": [{"old": {...}, "new": {...}}, ...],
   *   "items":           [{"old": {...}, "new": {...}}, ...]
   * }
   * providing old and new jsonb content of all updated holdings and items. For a newly
   * inserted holding only "new" is provided.
   *
   * @param holdingsRecords records to insert or update (upsert)
   */
  public Future<JsonObject> upsert(List<HoldingsRecord> holdingsRecords) {
    try {
      var array = ObjectMapperTool.getMapper().writeValueAsString(holdingsRecords);
      return postgresClient.selectSingle("SELECT upsert_holdings($1::text::jsonb)", Tuple.of(array))
          .map(row -> row.getJsonObject(0));
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  /**
   * Delete by CQL. For each deleted record return a {@link Row} with the instance id String
   * and with the holdings' jsonb String.
   */
  public Future<RowSet<Row>> delete(String cql) {
    try {
      CQLWrapper cqlWrapper = new CQLWrapper(new CQL2PgJSON(tableName + ".jsonb"), cql, -1, -1);
      String sql = "DELETE FROM " + postgresClientFuturized.getFullTableName(tableName)
        + " " + cqlWrapper.getWhereClause()
        + " RETURNING instanceId::text, jsonb::text";
      return postgresClient.execute(sql);
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }
}
