package org.folio.rest.support;

import static io.vertx.core.CompositeFuture.all;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.future.FailedFuture;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import java.util.List;

import io.vertx.sqlclient.impl.ArrayTuple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.HridSetting;
import org.folio.rest.jaxrs.model.HridSettings;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;

import java.util.Objects;
import java.util.function.Function;

public class HridManager {
  private static final Logger log = LogManager.getLogger();

  public static final String HRID_SETTINGS_TABLE  = "hrid_settings";

  private static final String HRID_INSTANCES_SEQUENCE_NAME = "hrid_instances_seq";
  private static final String HRID_ITEMS_SEQUENCE_NAME = "hrid_items_seq";
  private static final String HRID_HOLDINGS_SEQUENCE_NAME = "hrid_holdings_seq";


  private final Context context;
  private final PostgresClient postgresClient;

  public HridManager(Context context, PostgresClient postgresClient) {
    this.context = Objects.requireNonNull(context, "Context cannot be null");
    this.postgresClient = Objects.requireNonNull(postgresClient, "PostgresClient cannot be null");
  }

  public Future<String> getNextInstanceHrid() {
    return getNextHrid(InventoryType.Instances);
  }

  public Future<String> getNextHoldingsHrid() {
    return getNextHrid(InventoryType.Holdings);
  }

  public Future<String> getNextItemHrid() {
    return getNextHrid(InventoryType.Items);
  }

  public Future<Item> populateHrid(Item item) {
    return isBlank(item.getHrid())
      ? getNextItemHrid().map(item::withHrid)
      : Future.succeededFuture(item);
  }

  public Future<Instance> populateHrid(Instance instance) {
    return isBlank(instance.getHrid())
      ? getNextInstanceHrid().map(instance::withHrid)
      : Future.succeededFuture(instance);
  }

  public Future<HoldingsRecord> populateHrid(HoldingsRecord hr) {
    return isBlank(hr.getHrid())
      ? getNextHoldingsHrid().map(hr::withHrid)
      : Future.succeededFuture(hr);
  }

  public Future<List<Instance>> populateHridForInstances(List<Instance> instances) {
    return all(instances.stream()
      .map(this::populateHrid)
      .collect(toList()))
      .map(notUsed -> instances);
  }

  public Future<List<Item>> populateHridForItems(List<Item> items) {
    return all(items.stream()
      .map(this::populateHrid)
      .collect(toList()))
      .map(notUsed -> items);
  }

  public Future<List<HoldingsRecord>> populateHridForHoldings(List<HoldingsRecord> hrs) {
    return all(hrs.stream()
      .map(this::populateHrid)
      .collect(toList()))
      .map(notUsed -> hrs);
  }

  public Future<HridSettings> getHridSettings() {
    final Promise<HridSettings> promise = Promise.promise();

    try {
      context.runOnContext(v -> postgresClient.startTx(res -> {
        if (res.succeeded()) {
          try {
            getHridSettings(res)
              .onComplete(hridSettingsResult ->
                postgresClient.endTx(res, done -> {
                  if (hridSettingsResult.succeeded()) {
                    promise.complete(hridSettingsResult.result());
                  } else {
                    fail(promise, "Failed to retrieve the HRID settings",
                      hridSettingsResult.cause());
                  }})
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
              getHridSettings(conn)
                .compose(existingHridSettings -> updateHridSettings(
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

  private Future<HridSettings> getHridSettings(AsyncResult<SQLConnection> conn) {
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

  private Future<Void> updateHridSettings(AsyncResult<SQLConnection> conn,
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

  private Future<String> getNextHrid(Function<HridSettings, Future<String>> mapper) {
    final Promise<String> promise = Promise.promise();

    try {
      context.runOnContext(v -> getHridSettings().compose(mapper::apply).onComplete(promise));
    } catch (Exception e) {
      fail(promise, "Failed to get the next HRID", e);
    }

    return promise.future();
  }

  private Future<String> getNextHrid(final HridSettings hridSettings, InventoryType type) {
    final String sql = "SELECT nextval('hrid_" + type.name().toLowerCase() + "_seq')";
    final Promise<Row> promise = Promise.promise();

    try {
      postgresClient.selectSingle(sql, promise);
    } catch (Exception e) {
      fail(promise, "Failed to get the next sequence value from the database", e);
    }

    final String hridPrefix = type.getPrefix(hridSettings);
    return promise.future()
      .map(sequence -> String.format(getHridFormatter(hridSettings),
        Objects.toString(hridPrefix, ""), sequence.getLong(0)));
  }

  /**
   * Optimized version of getNextHrid that makes a single call to retrieve
   * hridsettings and the next sequence value for the specified inventory type.
   * @param type
   * @return
   */
  private Future<String> getNextHrid(InventoryType type) {
    final String sql = "select jsonb::TEXT from hrid_settings " +
      "union all " +
      "SELECT nextval($1)::text";
    final Promise<String> promise = Promise.promise();

    try {
      context.runOnContext(v ->
      {
        String sequenceName;
        switch (type) {
          case Instances:
            sequenceName = HRID_INSTANCES_SEQUENCE_NAME;
            break;
          case Holdings:
            sequenceName = HRID_HOLDINGS_SEQUENCE_NAME;
            break;
          case Items:
            sequenceName = HRID_ITEMS_SEQUENCE_NAME;
          default:
            sequenceName = "hrid_" + type.name().toLowerCase() + "_seq";
        }

        postgresClient.select(sql,
          new ArrayTuple(1).addString(sequenceName),
          reply -> {
            var result = reply.result();
            if (reply.failed() || result.size() != 2) {
              fail(promise, "Failed to get hridsettings and next sequence value from the database", reply.cause());
              return;
            }
            RowIterator<Row> iterator = result.iterator();
            // get hridSettings
            var hridSettingsrow = iterator.next();
            HridSettings hridSettings = Json.decodeValue(hridSettingsrow.getString(0), HridSettings.class);
            final String hridPrefix = type.getPrefix(hridSettings);
            // get hrid
            var hridIdrow = iterator.next();
            String s = String.format(
              getHridFormatter(hridSettings),
              Objects.toString(hridPrefix, ""),
              Long.parseLong(hridIdrow.getString(0)));
            promise.complete(s);
          });
      });
    } catch (Exception e) {
      fail(promise, "Failed to get hridsettings and next sequence value from the database", e);
    }

    return promise.future();
  }

  private String getHridFormatter(HridSettings hridSettings) {
    return hridSettings.getCommonRetainLeadingZeroes() ? "%s%011d" : "%s%d";
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

  private interface Inventory<H> {
    String getPrefix(HridSettings hridSettings);
  }

  private enum InventoryType implements Inventory<HridSettings> {
    Holdings {
      @Override
      public String getPrefix(HridSettings hridSettings) {
        return hridSettings.getHoldings().getPrefix();
      }
    },

    Instances {
      public String getPrefix(HridSettings hridSettings) {
        return hridSettings.getInstances().getPrefix();
      }
    },

    Items {
      public String getPrefix(HridSettings hridSettings) {
        return hridSettings.getItems().getPrefix();
      }
    }
  }
}
