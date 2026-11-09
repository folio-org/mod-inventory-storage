package org.folio.persist;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.List;
import org.folio.rest.jaxrs.model.HridSetting;
import org.folio.rest.jaxrs.model.HridSettings;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.Criteria.Criterion;
import org.springframework.stereotype.Repository;

@Repository
public class HridSettingsRepository extends AbstractRepository<HridSettings> {

  public static final String HRID_SETTINGS_TABLE = "hrid_settings";
  public static final String HRID_INSTANCES_SEQUENCE_NAME = "hrid_instances_seq";
  public static final String HRID_ITEMS_SEQUENCE_NAME = "hrid_items_seq";
  public static final String HRID_HOLDINGS_SEQUENCE_NAME = "hrid_holdings_seq";
  private static final String HRID_SETTINGS_VIEW = "hrid_settings_view";

  public HridSettingsRepository(Context context) {
    super(context, HRID_SETTINGS_TABLE, HridSettings.class);
  }

  /**
   * Returns the settings including the current number of each sequence.
   */
  public Future<HridSettings> getSettings(String tenantId) {
    return postgresClient(tenantId).selectSingle("SELECT jsonb FROM " + HRID_SETTINGS_VIEW)
      .map(row -> row.getJsonObject(0).mapTo(HridSettings.class));
  }

  /**
   * Stores the settings and restarts the sequences at the start numbers, all in one transaction.
   */
  public Future<Void> updateSettings(HridSettings hridSettings, String tenantId) {
    return inTransaction(tenantId, conn ->
      conn.update(HRID_SETTINGS_TABLE, hridSettings, new Criterion(), false)
        .compose(x -> updateSequence(conn, HRID_INSTANCES_SEQUENCE_NAME, hridSettings.getInstances()))
        .compose(x -> updateSequence(conn, HRID_HOLDINGS_SEQUENCE_NAME, hridSettings.getHoldings()))
        .compose(x -> updateSequence(conn, HRID_ITEMS_SEQUENCE_NAME, hridSettings.getItems()))
        .mapEmpty());
  }

  /**
   * Returns the settings together with the n next values of the sequence.
   */
  public Future<SettingsAndValues> getSettingsAndNextValues(String sequenceName, int n, String tenantId) {
    var sql = "SELECT jsonb::text%s FROM hrid_settings".formatted(", nextval($1)".repeat(Math.max(0, n)));
    return postgresClient(tenantId).selectSingle(sql, Tuple.of(sequenceName))
      .map(row -> {
        var values = new ArrayList<Long>(n);
        for (int i = 1; i <= n; i++) {
          values.add(row.getLong(i));
        }
        return new SettingsAndValues(Json.decodeValue(row.getString(0), HridSettings.class), values);
      });
  }

  private Future<RowSet<Row>> updateSequence(Conn conn, String field, HridSetting hridSetting) {
    // "ALTER SEQUENCE RESTART" is transactional, "setval" isn't:
    // https://www.postgresql.org/docs/current/sql-altersequence.html
    // https://www.postgresql.org/docs/current/functions-sequence.html
    // No SQL injection because field is a constant and the start number is a required long.
    var sql = String.format("ALTER SEQUENCE %s RESTART WITH %d", field, hridSetting.getStartNumber());
    return conn.execute(sql)
      .recover(e -> Future.failedFuture("Cannot update HRID setting " + field + ": " + e.getMessage()));
  }

  /**
   * The HRID settings and the sequence values taken together with them.
   */
  public record SettingsAndValues(HridSettings settings, List<Long> values) { }
}
