package org.folio.persist;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;

/**
 * Database access of the custom field value cleanup: its queue {@code custom_fields_cleanup}, the records
 * holding a value of a queued field, and the refId placeholders. An instance is bound to one connection,
 * so session state such as the advisory lock lasts across its statements.
 */
public final class CustomFieldsCleanupRepository {

  /**
   * The entityType of the refId placeholders, which no client uses. The trigger in
   * {@code create_custom_fields_cleanup.sql} inserts them and explains what they are for.
   */
  public static final String PLACEHOLDER_ENTITY_TYPE = "pendingValueCleanup";
  /**
   * The key of the advisory lock that a pass holds on the tenant, with the schema name as {@code $1}.
   */
  public static final String LOCK_KEY = "hashtext('custom_fields_cleanup'), hashtext($1)";
  private static final String HOLDS_VALUE = "jsonb->'customFields' ? $1";

  private final Conn conn;
  private final String schema;

  private CustomFieldsCleanupRepository(Conn conn, String schema) {
    this.conn = conn;
    this.schema = schema;
  }

  /**
   * Runs the function with a repository bound to one connection of the tenant, which returns to the pool
   * when the function's future completes.
   */
  public static <T> Future<T> withConnection(Vertx vertx, String tenantId,
                                             Function<CustomFieldsCleanupRepository, Future<T>> function) {
    var schema = PostgresClient.convertToPsqlStandard(tenantId);
    return PostgresClient.getInstance(vertx, tenantId)
      .withConn(conn -> function.apply(new CustomFieldsCleanupRepository(conn, schema)));
  }

  public String schema() {
    return schema;
  }

  /** Takes the tenant's cleanup lock for this session, unless another session holds it. */
  public Future<Boolean> tryLock() {
    return conn.execute("SELECT pg_try_advisory_lock(" + LOCK_KEY + ")", Tuple.of(schema))
      .map(rows -> Boolean.TRUE.equals(rows.iterator().next().getBoolean(0)));
  }

  public Future<Void> unlock() {
    return conn.execute("SELECT pg_advisory_unlock(" + LOCK_KEY + ")", Tuple.of(schema))
      .mapEmpty();
  }

  /** Lets the session's commits return before their WAL is flushed. */
  public Future<Void> commitAsynchronously() {
    return conn.execute("SET synchronous_commit = off")
      .mapEmpty();
  }

  public Future<Void> resetCommitMode() {
    return conn.execute("RESET synchronous_commit")
      .mapEmpty();
  }

  /** Returns the queued removals in queue order, without the excluded ones. */
  public Future<List<QueuedRemoval>> readQueue(Collection<UUID> excluded) {
    return conn.execute("SELECT id, entity_type, ref_id FROM " + schema + ".custom_fields_cleanup"
        + " WHERE id <> ALL($1) ORDER BY created_date", Tuple.of(excluded.toArray(UUID[]::new)))
      .map(rows -> {
        var removals = new ArrayList<QueuedRemoval>(rows.rowCount());
        for (Row row : rows) {
          removals.add(new QueuedRemoval(row.getUUID("id"), row.getString("entity_type"), row.getString("ref_id")));
        }
        return removals;
      });
  }

  /**
   * Returns the ids of up to {@code limit} records that hold a value of the field, without the excluded ones.
   */
  public Future<List<UUID>> readHolders(String table, String refId, int limit, Collection<UUID> excluded) {
    return conn.execute("SELECT id FROM " + schema + "." + table + " WHERE " + HOLDS_VALUE
        + " AND id <> ALL($3) LIMIT $2", Tuple.of(refId, limit, excluded.toArray(UUID[]::new)))
      .map(rows -> {
        var ids = new ArrayList<UUID>(rows.rowCount());
        for (Row row : rows) {
          ids.add(row.getUUID(0));
        }
        return ids;
      });
  }

  /**
   * Removes the field's value from the record in a statement of its own and returns the number of records
   * changed, 0 if the record no longer holds the value.
   */
  public Future<Integer> removeValue(String table, String refId, UUID id) {
    // a write after the read may have removed the value, or all of customFields, which would make the jsonb NULL
    return conn.execute("UPDATE " + schema + "." + table
        + " SET jsonb = jsonb_set(jsonb, '{customFields}', (jsonb->'customFields') - $1)"
        + " WHERE id = $2 AND " + HOLDS_VALUE, Tuple.of(refId, id))
      .map(RowSet::rowCount);
  }

  /** Counts a failed attempt at the queued removal and records its cause. */
  public Future<Void> recordFailure(UUID id, String message) {
    return conn.execute("UPDATE " + schema + ".custom_fields_cleanup"
        + " SET attempts = attempts + 1, last_error = $2, last_attempt_date = now() WHERE id = $1",
        Tuple.of(id, message))
      .mapEmpty();
  }

  /** Deletes the queue row and the placeholder of its refId in one statement, so neither outlives the other. */
  public Future<Void> deleteQueueRow(UUID id, String refId) {
    return conn.execute("WITH queue_row AS (DELETE FROM " + schema + ".custom_fields_cleanup WHERE id = $1)"
        + " DELETE FROM " + schema + ".custom_fields WHERE jsonb->>'entityType' = $2 AND jsonb->>'refId' = $3",
        Tuple.of(id, PLACEHOLDER_ENTITY_TYPE, refId))
      .mapEmpty();
  }

  /**
   * A removal of a deleted field's values, queued in {@code custom_fields_cleanup}.
   */
  public record QueuedRemoval(UUID id, String entityType, String refId) {
  }
}
