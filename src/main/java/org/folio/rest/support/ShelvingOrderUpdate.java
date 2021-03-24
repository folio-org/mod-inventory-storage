package org.folio.rest.support;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnection;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Transaction;
import io.vertx.sqlclient.Tuple;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.folio.dbschema.Versioned;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

public class ShelvingOrderUpdate {
  private static final Logger log = LogManager.getLogger();

  private static final String SQL_SELECT_ITEMS =
      new StringBuilder()
          .append("SELECT id AS \"id\", ")
          .append("jsonb AS \"itemJson\" ")
          .append("FROM item WHERE not (jsonb ? 'effectiveShelvingOrder') ")
          .append(" FOR UPDATE ")
          .append("LIMIT $1").toString();


  private static final String SQL_UPDATE_ITEMS = new StringBuilder()
      .append("UPDATE ITEM ")
      .append("SET jsonb = $1 ")
      .append("WHERE id = $2").toString();

  // Defines version threshold where this property was introduces from
  private static final String VERSION_WITH_SHELVING_ORDER = "20.1.0";

  public static int DEF_FETCH_SIZE = 5000;

  private static ShelvingOrderUpdate instance;
  private Versioned versionChecker;
  private int rowsBunchSize;
  private AtomicInteger totalUpdatedRows;
//  private Handler<AsyncResult<Boolean>> completionHandler;

  public static ShelvingOrderUpdate getInstance() {
    return getInstance(DEF_FETCH_SIZE);
  }

  public static ShelvingOrderUpdate getInstance(int rowsBunchSize) {
    if (Objects.isNull(instance)) {
      synchronized (ShelvingOrderUpdate.class) {
        if (Objects.isNull(instance)) {
          instance = new ShelvingOrderUpdate(rowsBunchSize);
          log.info("An shelving oder updater instantiated...");
        }
      }
    }
    return instance;
  }

  private ShelvingOrderUpdate(int rowsBunchSize) {
    this.versionChecker = new Versioned() {};
    this.versionChecker.setFromModuleVersion(VERSION_WITH_SHELVING_ORDER);
    this.rowsBunchSize = rowsBunchSize;
    this.totalUpdatedRows = new AtomicInteger(0);
  }

//  public void setCompletionHandler(Handler<AsyncResult<Boolean>> handler) {
//    log.info("getCompletionHandler set up: {}", handler);
//    completionHandler = handler;
//  }

//  public Handler<AsyncResult<Boolean>> getCompletionHandler() {
//    log.info("getCompletionHandler acquired: {}", completionHandler);
//    return completionHandler;
//  }

  /**
   * Entry routine of items updates. Invokes the process recursively until no items to update
   *
   * @param attributes
   * @param headers
   * @param vertxContext
   * @return
   */
  public Future<Integer> startUpdatingOfItems(TenantAttributes attributes, Map<String, String> headers,
      Context vertxContext) {

    // Set completion handler promise for notifying if update performed or not
//    final Promise<Boolean> completionHandlerPromise = Promise.promise();
//    if (!Objects.isNull(getCompletionHandler())) {
//      log.info("Setting completionHandler {} to promise: {}", getCompletionHandler(), completionHandlerPromise);
//      getCompletionHandler().handle(completionHandlerPromise.future());
//    } else {
//      log.info("Setting completionHandler skipped, it's a null: {}", getCompletionHandler());
//    }

    final Promise<Integer> updateItemsPromise = Promise.promise();
    final Vertx vertx = vertxContext.owner();

    log.info("Update items started, moduleTo is: \"{}\",  moduleFrom is: \"{}\"",
        attributes.getModuleTo(), attributes.getModuleFrom());

    if (ShelvingOrderUpdate.getInstance().isAllowedToUpdate(attributes)) {
      log.info("Module is eligible for upgrade.");
      // Updates items with shelving order property.
      // This should be triggered once for particular version only.
      PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, headers);

      final long startTime = System.currentTimeMillis();
      fetchAndUpdatesItems(vertx, postgresClient)
          .onSuccess(rowsAffected -> {
           totalUpdatedRows.addAndGet(rowsAffected);
            // If previous updates returns affected rows, try it again
            if (rowsAffected > 0) {
              fetchAndUpdatesItems(vertx, postgresClient);
            } else {
              // No rows affected, just set accumulated amount of rows to result
              log.info("Items update with shelving order property completed in {} seconds",
                  (System.currentTimeMillis() - startTime) / 1000);
              updateItemsPromise.complete(totalUpdatedRows.get());
//              completionHandlerPromise.complete((totalUpdatedRows.get() > 0));
            }
          })
          .onFailure(h -> {
            updateItemsPromise.fail(h.getCause());
//            completionHandlerPromise.fail(h.getCause());
            log.error("Error updating items: {}", h.getCause().getMessage());
          });

    } else {
      log.info("Module isn't eligible for upgrade, just exit.");
      // Not allowed to perform items updates, just returns
      updateItemsPromise.complete(0);
//      completionHandlerPromise.complete(Boolean.FALSE);
    }

    return updateItemsPromise.future();
  }

  /**
   * Performs fetching items by fixed bunches of rows with further property calculations and
   * updating in database
   *
   * @param vertx
   * @param postgresClient
   * @return
   */
  public Future<Integer> fetchAndUpdatesItems(Vertx vertx, PostgresClient postgresClient) {
    log.info("The routine of \"fetchAndUpdatesItems\" has started");
    Promise<Integer> fetchAndUpdatePromise = Promise.promise();

    postgresClient.getConnection(ar -> {
      
      if (ar.failed()) {
        log.info("Connection acquiring failure : {}", ar.cause().getMessage());
        fetchAndUpdatePromise.fail("Connection acquiring failure : " + ar.cause().getMessage());

      } else {
        PgConnection connection = ar.result();
        // Starts a new transaction (acquiring and updating of items performs in single transaction)
        connection.begin()
            // New transaction started
            .compose(tx -> connection

            // Acquiring current bunch of items
            .preparedQuery(SQL_SELECT_ITEMS).execute(Tuple.of(rowsBunchSize))

            .compose(fetchedRows ->
                // Read items and fill them into data structures for further processing
                aggregateRow2List(fetchedRows)

                  // Calculate items shelving order
                  .compose(ShelvingOrderUpdate.this::calculateShelvingOrder)

                  // Prepare items update parameters
                  .compose(ShelvingOrderUpdate.this::prepareItemsUpdate)

                  // Updates of items
                  .compose(listOfTuple -> updateItems(listOfTuple, connection))

                  // Finish transaction
                  .compose(updatedRowsCount -> completeTransaction(tx, updatedRowsCount, fetchAndUpdatePromise))

                  // Close connection
                  .eventually(v -> connection.close())));
      }
    });

    return fetchAndUpdatePromise.future();
  }

  /**
   * Completes transaction and set common update promise with affected rows count
   *
   * @param tx
   * @param updatedRowsCount
   * @param itemsUpdatePromise
   * @return
   */
  private Future<Void> completeTransaction(Transaction tx,Integer updatedRowsCount,
      Promise<Integer> itemsUpdatePromise) {
    log.info("Invoking of completeTransaction, updatedRows size: {}", updatedRowsCount);

    tx.commit()
        .onSuccess(h -> itemsUpdatePromise.complete(updatedRowsCount))
        .onFailure(h -> itemsUpdatePromise.fail(h.getCause().getMessage()));

    return Future.succeededFuture();
  }

  /**
   * Read items and fill them into data structures for further processing
   * 
   * @param rowSet
   * @return
   */
  public Future<List<Item>> aggregateRow2List(RowSet<Row> rowSet) {
    log.info("Invoking of aggregateRow2List, rows size: {}", rowSet.size());
    Promise<List<Item>> promise = Promise.promise();

    if (rowSet.size() > 0) {
      List<Item> targetList = new ArrayList<>();
      rowSet.forEach(row -> {
        JsonObject itemJsonObject = row.getJsonObject("itemJson");
        Item item = itemJsonObject.mapTo(Item.class);
        targetList.add(item);
      });
      promise.complete(targetList);
    } else {
      promise.complete(Collections.emptyList());
    }

    return promise.future();
  }

  /**
   * Calculate items shelving order
   * 
   * @return
   */
  public Future<Map<UUID, JsonObject>> calculateShelvingOrder(List<Item> itemList) {
    log.info("Invoking of calculateShelvingOrder, itemList size: {}",
        Optional.ofNullable(itemList).orElse(new ArrayList<>()).size());

    Map<UUID, JsonObject> updatedItemsMap = itemList.stream()
        .map(EffectiveCallNumberComponentsUtil::getCalculateAndSetEffectiveShelvingOrder)
        .collect(Collectors.toMap(item -> UUID.fromString(item.getId()), JsonObject::mapFrom));

    return Future.succeededFuture(updatedItemsMap);
  }

  /**
   * Prepare items update parameters
   * 
   * @param itemIdMap
   * @return
   */
  public Future<List<Tuple>> prepareItemsUpdate(Map<UUID, JsonObject> itemIdMap) {
    log.info("Invoking of prepareItemsUpdate");

    List<Tuple> itemsUpdateParams = new ArrayList<>();
    itemIdMap.entrySet().stream()
        .map(entry -> itemsUpdateParams.add(Tuple.of(entry.getValue(), entry.getKey())))
        .collect(Collectors.toList());

    return Future.succeededFuture(itemsUpdateParams);
  }

  /**
   * Updates of items
   * 
   * @param itemsParams
   * @param connection
   * @return
   */
  public Future<Integer> updateItems(List<Tuple> itemsParams, PgConnection connection) {
    log.info("Invoking of updateItems, itemsParams size: {}", itemsParams.size());
    log.info("Items parameters for update SQL: {}",
        StringUtils.defaultIfBlank(itemsParams.stream().map(Tuple::toString).collect(Collectors.joining(",")),
            "none"));

    if (itemsParams.isEmpty()) {
      log.info("Items parameters for update SQL are empty");
      return Future.succeededFuture(0);
    }

    Promise<Integer> promise = Promise.promise();
    connection.preparedQuery(SQL_UPDATE_ITEMS).executeBatch(itemsParams, res -> {
      if (res.succeeded()) {
        int updatedItemsCount = res.result().rowCount();
        log.info("There were {} items updated", updatedItemsCount);
        promise.complete(updatedItemsCount);
      } else {
        log.error("Items updates failed: {} ", res.cause().getMessage());
        promise.fail(res.cause().getMessage());
      }
    });

    return promise.future();
  }

  /**
   * Determine conditions of starting items updates
   * 
   * @param attributes
   * @return
   */
  public boolean isAllowedToUpdate(TenantAttributes attributes) {
    log.info("Checking if upgrade allowed, moduleTo is: \"{}\",  moduleFrom is: \"{}\"",
        attributes.getModuleTo(), attributes.getModuleFrom());

    String moduleFrom =
        Optional.ofNullable(attributes.getModuleFrom()).orElse(StringUtils.EMPTY).trim();

    boolean result =
        StringUtils.isNotBlank(moduleFrom) && versionChecker.isNewForThisInstall(moduleFrom);
    log.info("Module upgrade {}", result ? "is allowed" : "isn't allowed");

    return result;
  }

}
