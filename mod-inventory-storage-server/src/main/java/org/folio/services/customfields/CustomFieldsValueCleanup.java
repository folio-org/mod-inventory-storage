package org.folio.services.customfields;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.record.RecordServiceFactoryImpl;
import org.folio.utils.Environment;

/**
 * Removes the values of the deleted custom fields queued in {@code custom_fields_cleanup}, one batch
 * per transaction, so a cleanup of any size never locks items longer than one batch takes. A row is
 * deleted together with the placeholder that holds its refId (create_custom_fields_cleanup.sql), so
 * a new field can take the refId only once its values are gone. A failed pass is retried after a
 * delay. The updates are idempotent, so two module instances processing the same row do no harm.
 */
public final class CustomFieldsValueCleanup {

  public static final String BATCH_SIZE_KEY = "CUSTOM_FIELDS_CLEANUP_BATCH_SIZE";
  /** The entityType of the refId placeholders, which no client uses. */
  public static final String PLACEHOLDER_ENTITY_TYPE = "pendingValueCleanup";
  private static final int DEFAULT_BATCH_SIZE = 1000;
  private static final long RETRY_DELAY_MS = 1000;

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

  private static void runPass(Vertx vertx, String tenantId) {
    processQueue(vertx, tenantId)
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
        } else if (result.failed()) {
          vertx.setTimer(RETRY_DELAY_MS, timer -> kick(vertx, tenantId));
        }
      });
  }

  private static Future<Void> processQueue(Vertx vertx, String tenantId) {
    var pg = PostgresClient.getInstance(vertx, tenantId);
    var schema = PostgresClient.convertToPsqlStandard(tenantId);
    return pg.select("SELECT id, entity_type, ref_id FROM " + schema
        + ".custom_fields_cleanup ORDER BY created_date")
      .compose(queue -> {
        Future<Void> result = Future.succeededFuture();
        for (Row row : queue) {
          result = result.compose(x -> processRow(pg, schema, row));
        }
        return result;
      });
  }

  private static Future<Void> processRow(PostgresClient pg, String schema, Row row) {
    var id = row.getUUID("id");
    var refId = row.getString("ref_id");
    var table = RecordServiceFactoryImpl.ENTITY_TYPE_TABLES.get(row.getString("entity_type"));
    if (table == null) {
      log.warn("processRow:: discarding cleanup row with unsupported entityType [{}]", row.getString("entity_type"));
      return deleteRow(pg, schema, id);
    }
    return removeFieldValues(pg, schema, table, refId)
      .compose(x -> deleteFieldRow(pg, schema, id, refId));
  }

  private static Future<Void> removeFieldValues(PostgresClient pg, String schema, String table, String refId) {
    log.info("removeFieldValues:: removing values of custom field [refId={}]", refId);
    var sql = "UPDATE " + schema + "." + table
      + " SET jsonb = jsonb_set(jsonb, '{customFields}', (jsonb->'customFields') - $1)"
      + " WHERE id IN (SELECT id FROM " + schema + "." + table
      + " WHERE jsonb->'customFields' ? $1 LIMIT $2)";
    return repeatUntilNoneLeft(pg, sql, Tuple.of(refId, batchSize()));
  }

  private static Future<Void> repeatUntilNoneLeft(PostgresClient pg, String sql, Tuple params) {
    return pg.execute(sql, params)
      .compose(result -> result.rowCount() < 1
                         ? Future.succeededFuture()
                         : repeatUntilNoneLeft(pg, sql, params));
  }

  private static Future<Void> deleteRow(PostgresClient pg, String schema, Object id) {
    return pg.execute("DELETE FROM " + schema + ".custom_fields_cleanup WHERE id = $1", Tuple.of(id))
      .mapEmpty();
  }

  /** Deletes the queue row and the placeholder of its refId in one statement, so neither outlives the other. */
  private static Future<Void> deleteFieldRow(PostgresClient pg, String schema, Object id, String refId) {
    return pg.execute("WITH queue_row AS (DELETE FROM " + schema + ".custom_fields_cleanup WHERE id = $1)"
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
