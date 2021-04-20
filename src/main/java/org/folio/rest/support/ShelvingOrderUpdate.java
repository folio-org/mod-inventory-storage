package org.folio.rest.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnection;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
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

  public static final String UPDATED_ATTR_NAME = "effectiveShelvingOrder";

  private static final String SQL_SELECT_ITEMS =
          "SELECT id AS \"id\", " +
          "jsonb AS \"itemJson\" " +
          "FROM item WHERE NOT (jsonb ? '" + UPDATED_ATTR_NAME + "') " +
          "FOR UPDATE " +
          "LIMIT $1";

  private static final String SQL_UPDATE_ITEMS =
      "UPDATE item " +
      "SET jsonb = jsonb - '" + UPDATED_ATTR_NAME + "' || jsonb_build_object('" + UPDATED_ATTR_NAME + "', $2) " +
      "WHERE id = $1 " +
//      "AND COALESCE($2, '') <> ''" +
      "AND true";

  // Defines version threshold where this property was introduces from
  private static final String VERSION_WITH_SHELVING_ORDER = "20.1.0";

  public static int DEF_FETCH_SIZE = 5000;

  private static ShelvingOrderUpdate instance;
  private Versioned versionChecker;
  private int rowsBunchSize;

  public static ShelvingOrderUpdate getInstance() {
    return getInstance(DEF_FETCH_SIZE);
  }

  public static ShelvingOrderUpdate getInstance(int rowsBunchSize) {
    if (Objects.isNull(instance)) {
      synchronized (ShelvingOrderUpdate.class) {
        if (Objects.isNull(instance)) {
          instance = new ShelvingOrderUpdate(rowsBunchSize);
          log.info("An shelving oder updater instantiated, rowsBunchSize: {}", rowsBunchSize);
        }
      }
    }
    return instance;
  }

  private ShelvingOrderUpdate(int rowsBunchSize) {
    this.versionChecker = new Versioned() {};
    this.versionChecker.setFromModuleVersion(VERSION_WITH_SHELVING_ORDER);
    this.rowsBunchSize = rowsBunchSize;
  }

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

    final Vertx vertx = vertxContext.owner();

    log.info("Update items started, moduleTo is: \"{}\",  moduleFrom is: \"{}\"",
        attributes.getModuleTo(), attributes.getModuleFrom());

    if (! ShelvingOrderUpdate.getInstance().isAllowedToUpdate(attributes)) {
      log.info("Module isn't eligible for upgrade, just exit.");
      // Not allowed to perform items updates, just returns
      return Future.succeededFuture(0);
    }

    log.info("Module is eligible for upgrade.");
    // Updates items with shelving order property.
    // This should be triggered once for particular version only.
    PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, headers);

    final long startTime = System.currentTimeMillis();

    return fetchAndUpdateAll(vertx, postgresClient)
        .onComplete(x -> log.info("Items updates with shelving order property completed in {} milliseconds",
            (System.currentTimeMillis() - startTime)));
  }

  public Future<Integer> fetchAndUpdateAll(Vertx vertx, PostgresClient postgresClient) {
    return fetchAndUpdateBunch(vertx, postgresClient)
    .compose(headRows -> {
      if (headRows <= 0) {
        return Future.succeededFuture(0);
      }
      return fetchAndUpdateAll(vertx, postgresClient).map(tailRows -> headRows + tailRows);
    });
  }

  /**
   * Performs fetching items by fixed bunches of rows with further property calculations and
   * updating in database
   *
   * @param vertx
   * @param postgresClient
   * @return
   */
  public Future<Integer> fetchAndUpdateBunch(Vertx vertx, PostgresClient postgresClient) {
    log.info("The routine of \"fetchAndUpdateBunch\" has started");

    return postgresClient.withTransaction(connection -> connection
        // Acquiring current bunch of items
        .preparedQuery(SQL_SELECT_ITEMS)
        .execute(Tuple.of(rowsBunchSize))
        .compose(fetchedRows -> {
              // Read items and fill them into data structures for further processing
              List<Item> arrangedItems = aggregateRow2List(fetchedRows);

              // Calculate items shelving order
              Map<UUID, String> calculatedPropertyItems = calculateShelvingOrder(arrangedItems);

              // Prepare items update parameters
              List<Tuple> itemsParams = prepareItemsUpdate(calculatedPropertyItems);

              log.info("The routine of \"fetchAndUpdateBunch\" is about to be finished");
              // Updates of items
              return updateItems(itemsParams, connection);
            }
        ));
  }

  /**
   * Read items and fill them into data structures for further processing
   *
   * @param rowSet
   * @return
   */
  public List<Item> aggregateRow2List(RowSet<Row> rowSet) {
    log.info("Invoking of aggregateRow2List, rows size: {}", rowSet.size());

    List<Item> result;
    if (rowSet.size() > 0) {
      List<Item> targetList = new ArrayList<>();
      rowSet.forEach(row -> {
        JsonObject itemJsonObject = row.getJsonObject("itemJson");
        log.info("Read JSON from database as JsonObject : {}", itemJsonObject);
        log.info("JonObject toString: {}", itemJsonObject.toString());
        Item item = itemJsonObject.mapTo(Item.class);
        log.info("Deserialized item which was converted to JSON string : {}", item);
        targetList.add(item);
      });
      result = targetList;
    } else {
      result = Collections.emptyList();
    }

    log.info("Finishing of aggregateRow2List, result: {}", result);
    return result;
  }

  /**
   * Calculate items shelving order
   *
   * @return
   */
  public Map<UUID, String> calculateShelvingOrder(List<Item> itemList) {
    log.info("Invoking of calculateShelvingOrder, itemList size: {}",
        Optional.ofNullable(itemList).orElse(new ArrayList<>()).size());

    Map<UUID, String> updatedItemsShelvingOrderMap = itemList.stream()
        .map(EffectiveCallNumberComponentsUtil::getCalculateAndSetEffectiveShelvingOrder)
        .collect(Collectors.toMap(
            item -> UUID.fromString(item. getId()),
            item -> {
              log.info("map's value, an item: {}", item);
              return item.getEffectiveShelvingOrder();
            }));

    log.info("Finishing of calculateShelvingOrder, updatedItemsShelvingOrderMap: {}", updatedItemsShelvingOrderMap);
    return updatedItemsShelvingOrderMap;
  }

  /**
   * Prepare items update parameters
   *
   * @param itemIdShelvingOrderMap
   * @return
   */
  public List<Tuple> prepareItemsUpdate(Map<UUID, String> itemIdShelvingOrderMap) {
    log.info("Invoking of prepareItemsUpdate, itemIdShelvingOrderMap: {}", itemIdShelvingOrderMap);

    List<Tuple> itemsUpdateParams = new ArrayList<>();
    itemIdShelvingOrderMap.entrySet().stream()
        .map(entry -> itemsUpdateParams.add(Tuple.of(entry.getKey(), entry.getValue())))
        .collect(Collectors.toList());

    log.info("Finishing of prepareItemsUpdate, itemsUpdateParams: {}, single tuple: {}", itemsUpdateParams, itemsUpdateParams.stream().findFirst().orElse(Tuple.of(StringUtils.EMPTY)));
    return itemsUpdateParams;
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
    log.info("Items parameters for update SQL: {}, query itself: {}",
        StringUtils.defaultIfBlank(itemsParams.stream().map(Tuple::deepToString).collect(Collectors.joining(",")),
            "none"), SQL_UPDATE_ITEMS);

    if (itemsParams.isEmpty()) {
      log.info("Items parameters for update SQL are empty");
      return Future.succeededFuture(0);
    }

    return connection.preparedQuery(SQL_UPDATE_ITEMS).executeBatch(itemsParams)
        .map(result -> {
          int updatedItemsCount = result.rowCount();
          log.info("There were {} items updated", updatedItemsCount);
          return updatedItemsCount;
        })
        .onFailure(handler -> {
          log.error("Items updates failed: {} ", handler.getMessage());
        })
        .onComplete(ar ->log.info("Finishing of updateItems, promise: {}", ar.result()));
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

  public ShelvingOrderUpdate withLimit(int rowLimit) {
    this.rowsBunchSize = rowLimit;
    return this;
  }
}
