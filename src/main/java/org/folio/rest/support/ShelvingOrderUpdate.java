package org.folio.rest.support;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnection;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dbschema.Versioned;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

public class ShelvingOrderUpdate extends Versioned {

  private static final Logger log = LogManager.getLogger();

  private static final String SQL_EXISTS_ITEMS_TO_UPDATE = new StringBuilder()
      .append("SELECT EXISTS (SELECT null FROM item WHERE not (jsonb ? 'effectiveShelvingOrder'))")
      .toString();

  private static final String SQL_SELECT_ITEMS = new StringBuilder()
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
  private static final String FROM_VERSION_SHELVING_ORDER = "20.1.0";

  private static final String OPERATION_STATUS_VERSION_MISMATCH = "Module version isn't expected";
  private static final String OPERATION_STATUS_ITEMS_UPDATES_SUCCESSFUL = "Items updates completed successfully";
  private static final String OPERATION_STATUS_ITEMS_UPDATES_FAILED = "Items updates failed";


  private int updateBunchSize;
  private Map<UUID, String> itemsShelvingOrderMap;
  private Map<UUID, Item> itemsMap;

  public ShelvingOrderUpdate(int updateBunchSize) {
    super();
    this.updateBunchSize = updateBunchSize;
    setFromModuleVersion(FROM_VERSION_SHELVING_ORDER);
    itemsShelvingOrderMap = new LinkedHashMap<>();
    itemsMap = new LinkedHashMap<>();
  }

  public Future<String> updateItems(TenantAttributes attributes, Map<String, String> headers, Context vertxContext) {
    Promise<String> promise = Promise.promise();

    String fromModuleVersion = attributes.getModuleFrom().trim();
    if (StringUtils.isBlank(fromModuleVersion) || isNewForThisInstall(fromModuleVersion)) {
      // Update items only for versions before the expected
      promise.fail(OPERATION_STATUS_VERSION_MISMATCH);
    } else {
      PostgresClient postgresClient =  PgUtil.postgresClient(vertxContext, headers);

      hasItemsToUpdate(vertxContext.owner(), postgresClient)
          .onSuccess(itemsExists -> {
            log.warn("Items update started");
          }).onFailure(h -> {
            log.info("No items for update");
      });
    }

    return promise.future();
  }

  private Future<Boolean> hasItemsToUpdate(Vertx vertx, PostgresClient postgresClient) {
    Promise<Boolean> promise = Promise.promise();

    postgresClient.execute(SQL_EXISTS_ITEMS_TO_UPDATE, ar -> {
      if (ar.succeeded()) {
        RowSet<Row> rows = ar.result();
        boolean itemsExists = rows.iterator().next().getBoolean(0);
        promise.complete(itemsExists);

        fetchAndUpdatesItems(vertx, postgresClient)
            .onSuccess(h -> hasItemsToUpdate(vertx, postgresClient))
            .onFailure(h -> log.warn("Current items updates failed: "  + h.getMessage()));

      } else {
        promise.fail("Failure: " + ar.cause().getMessage());
      }
    });

    return promise.future();
  }

  private Future<Integer> fetchAndUpdatesItems(Vertx vertx, PostgresClient postgresClient) {
    Promise<Integer> promise = Promise.promise();

    vertx.setTimer(500L, timer -> {
      itemsShelvingOrderMap.clear();

      postgresClient.getConnection(ar -> {

        if (ar.failed()) {
          promise.fail("Connection acquiring failure : " + ar.cause().getMessage());
        } else {

          PgConnection conn = ar.result();
          // Starts a new transaction (acquiring and updating of items performs in single transaction)
          conn.begin()
              .compose(tx -> conn

                  // Acquiring current bunch of items
                .preparedQuery(SQL_SELECT_ITEMS)
                .execute(Tuple.of(updateBunchSize))
                .compose(itemsRows -> {
                  final Promise<RowSet<Row>> promiseOfUpdateItems = Promise.promise();

                  // Read items and fill them into data structures for further processing
                  List<Item> itemList = new ArrayList<>();
                  itemsRows.forEach(row -> {
                    UUID itemId = row.getUUID("id");
                    JsonObject itemJsonObject = row.getJsonObject("itemJson");
                    Item item = itemJsonObject.mapTo(Item.class);
                    itemList.add(item);
                    itemsMap.put(itemId, item);
                  });

                  // Calculate items shelving order
                  Map<UUID, JsonObject> updatedItemsMap = itemList.stream()
                      .map(EffectiveCallNumberComponentsUtil::getCalculateAndSetEffectiveShelvingOrder)
                      .collect(Collectors.toMap(item -> UUID.fromString(item.getId()), JsonObject::mapFrom));

                  // Prepare items update parameters
                  List<Tuple> itemsUpdateParams = new ArrayList<>();
                  updatedItemsMap.entrySet().stream()
                      .map(entry -> itemsUpdateParams.add(Tuple.of(entry.getValue(), entry.getKey())))
                      .collect(Collectors.toList());

                  // Updates of items
                  conn
                      .preparedQuery(SQL_UPDATE_ITEMS)
                      .executeBatch(itemsUpdateParams, res -> {
                        if (res.succeeded()) {
                          int updatedItemsCount = res.result().iterator().next().getInteger(0);
                          log.info("There were {} items updated", updatedItemsCount);
                          promiseOfUpdateItems.complete(res.result());
                        } else {
                          log.error("Items updates failed {}", res.cause());
                          promiseOfUpdateItems.fail(res.cause().getMessage());
                        }
                      });

                  return promiseOfUpdateItems.future();
                }
              )
                  // Finish transaction
              .compose(res3 -> {
                tx.commit();
                // Set updated rows count
                promise.complete(res3.size());
                return Future.<Void>succeededFuture();
              })
              .eventually(v -> conn.close())
                  .onSuccess(h -> log.info("Items update transaction succeeded"))
                  .onFailure(h -> log.error("Items update transaction filed: {}", h.getMessage())));

        } // Connection acquired successfully

      }); // Eof getConnection scope

     }); // Eof vert.x timer scope

    return promise.future();
  }

  public static boolean isModuleUpgradeRequest(TenantAttributes attributes) {
    return StringUtils.isNotBlank(attributes.getModuleFrom());
  }

}
