package org.folio.persist;

import static org.folio.rest.impl.HoldingsStorageApi.HOLDINGS_RECORD_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.persist.cql.CQLWrapper;

public class HoldingsRepository extends AbstractRepository<HoldingsRecord> {
  public HoldingsRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), HOLDINGS_RECORD_TABLE, HoldingsRecord.class);
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

  public Future<List<Map<String, Object>>> getReindexHoldingsRecords(String fromId, String toId) {
    var sql = "SELECT jsonb FROM " + postgresClientFuturized.getFullTableName(HOLDINGS_RECORD_TABLE)
                 + " i WHERE id >= '" + fromId + "' AND id <= '" + toId + "'"
                 + ";";
    return postgresClient.select(sql).map(rows -> {
      var resultList = new LinkedList<Map<String, Object>>();
      for (var row : rows) {
        resultList.add(row.getJsonObject(0).getMap());
      }
      return resultList;
    });
  }
}
