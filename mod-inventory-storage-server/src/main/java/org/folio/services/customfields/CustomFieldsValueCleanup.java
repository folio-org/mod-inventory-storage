package org.folio.services.customfields;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import java.util.HashMap;
import java.util.Map;
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
 * Removes the values of deleted custom fields queued in {@code custom_fields_cleanup}, one batch per
 * transaction, so a cleanup of any size locks items for one batch at a time. A pass starts after each
 * field delete. Each {@code _timer} call starts one for the rows those passes leave behind: after a
 * restart, after a failed row, or when another instance held the tenant's lock.
 */
public final class CustomFieldsValueCleanup {

  public static final String BATCH_SIZE_KEY = "CUSTOM_FIELDS_CLEANUP_BATCH_SIZE";
  /** The entityType of the refId placeholders, which no client uses. */
  public static final String PLACEHOLDER_ENTITY_TYPE = "pendingValueCleanup";
  private static final int DEFAULT_BATCH_SIZE = 1000;
  private static final String LOCK_KEY = "hashtext('custom_fields_cleanup'), hashtext($1)";
  private static final String HOLDS_VALUE = "jsonb->'customFields' ? $1";

  private static final Logger log = LogManager.getLogger(CustomFieldsValueCleanup.class);

  private enum State { RUNNING, RERUN }

  private static final Map<String, State> TENANTS = new HashMap<>();

  private CustomFieldsValueCleanup() {
  }

  /** Starts processing the tenant's queue, or marks a running pass to run again. */
  public static void kick(Vertx vertx, String tenantId) {
    synchronized (TENANTS) {
      if (TENANTS.putIfAbsent(tenantId, State.RUNNING) != null) {
        TENANTS.put(tenantId, State.RERUN);
        return;
      }
    }
    runPass(vertx, tenantId);
  }

  /** Starts the pass inside the future chain, so a pass that throws before it returns a future still ends. */
  private static void runPass(Vertx vertx, String tenantId) {
    Future.<Void>succeededFuture()
      .compose(x -> processQueue(vertx, tenantId))
      .onComplete(result -> {
        if (result.failed()) {
          log.error("runPass:: custom field value cleanup failed [tenant={}]", tenantId, result.cause());
        }
        boolean rerun;
        synchronized (TENANTS) {
          rerun = TENANTS.remove(tenantId) == State.RERUN;
        }
        if (rerun) {
          kick(vertx, tenantId);
        }
      });
  }

  private static Future<Void> processQueue(Vertx vertx, String tenantId) {
    var pg = PostgresClient.getInstance(vertx, tenantId);
    var schema = PostgresClient.convertToPsqlStandard(tenantId);
    return pg.withConn(conn -> processQueueLocked(conn, schema));
  }

  /**
   * Runs the pass under a session advisory lock on the tenant, held on this connection across the
   * pass's transactions, so one pass per tenant runs across all module instances. A pass that finds
   * the lock held leaves the queue to the holder; rows queued meanwhile wait for the next pass.
   */
  private static Future<Void> processQueueLocked(Conn conn, String schema) {
    var lockKey = Tuple.of(schema);
    return conn.execute("SELECT pg_try_advisory_lock(" + LOCK_KEY + ")", lockKey)
      .compose(locked -> {
        if (!locked.iterator().next().getBoolean(0)) {
          log.info("processQueueLocked:: custom field value cleanup runs on another instance [schema={}]", schema);
          return Future.succeededFuture();
        }
        return processRows(conn, schema)
          .eventually(() -> unlock(conn, lockKey, schema));
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

  private static Future<Void> processRows(Conn conn, String schema) {
    return conn.execute("SELECT id, entity_type, ref_id FROM " + schema
        + ".custom_fields_cleanup ORDER BY created_date")
      .compose(queue -> {
        Future<Void> result = Future.succeededFuture();
        for (Row row : queue) {
          result = result.compose(x -> processRow(conn, schema, row)
            .recover(e -> recordFailure(conn, schema, row.getUUID("id"), e)));
        }
        return result;
      });
  }

  private static Future<Void> processRow(Conn conn, String schema, Row row) {
    var id = row.getUUID("id");
    var refId = row.getString("ref_id");
    var table = RecordServiceFactoryImpl.ENTITY_TYPE_TABLES.get(row.getString("entity_type"));
    if (table == null) {
      log.warn("processRow:: discarding cleanup row with unsupported entityType [{}]", row.getString("entity_type"));
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

  private static Future<Void> removeFieldValues(Conn conn, String schema, String table, String refId) {
    log.info("removeFieldValues:: removing values of custom field [refId={}]", refId);
    var fullTable = schema + "." + table;
    var batch = removeValueBatch(fullTable);
    var batchParams = Tuple.of(refId, batchSize());
    var records = new AtomicLong();
    var batches = new AtomicInteger();
    var start = System.nanoTime();
    return whileMore(() -> conn.execute(batch, batchParams)
      .compose(result -> {
        if (result.rowCount() == 0) {
          return anyLeft(conn, fullTable, refId);
        }
        batches.incrementAndGet();
        records.addAndGet(result.rowCount());
        return Future.succeededFuture(true);
      }))
      .onSuccess(v -> log.info("removeFieldValues:: removed values of custom field "
          + "[refId={}, records={}, batches={}, durationMs={}]",
        refId, records.get(), batches.get(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)));
  }

  /**
   * The condition is repeated outside the subquery: a batch that waits for a concurrent write of its records
   * re-checks only the outer condition when that write commits, and would otherwise rewrite records that no
   * longer hold the value.
   */
  private static String removeValueBatch(String table) {
    return "UPDATE " + table + " SET jsonb = jsonb_set(jsonb, '{customFields}', (jsonb->'customFields') - $1)"
      + " WHERE " + HOLDS_VALUE
      + " AND id IN (SELECT id FROM " + table + " WHERE " + HOLDS_VALUE + " LIMIT $2)";
  }

  /**
   * Runs the step until it yields false. The last step completes the result itself: completing it
   * through one nested future per step overflows the stack on long runs, and the result never completes.
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

  /**
   * A batch whose records were all changed by concurrent writes updates none of them while others still
   * hold the value, so an empty batch is confirmed with this query.
   */
  private static Future<Boolean> anyLeft(Conn conn, String table, String refId) {
    return conn.execute("SELECT EXISTS (SELECT 1 FROM " + table + " WHERE " + HOLDS_VALUE + ")", Tuple.of(refId))
      .map(rows -> rows.iterator().next().getBoolean(0));
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
