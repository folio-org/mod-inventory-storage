package org.folio.persist;

import static org.folio.rest.impl.HoldingsStorageApi.HOLDINGS_RECORD_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.services.location.LocationService.LOCATION_TABLE;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
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
   * Produce a single row where the {@code holdings} column is a text field
   * containing a JSON array with all holdings records; the
   * {@code total_records} column is the exact totalRecords count.
   */
  public Future<Row> getByInstanceId(String instanceId, String[] sortBys, int offset, int limit) {
    return buildOrderByClause(sortBys)
        .map(this::buildHoldingsQuerySql)
        .compose(sql -> postgresClient
            .withReadConn(conn -> conn.execute(sql, Tuple.of(instanceId, offset, limit)))
            .map(rowSet -> rowSet.iterator().next()));
  }

  private Future<String> buildOrderByClause(String[] sortBys) {
    var orderBy = new StringBuilder();
    for (var sortBy : sortBys) {
      if (sortBy.isEmpty()) {
        continue;
      }
      var s = switch (sortBy) {
        case "effectiveLocation.name" -> "name";
        case "callNumberPrefix",
             "callNumber",
             "callNumberSuffix" -> "jsonb->>'" + sortBy + "'";
        default -> null;
      };
      if (s == null) {
        return Future.failedFuture(new IllegalArgumentException("sortBy: " + sortBy));
      }
      if (!orderBy.isEmpty()) {
        orderBy.append(", ");
      }
      orderBy.append(s);
    }
    return Future.succeededFuture(orderBy.toString());
  }

  private String buildHoldingsQuerySql(String orderBy) {
    return "WITH data AS NOT MATERIALIZED ("
        + " SELECT h.jsonb AS jsonb, l.jsonb->>'name' AS name"
        + " FROM " + HOLDINGS_RECORD_TABLE + " h"
        + " LEFT JOIN " + LOCATION_TABLE + " l"
        + "   ON h.effectiveLocationId = l.id"
        + " WHERE h.instanceId=$1"
        + " )"
        + " SELECT json_array("
        + "   SELECT jsonb"
        + "   FROM data"
        + "   ORDER BY " + orderBy
        + "   OFFSET $2"
        + "   LIMIT $3"
        + " )::text AS holdings, ("
        + "   SELECT COUNT(*) AS total_records"
        + "   FROM " + HOLDINGS_RECORD_TABLE
        + "   WHERE instanceId=$1"
        + ")";
  }

  /**
   * Delete by CQL. For each deleted record return a {@link Row} with the instance id String
   * and with the holdings' jsonb String.
   */
  public Future<RowSet<Row>> delete(String cql) {
    try {
      CQLWrapper cqlWrapper = new CQLWrapper(new CQL2PgJSON(tableName + ".jsonb"), cql, -1, -1);
      String sql = "DELETE FROM " + getFullTableName(tableName)
        + " " + cqlWrapper.getWhereClause()
        + " RETURNING instanceId::text, jsonb::text";
      return postgresClient.execute(sql);
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  public Future<List<Map<String, Object>>> getReindexHoldingsRecords(String fromId, String toId) {
    var sql = "SELECT jsonb FROM " + getFullTableName(HOLDINGS_RECORD_TABLE)
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

  public Future<RowSet<Row>> getHoldingsByIds(List<String> holdingsIds) {
    if (holdingsIds.isEmpty()) {
      return Future.succeededFuture();
    }

    var holdingsIdsArray = String.join("','", holdingsIds);
    var sql = """
      SELECT id::text, jsonb::text
      FROM %s
      WHERE id = ANY(ARRAY['%s']::uuid[])
      """.formatted(
      getFullTableName(HOLDINGS_RECORD_TABLE),
      holdingsIdsArray
    );

    return postgresClient.execute(sql);
  }
}
