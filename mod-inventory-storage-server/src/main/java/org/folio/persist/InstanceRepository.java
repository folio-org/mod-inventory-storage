package org.folio.persist;

import static org.folio.persist.HoldingsRepository.HOLDINGS_RECORD_TABLE;
import static org.folio.rest.impl.BoundWithPartApi.BOUND_WITH_TABLE;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgException;
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
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.entity.DeletedRecord;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.utils.DatabaseUtils;
import org.springframework.stereotype.Repository;

@Repository
public class InstanceRepository extends AbstractRepository<Instance> {
  public static final String INSTANCE_TABLE = "instance";
  private static final String INSTANCE_SUBJECT_SOURCE_TABLE = "instance_subject_source";
  private static final String INSTANCE_SUBJECT_TYPE_TABLE = "instance_subject_type";

  public InstanceRepository(Context context) {
    super(context, INSTANCE_TABLE, Instance.class);
  }

  public Future<Void> unlinkInstanceFromSubjectSource(Conn conn, String instanceId, String tenantId) {
    try {
      String sql = unlinkInstanceFromSubjectSql(INSTANCE_SUBJECT_SOURCE_TABLE, instanceId, tenantId);
      return conn.execute(sql).mapEmpty();
    } catch (PgException e) {
      return Future.failedFuture(new BadRequestException(e.getMessage()));
    }
  }

  public Future<Void> unlinkInstanceFromSubjectType(Conn conn, String instanceId, String tenantId) {
    try {
      String sql = unlinkInstanceFromSubjectSql(INSTANCE_SUBJECT_TYPE_TABLE, instanceId, tenantId);
      return conn.execute(sql).mapEmpty();
    } catch (PgException e) {
      return Future.failedFuture(new BadRequestException(e.getMessage()));
    }
  }

  public Future<Void> batchLinkSubjectSource(Conn conn, List<Pair<String, String>> sourcePairs, String tenantId) {
    try {
      String sql = """
        INSERT INTO %s (instance_id, source_id)
        VALUES %s
        ON CONFLICT DO NOTHING;
        """
        .formatted(
          getFullTableName(INSTANCE_SUBJECT_SOURCE_TABLE, tenantId),
          sourcePairs.stream()
            .map(pair -> String.format("('%s', '%s')", pair.getKey(), pair.getValue()))
            .collect(Collectors.joining(", "))
        );
      return conn.execute(sql).mapEmpty();
    } catch (PgException e) {
      return Future.failedFuture(new BadRequestException(e.getMessage()));
    }
  }

  public Future<Instance> createInstance(Conn conn, Instance instance) {
    return conn.save(INSTANCE_TABLE, instance.getId(), instance)
      .map(instance::withId);
  }

  /**
   * Returns the summary of the instance, or null if there is no such instance.
   */
  public Future<JsonObject> getInstanceSummary(String id, String tenantId) {
    return postgresClient(tenantId).execute("SELECT get_instance_summary($1::uuid) AS summary", Tuple.of(id))
      .map(rows -> {
        var iterator = rows.iterator();
        return iterator.hasNext() ? iterator.next().getJsonObject("summary") : null;
      });
  }

  public Future<Void> batchLinkSubjectType(Conn conn, List<Pair<String, String>> typePairs, String tenantId) {
    try {
      String sql = """
        INSERT INTO %s (instance_id, type_id)
        VALUES %s
        ON CONFLICT DO NOTHING;
        """
        .formatted(
          getFullTableName(INSTANCE_SUBJECT_TYPE_TABLE, tenantId),
          typePairs.stream()
            .map(pair -> String.format("('%s', '%s')", pair.getKey(), pair.getValue()))
            .collect(Collectors.joining(", "))
        );
      return conn.execute(sql).mapEmpty();
    } catch (PgException e) {
      return Future.failedFuture(new BadRequestException(e.getMessage()));
    }
  }

  public Future<Void> batchUnlinkSubjectSource(Conn conn, String instanceId, List<String> sourceIds, String tenantId) {
    try {
      String sql = """
        DELETE FROM %s WHERE instance_id = '%s' AND source_id IN ( %s );
        """.formatted(
        getFullTableName(INSTANCE_SUBJECT_SOURCE_TABLE, tenantId),
        instanceId,
        sourceIds.stream().map(id -> "'" + id + "'").collect(Collectors.joining(", "))
      );
      return conn.execute(sql).mapEmpty();
    } catch (PgException e) {
      return Future.failedFuture(new BadRequestException(e.getMessage()));
    }
  }

  public Future<Void> batchUnlinkSubjectType(Conn conn, String instanceId, List<String> typeIds, String tenantId) {
    try {
      String sql = """
        DELETE FROM %s WHERE instance_id = '%s' AND type_id IN ( %s );
        """
        .formatted(
          getFullTableName(INSTANCE_SUBJECT_TYPE_TABLE, tenantId),
          instanceId,
          typeIds.stream().map(id -> "'" + id + "'")
            .collect(Collectors.joining(", "))
        );
      return conn.execute(sql).mapEmpty();
    } catch (PgException e) {
      return Future.failedFuture(new BadRequestException(e.getMessage()));
    }
  }

  /**
   * Returns the {@code source} of each existing instance of the ids, keyed by instance id.
   */
  public Future<Map<String, String>> getSourcesByIds(Collection<String> instanceIds, String tenantId) {
    var ids = instanceIds.stream().filter(Objects::nonNull).distinct().map(UUID::fromString).toArray(UUID[]::new);
    if (ids.length == 0) {
      return Future.succeededFuture(Map.of());
    }
    var sql = "SELECT id::text, jsonb->>'source' FROM " + getFullTableName(tenantId) + " WHERE id = ANY($1)";
    return postgresClient(tenantId).execute(sql, Tuple.tuple().addArrayOfUUID(ids))
      .map(rows -> {
        var sources = new HashMap<String, String>();
        rows.forEach(row -> sources.put(row.getString(0), row.getString(1)));
        return sources;
      });
  }

  public Future<RowStream<Row>> getAllIds(Conn connection, String tenantId) {
    var query = "SELECT id FROM " + getFullTableName(tenantId);
    return DatabaseUtils.selectStream(connection, query);
  }

  /**
   * Delete by CQL. For each deleted record return a {@link DeletedRecord} with the instance id
   * and  the instance jsonb String.
   *
   * <p>This automatically deletes connected marc records because the instance_source_marc foreign
   * key has "ON DELETE CASCADE".
   */
  public Future<List<DeletedRecord>> delete(String cql, String tenantId) {
    try {
      CQLWrapper cqlWrapper = cqlWrapper(cql);
      String sql = "DELETE FROM " + getFullTableName(tenantId)
                   + " " + cqlWrapper.getWhereClause()
                   + " RETURNING id::text, jsonb::text";
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

  public Future<List<Map<String, Object>>> getReindexInstances(String fromId, String toId,
                                                               boolean notConsortiumRecords,
                                                               String tenantId) {
    return postgresClient(tenantId)
      .select(buildReindexInstancesSql(fromId, toId, notConsortiumRecords, tenantId))
      .map(rows -> rows.stream()
        .map(row -> row.getJsonObject(0).getMap())
        .collect(Collectors.toCollection(LinkedList::new)));
  }

  public Future<RowStream<Row>> streamReindexInstances(Conn conn, String fromId, String toId,
                                                       boolean notConsortiumRecords, String tenantId) {
    return DatabaseUtils.selectStream(conn, buildReindexInstancesSql(fromId, toId, notConsortiumRecords, tenantId));
  }

  private String buildReindexInstancesSql(String fromId, String toId, boolean notConsortiumRecords, String tenantId) {
    var sql = new StringBuilder("WITH bound_instances AS MATERIALIZED (");
    sql.append("SELECT DISTINCT hr.instanceId FROM ");
    sql.append(getFullTableName(BOUND_WITH_TABLE, tenantId));
    sql.append(" as bw JOIN ");
    sql.append(getFullTableName(HOLDINGS_RECORD_TABLE, tenantId));
    sql.append(" as hr ON hr.id = bw.holdingsrecordid");
    sql.append(" WHERE hr.instanceId >= '").append(fromId).append("' AND hr.instanceId <= '").append(toId).append("'");
    sql.append(") ");
    sql.append("SELECT i.jsonb || jsonb_build_object('isBoundWith', (bi.instanceId IS NOT NULL)) FROM ");
    sql.append(getFullTableName(tenantId));
    sql.append(" i LEFT JOIN bound_instances bi ON i.id = bi.instanceId");
    sql.append(" WHERE i.id >= '").append(fromId).append("' AND i.id <= '").append(toId).append("'");
    if (notConsortiumRecords) {
      sql.append(" AND i.jsonb->>'source' NOT LIKE 'CONSORTIUM-%'");
    }
    sql.append(";");
    return sql.toString();
  }

  private String unlinkInstanceFromSubjectSql(String table, String id, String tenantId) {
    return String.format("DELETE FROM %s WHERE instance_id = '%s'; ", getFullTableName(table, tenantId), id);
  }
}
