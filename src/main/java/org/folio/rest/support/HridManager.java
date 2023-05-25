package org.folio.rest.support;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.impl.InstanceStorageBatchApi;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.HridSetting;
import org.folio.rest.jaxrs.model.HridSettings;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;

public class HridManager {
  private static final String HRID_SETTINGS_TABLE = "hrid_settings";
  private static final String HRID_SETTINGS_VIEW = "hrid_settings_view";
  private static final Logger log = LogManager.getLogger();
  private static final String HRID_INSTANCES_SEQUENCE_NAME = "hrid_instances_seq";
  private static final String HRID_ITEMS_SEQUENCE_NAME = "hrid_items_seq";
  private static final String HRID_HOLDINGS_SEQUENCE_NAME = "hrid_holdings_seq";

  private final Context context;
  private final PostgresClient postgresClient;

  public HridManager(Context context, PostgresClient postgresClient) {
    this.context = Objects.requireNonNull(context, "Context cannot be null");
    this.postgresClient = Objects.requireNonNull(postgresClient, "PostgresClient cannot be null");
  }

  /**
   * Deprecated.
   *
   * @deprecated Remove after deprecated {@link InstanceStorageBatchApi} has been removed
   */
  @Deprecated
  public Future<String> getNextInstanceHrid() {
    return populateHrid(new Instance()).map(Instance::getHrid);
  }

  public Future<Item> populateHrid(Item item) {
    return populateHridForItems(List.of(item)).map(item);
  }

  public Future<Instance> populateHrid(Instance instance) {
    return populateHridForInstances(List.of(instance)).map(instance);
  }

  public Future<HoldingsRecord> populateHrid(HoldingsRecord hr) {
    return populateHridForHoldings(List.of(hr)).map(hr);
  }

  public Future<List<Instance>> populateHridForInstances(List<Instance> instances) {
    return populateHrids(InventoryType.INSTANCES, instances, Instance::getHrid, Instance::setHrid);
  }

  public Future<List<Item>> populateHridForItems(List<Item> items) {
    return populateHrids(InventoryType.ITEMS, items, Item::getHrid, Item::setHrid);
  }

  public Future<List<HoldingsRecord>> populateHridForHoldings(List<HoldingsRecord> hrs) {
    return populateHrids(InventoryType.HOLDINGS, hrs, HoldingsRecord::getHrid, HoldingsRecord::setHrid);
  }

  public Future<HridSettings> getHridSettings() {
    return postgresClient.selectSingle("SELECT jsonb FROM " + HRID_SETTINGS_VIEW)
        .map(row -> row.getJsonObject(0).mapTo(HridSettings.class))
        .onFailure(e -> log.error("Failed to retrieve the HRID settings", e));
  }

  public Future<Void> updateHridSettings(HridSettings hridSettings) {
    hridSettings.getInstances().setCurrentNumber(null);
    hridSettings.getHoldings().setCurrentNumber(null);
    hridSettings.getItems().setCurrentNumber(null);
    return postgresClient.withTrans(conn ->
        conn.update(HRID_SETTINGS_TABLE, hridSettings, new Criterion(), false)
        .compose(x -> updateSequence(conn, HRID_INSTANCES_SEQUENCE_NAME, hridSettings.getInstances()))
        .compose(x -> updateSequence(conn, HRID_HOLDINGS_SEQUENCE_NAME, hridSettings.getHoldings()))
        .compose(x -> updateSequence(conn, HRID_ITEMS_SEQUENCE_NAME, hridSettings.getItems()))
        .mapEmpty());
  }

  private <T> Future<List<T>> populateHrids(InventoryType inventoryType, List<T> list,
                                            Function<T, String> getHrid, BiConsumer<T, String> setHrid) {

    int n = 0;
    for (T t : list) {
      if (isBlank(getHrid.apply(t))) {
        n++;
      }
    }
    return getNextHrids(inventoryType, n)
      .map(List::iterator)
      .map(hrids -> {
        for (T t : list) {
          if (isBlank(getHrid.apply(t))) {
            setHrid.accept(t, hrids.next());
          }
        }
        return list;
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
   * Return the next n HRIDs for the given type.
   */
  private Future<List<String>> getNextHrids(InventoryType type, int n) {
    if (n == 0) {
      return Future.succeededFuture(Collections.emptyList());
    }
    StringBuilder sql = new StringBuilder("SELECT jsonb::text");
    for (int i = 0; i < n; i++) {
      sql.append(", nextval($1)");
    }
    sql.append(" FROM hrid_settings");
    return postgresClient.selectSingle(sql.toString(), Tuple.of(type.getSequenceName()))
      .map(row -> {
        HridSettings hridSettings = Json.decodeValue(row.getString(0), HridSettings.class);
        final String hridPrefix = type.getPrefix(hridSettings);
        final String formatter = getHridFormatter(hridSettings);
        List<String> list = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
          String hrid = String.format(
            formatter,
            Objects.toString(hridPrefix, ""),
            row.getLong(i));
          list.add(hrid);
        }
        return list;
      });
  }

  private String getHridFormatter(HridSettings hridSettings) {
    return Boolean.TRUE.equals(hridSettings.getCommonRetainLeadingZeroes()) ? "%s%011d" : "%s%d";
  }

  private Void endTransaction(AsyncResult<SQLConnection> conn, Promise<Void> promise) {
    try {
      postgresClient.endTx(conn, promise);
    } catch (Exception e) {
      fail(promise, "Failed ending the database transaction", e);
    }

    return null;
  }

  private Void rollback(AsyncResult<SQLConnection> conn, Promise<Void> promise,
                        Throwable error) {
    log.error("Error updating HRID settings, rolling back transaction", error);

    try {
      postgresClient.rollbackTx(conn, rollback -> {
        if (rollback.failed()) {
          log.error("Rollback failed", rollback.cause());
        } else {
          log.error("Rollback completed");
        }
        promise.fail(error);
      });
    } catch (Exception e) {
      fail(promise, "Failed rolling back the transaction", e);
    }

    return null;
  }

  private <T> void fail(Promise<T> promise, String message, Throwable t) {
    log.error(message, t);
    promise.fail(t);
  }

  private enum InventoryType implements Inventory<HridSettings> {
    HOLDINGS {
      @Override
      public String getPrefix(HridSettings hridSettings) {
        return hridSettings.getHoldings().getPrefix();
      }

      @Override
      public String getSequenceName() {
        return HRID_HOLDINGS_SEQUENCE_NAME;
      }
    },

    INSTANCES {
      @Override
      public String getPrefix(HridSettings hridSettings) {
        return hridSettings.getInstances().getPrefix();
      }

      @Override
      public String getSequenceName() {
        return HRID_INSTANCES_SEQUENCE_NAME;
      }
    },

    ITEMS {
      @Override
      public String getPrefix(HridSettings hridSettings) {
        return hridSettings.getItems().getPrefix();
      }

      @Override
      public String getSequenceName() {
        return HRID_ITEMS_SEQUENCE_NAME;
      }
    }
  }

  private interface Inventory<H> {
    String getPrefix(HridSettings hridSettings);

    String getSequenceName();
  }
}
