package org.folio.rest.support;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
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
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;

public class HridManager {
  public static final String HRID_SETTINGS_TABLE = "hrid_settings";
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
    final Promise<HridSettings> promise = Promise.promise();

    try {
      context.runOnContext(v -> postgresClient.startTx(res -> {
        if (res.succeeded()) {
          try {
            fetchHridSettings(res)
              .onComplete(hridSettingsResult ->
                postgresClient.endTx(res, done -> {
                  if (hridSettingsResult.succeeded()) {
                    promise.complete(hridSettingsResult.result());
                  } else {
                    fail(promise, "Failed to retrieve the HRID settings",
                      hridSettingsResult.cause());
                  }
                })
              );
          } catch (Exception e) {
            postgresClient.rollbackTx(res, done -> fail(promise, "Failed retrieving the HRID settings", e));
          }
        } else {
          fail(promise, "Failed to obtain a database connection", res.cause());
        }
      }));
    } catch (Exception e) {
      fail(promise, "Failed to execute getting the HRID settings on a context", e);
    }

    return promise.future();
  }

  public Future<Void> updateHridSettings(HridSettings hridSettings) {
    final Promise<Void> promise = Promise.promise();

    try {
      context.runOnContext(v -> {
        try {
          postgresClient.startTx(conn -> {
            try {
              fetchHridSettings(conn)
                .compose(existingHridSettings -> doUpdateHridSettings(
                  conn, existingHridSettings, hridSettings))
                .map(success -> endTransaction(conn, promise))
                .otherwise(error -> rollback(conn, promise, error));
            } catch (Exception e) {
              fail(promise, "Failed to update HRID settings", e);
            }
          });
        } catch (Exception e) {
          fail(promise, "Failed creating a database transaction", e);
        }
      });
    } catch (Exception e) {
      fail(promise, "Failed to execute updating the HRID settings in a context", e);
    }

    return promise.future();
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

  private Future<HridSettings> fetchHridSettings(AsyncResult<SQLConnection> conn) {
    final String sql = "SELECT jsonb FROM " + HRID_SETTINGS_TABLE;
    final Promise<Row> promise = Promise.promise();

    try {
      postgresClient.selectSingle(conn, sql, promise);
    } catch (Exception e) {
      fail(promise, "Failed to get HRID settings from the database", e);
    }

    return promise.future().map(
      results -> {
        try {
          JsonObject o = (JsonObject) results.getValue(0);
          return Json.decodeValue(o.encode(), HridSettings.class);
        } catch (Exception e) {
          log.fatal(e.getMessage(), e);
        }
        return null;
      });
  }

  private Future<Void> doUpdateHridSettings(AsyncResult<SQLConnection> conn,
                                            HridSettings existingHridSettings, HridSettings hridSettings) {
    hridSettings.setId(existingHridSettings.getId());

    final Promise<RowSet<Row>> promise = Promise.promise();

    try {
      postgresClient.update(conn, HRID_SETTINGS_TABLE, hridSettings, null, false, promise);

      // Even though we are executing this within a transaction, all updates must be done
      // sequentially or else the SQL client will report race condition errors due to a
      // currently running query. The order is arbitrary, but first update the settings, then
      // update the instance sequence, then update the holdings sequence and finally update
      // the item sequence.
      return promise.future().compose(v -> updateSequence(conn, "instances",
        existingHridSettings.getInstances(), hridSettings.getInstances())
        .compose(v1 -> updateSequence(conn, "holdings", existingHridSettings.getHoldings(),
          hridSettings.getHoldings())
          .compose(v2 -> updateSequence(conn, "items", existingHridSettings.getItems(),
            hridSettings.getItems()))));
    } catch (Exception e) {
      fail(promise, "Failed to update the HRID settings and sequences", e);
    }

    return promise.future().map(v -> null);
  }

  private Future<Void> updateSequence(AsyncResult<SQLConnection> conn, String field,
                                      HridSetting existingHridSetting, HridSetting hridSetting) {
    final Promise<RowSet<Row>> promise = Promise.promise();

    // Only update the sequence if the start number has changed
    if (!Objects.equals(existingHridSetting.getStartNumber(), hridSetting.getStartNumber())) {
      final String sql = String.format("select setval('hrid_%s_seq',%d,FALSE)",
        field, hridSetting.getStartNumber());
      try {
        postgresClient.select(conn, sql, promise);
      } catch (Exception e) {
        fail(promise, "Failed updating the sequence number: " + sql, e);
      }
    } else {
      promise.complete(null);
    }

    return promise.future().map(v -> null);
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
