package org.folio.services.customfields;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.record.RecordServiceFactoryImpl;
import org.folio.utils.Environment;

/**
 * Removes the values of deleted custom fields queued in {@code custom_fields_cleanup}. A pass
 * starts after each field delete and on each {@code _timer} call. The timer's passes pick up the rows that
 * other passes leave behind: after a restart, after a failed row or pass, or a row queued after the running
 * pass last read the queue.
 */
public final class CustomFieldsValueCleanup {

  public static final String BATCH_SIZE_KEY = "CUSTOM_FIELDS_CLEANUP_BATCH_SIZE";
  /**
   * The entityType of the refId placeholders, which no client uses. The trigger in
   * {@code create_custom_fields_cleanup.sql} inserts them and explains what they are for.
   */
  public static final String PLACEHOLDER_ENTITY_TYPE = "pendingValueCleanup";
  private static final int DEFAULT_BATCH_SIZE = 1000;
  private static final String LOCK_KEY = "hashtext('custom_fields_cleanup'), hashtext($1)";
  private static final String HOLDS_VALUE = "jsonb->'customFields' ? $1";

  private static final Logger log = LogManager.getLogger(CustomFieldsValueCleanup.class);

  private CustomFieldsValueCleanup() {
  }

  /**
   * Starts a pass over the tenant's queue. It starts inside the future chain, so an exception thrown before
   * {@code processQueue} returns a future (for example {@code PostgresClient.getInstance} for tenant
   * {@code public}) is logged instead of thrown to the caller.
   */
  public static void kick(Vertx vertx, String tenantId) {
    Future.<Void>succeededFuture()
      .compose(x -> processQueue(vertx, tenantId))
      .onFailure(e -> log.error("kick:: custom field value cleanup failed [tenant={}]", tenantId, e));
  }

  private static Future<Void> processQueue(Vertx vertx, String tenantId) {
    var pg = PostgresClient.getInstance(vertx, tenantId);
    var schema = PostgresClient.convertToPsqlStandard(tenantId);
    return pg.withConn(conn -> processQueueLocked(conn, schema));
  }

  /**
   * Runs the pass under a session advisory lock on the tenant, held on this connection across the
   * pass's transactions, so one pass per tenant runs across all module instances. A pass that finds
   * the lock held leaves the queue to the holder, which reads the queue again before it ends.
   *
   * <p>The pass also commits asynchronously, since it commits once per record and waiting for each commit
   * to be flushed would make it several times slower. A crash can undo the latest removals, but only
   * together with everything committed after them, including the deletion of their queue row, so the
   * next pass removes them again. The pass resets the commit mode and releases the lock when it ends, before
   * the connection returns to the pool.
   */
  private static Future<Void> processQueueLocked(Conn conn, String schema) {
    var lockKey = Tuple.of(schema);
    return conn.execute("SELECT pg_try_advisory_lock(" + LOCK_KEY + ")", lockKey)
      .compose(locked -> {
        if (!Boolean.TRUE.equals(locked.iterator().next().getBoolean(0))) {
          log.info("processQueueLocked:: custom field value cleanup already runs [schema={}]", schema);
          return Future.succeededFuture();
        }
        return conn.execute("SET synchronous_commit = off")
          .compose(x -> processRows(conn, schema))
          .eventually(() -> resetCommitMode(conn, schema))
          .eventually(() -> unlock(conn, lockKey, schema));
      });
  }

  private static Future<Void> resetCommitMode(Conn conn, String schema) {
    return conn.execute("RESET synchronous_commit")
      .<Void>mapEmpty()
      .recover(e -> {
        log.warn("resetCommitMode:: resetting synchronous_commit failed [schema={}]", schema, e);
        return Future.succeededFuture();
      });
  }

  private static Future<Void> unlock(Conn conn, Tuple lockKey, String schema) {
    return conn.execute("SELECT pg_advisory_unlock(" + LOCK_KEY + ")", lockKey)
      .<Void>mapEmpty()
      .recover(e -> {
        log.warn("unlock:: releasing the custom field value cleanup lock failed [schema={}]", schema, e);
        return Future.succeededFuture();
      });
  }

  /**
   * Processes the queue in order and reads it again until every row it holds has been attempted in this
   * pass, so rows queued during the pass are processed too, and a failed row waits for the next pass.
   */
  private static Future<Void> processRows(Conn conn, String schema) {
    var queueRead = "SELECT id, entity_type, ref_id FROM " + schema
      + ".custom_fields_cleanup WHERE id <> ALL($1) ORDER BY created_date";
    Set<UUID> attempted = new HashSet<>();
    return whileMore(() -> conn.execute(queueRead, Tuple.of(attempted.toArray(UUID[]::new)))
      .compose(queue -> {
        Future<Void> result = Future.succeededFuture();
        for (Row row : queue) {
          var id = row.getUUID("id");
          attempted.add(id);
          result = result.compose(x -> processRow(conn, schema, row)
            .recover(e -> recordFailure(conn, schema, id, e)));
        }
        return result.map(queue.rowCount() > 0);
      }));
  }

  private static Future<Void> processRow(Conn conn, String schema, Row row) {
    var id = row.getUUID("id");
    var refId = row.getString("ref_id");
    var entityType = row.getString("entity_type");
    var table = RecordServiceFactoryImpl.ENTITY_TYPE_TABLES.get(entityType);
    if (table == null) {
      log.warn("processRow:: discarding cleanup row with unsupported entityType [{}]", entityType);
      return deleteFieldRow(conn, schema, id, refId);
    }
    return removeFieldValues(conn, schema, table, refId)
      .compose(x -> deleteFieldRow(conn, schema, id, refId));
  }

  /** Keeps the row for the next pass with the cause; a failure to record it ends the pass. */
  private static Future<Void> recordFailure(Conn conn, String schema, UUID id, Throwable cause) {
    log.error("recordFailure:: removing custom field values failed, kept for the next pass [id={}]", id, cause);
    return conn.execute("UPDATE " + schema + ".custom_fields_cleanup"
        + " SET attempts = attempts + 1, last_error = $2, last_attempt_date = now() WHERE id = $1",
        Tuple.of(id, String.valueOf(cause.getMessage())))
      .mapEmpty();
  }

  /**
   * Updates each record in a transaction of its own, as a single-item write does, so the removal locks the
   * rows that the item triggers lock in the same order as any other item write and adds no deadlock that
   * two item writes could not run into. The records are read in batches.
   *
   * <p>A record whose update fails keeps the value and is left out of the following batches, so it does not
   * hold up the records after it. The removal then fails, which keeps the field queued for the next pass.
   */
  private static Future<Void> removeFieldValues(Conn conn, String schema, String table, String refId) {
    log.info("removeFieldValues:: removing values of custom field [refId={}]", refId);
    var fullTable = schema + "." + table;
    var batch = "SELECT id FROM " + fullTable + " WHERE " + HOLDS_VALUE + " AND id <> ALL($3) LIMIT $2";
    var size = batchSize();
    // a write after the read may have removed the value, or all of customFields, which would make the jsonb NULL
    var removeValue = "UPDATE " + fullTable
      + " SET jsonb = jsonb_set(jsonb, '{customFields}', (jsonb->'customFields') - $1)"
      + " WHERE id = $2 AND " + HOLDS_VALUE;
    var records = new AtomicLong();
    var batches = new AtomicInteger();
    Map<UUID, Throwable> failed = new LinkedHashMap<>();
    var start = System.nanoTime();
    return whileMore(() -> conn.execute(batch, Tuple.of(refId, size, failed.keySet().toArray(UUID[]::new)))
      .compose(ids -> {
        if (ids.rowCount() == 0) {
          return Future.succeededFuture(false);
        }
        batches.incrementAndGet();
        return removeValueOneByOne(conn, removeValue, refId, ids, records, failed).map(true);
      }))
      .compose(v -> endRemoval(refId, records.get(), batches.get(), failed, start));
  }

  private static Future<Void> endRemoval(String refId, long records, int batches, Map<UUID, Throwable> failed,
                                         long start) {
    log.info("endRemoval:: removed values of custom field [refId={}, records={}, failedRecords={}, batches={}, "
        + "durationMs={}]",
      refId, records, failed.size(), batches, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
    if (failed.isEmpty()) {
      return Future.succeededFuture();
    }
    var first = failed.entrySet().iterator().next();
    return Future.failedFuture(new IllegalStateException(failed.size() + " of the records holding the value could not "
      + "be updated, first [id=" + first.getKey() + "]: " + first.getValue().getMessage(), first.getValue()));
  }

  private static Future<Void> removeValueOneByOne(Conn conn, String removeValue, String refId, RowSet<Row> ids,
                                                  AtomicLong records, Map<UUID, Throwable> failed) {
    var remaining = ids.iterator();
    return whileMore(() -> {
      if (!remaining.hasNext()) {
        return Future.succeededFuture(false);
      }
      var id = remaining.next().getUUID(0);
      return conn.execute(removeValue, Tuple.of(refId, id))
        .map(result -> {
          records.addAndGet(result.rowCount());
          return true;
        })
        .recover(e -> {
          var message = e.getMessage();
          log.warn("removeValueOneByOne:: removing the custom field value failed, kept for the next pass "
            + "[refId={}, id={}]: {}", refId, id, message);
          failed.put(id, e);
          return Future.succeededFuture(true);
        });
    });
  }

  /**
   * Runs the step until it yields false. Each step starts the next from its callback, and the last one
   * completes the returned future. Composing the steps instead would chain one future per step, and
   * completing that chain on long runs would overflow the stack and leave the future incomplete.
   */
  static Future<Void> whileMore(Supplier<Future<Boolean>> step) {
    Promise<Void> done = Promise.promise();
    runStep(step, done);
    return done.future();
  }

  private static void runStep(Supplier<Future<Boolean>> step, Promise<Void> done) {
    step.get().onComplete(more -> {
      if (more.failed()) {
        done.fail(more.cause());
      } else if (Boolean.TRUE.equals(more.result())) {
        runStep(step, done);
      } else {
        done.complete();
      }
    });
  }

  /** Deletes the queue row and the placeholder of its refId in one statement, so neither outlives the other. */
  private static Future<Void> deleteFieldRow(Conn conn, String schema, Object id, String refId) {
    return conn.execute("WITH queue_row AS (DELETE FROM " + schema + ".custom_fields_cleanup WHERE id = $1)"
        + " DELETE FROM " + schema + ".custom_fields WHERE jsonb->>'entityType' = $2 AND jsonb->>'refId' = $3",
        Tuple.of(id, PLACEHOLDER_ENTITY_TYPE, refId))
      .mapEmpty();
  }

  private static int batchSize() {
    var value = Environment.getValue(BATCH_SIZE_KEY);
    if (value == null) {
      return DEFAULT_BATCH_SIZE;
    }
    var size = parseOrZero(value);
    if (size < 1) {
      log.warn("batchSize:: invalid {} [{}], using {}", BATCH_SIZE_KEY, value, DEFAULT_BATCH_SIZE);
      return DEFAULT_BATCH_SIZE;
    }
    return size;
  }

  private static int parseOrZero(String value) {
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
