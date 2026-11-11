package org.folio.persist;

import static org.folio.dbschema.ObjectMapperTool.readValue;
import static org.folio.persist.InstanceRepository.INSTANCE_TABLE;
import static org.folio.persist.LocationRepository.LOCATION_TABLE;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.folio.persist.entity.DeletedRecord;
import org.folio.persist.entity.HoldingsInstance;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.persist.Conn;
import org.folio.utils.DatabaseUtils;
import org.springframework.stereotype.Repository;

@Repository
public class HoldingsRepository extends AbstractRepository<HoldingsRecord> {

  public static final String HOLDINGS_RECORD_TABLE = "holdings_record";

  public HoldingsRepository(Context context) {
    super(context, HOLDINGS_RECORD_TABLE, HoldingsRecord.class);
  }

  /**
   * Produce a single row where the {@code holdings} column is a text field
   * containing a JSON array with all holdings records; the
   * {@code total_records} column is the exact totalRecords count.
   */
  public Future<Row> getByInstanceId(String instanceId, String[] sortBys, int offset, int limit, String tenantId) {
    return buildOrderByClause(sortBys)
      .map(this::buildHoldingsQuerySql)
      .compose(sql -> postgresClient(tenantId)
        .withReadConn(conn -> conn.execute(sql, Tuple.of(instanceId, offset, limit)))
        .map(rowSet -> rowSet.iterator().next()));
  }

  /**
   * Delete by CQL. For each deleted record return a {@link DeletedRecord} with the instance id
   * and  the holdings' jsonb String.
   */
  public Future<List<DeletedRecord>> delete(String cql, String tenantId) {
    try {
      var fullTableName = getFullTableName(tenantId);
      var whereClause = cqlWrapper(cql).getWhereClause();
      var sql = "DELETE FROM " + fullTableName + " " + whereClause + " RETURNING instanceId::text, jsonb::text";
      return postgresClient(tenantId).execute(sql)
        .map(rows -> {
          var deleted = new ArrayList<DeletedRecord>(rows.size());
          rows.forEach(row -> deleted.add(new DeletedRecord(row.getString(0), row.getString(1))));
          return deleted;
        });
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  public Future<List<Map<String, Object>>> getReindexHoldingsRecords(String fromId, String toId, String tenantId) {
    return postgresClient(tenantId).select(buildReindexHoldingsSql(fromId, toId, tenantId))
      .map(rows -> rows.stream()
        .map(row -> row.getJsonObject(0).getMap())
        .collect(Collectors.toCollection(LinkedList::new)));
  }

  public Future<RowStream<Row>> streamReindexHoldingsRecords(Conn conn, String fromId, String toId, String tenantId) {
    return DatabaseUtils.selectStream(conn, buildReindexHoldingsSql(fromId, toId, tenantId));
  }

  /**
   * Returns the instance of each existing holdings record of the ids, keyed by holdings record id.
   */
  public Future<Map<String, HoldingsInstance>> getInstancesByHoldingsIds(Collection<String> holdingsIds,
                                                                         String tenantId) {
    var ids = holdingsIds.stream().filter(Objects::nonNull).distinct().map(UUID::fromString).toArray(UUID[]::new);
    if (ids.length == 0) {
      return Future.succeededFuture(Map.of());
    }
    var sql = "SELECT hr.id::text, hr.instanceId::text, inst.jsonb->>'source'"
              + " FROM " + getFullTableName(tenantId) + " hr"
              + " JOIN " + getFullTableName(INSTANCE_TABLE, tenantId) + " inst ON inst.id = hr.instanceId"
              + " WHERE hr.id = ANY($1)";
    return postgresClient(tenantId).execute(sql, Tuple.tuple().addArrayOfUUID(ids))
      .map(rows -> {
        var instances = new HashMap<String, HoldingsInstance>();
        rows.forEach(row -> instances.put(row.getString(0), new HoldingsInstance(row.getString(1), row.getString(2))));
        return instances;
      });
  }

  public Future<List<HoldingsRecord>> getHoldingsByIds(List<String> holdingsIds, String tenantId) {
    if (holdingsIds.isEmpty()) {
      return Future.succeededFuture(List.of());
    }

    var holdingsIdsArray = String.join("','", holdingsIds);
    var sql = """
      SELECT jsonb::text
      FROM %s
      WHERE id = ANY(ARRAY['%s']::uuid[])
      """.formatted(getFullTableName(tenantId), holdingsIdsArray);

    return postgresClient(tenantId).execute(sql)
      .map(rowSet -> {
        var result = new ArrayList<HoldingsRecord>(rowSet.size());
        rowSet.forEach(row -> result.add(readValue(row.getString(0), HoldingsRecord.class)));
        return result;
      });
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

  private String buildReindexHoldingsSql(String fromId, String toId, String tenantId) {
    return "SELECT jsonb FROM " + getFullTableName(tenantId)
           + " i WHERE id >= '" + fromId + "' AND id <= '" + toId + "';";
  }
}
