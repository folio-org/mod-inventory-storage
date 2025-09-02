package org.folio.persist;

import static org.folio.dbschema.ObjectMapperTool.readValue;
import static org.folio.rest.impl.HoldingsStorageApi.HOLDINGS_RECORD_TABLE;
import static org.folio.rest.impl.ItemStorageApi.ITEM_TABLE;
import static org.folio.rest.impl.StorageHelper.MAX_ENTITIES;
import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnection;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.ItemPatch;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.OptimisticLockingUtil;

public class ItemRepository extends AbstractRepository<Item> {

  private static final Logger logger = LogManager.getLogger(ItemRepository.class);
  private static final String EXPECTED_A_MAXIMUM_RECORDS_TO_PREVENT_OUT_OF_MEMORY =
    "Expected a maximum of %s records to prevent out of memory but got %s";

  public ItemRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), ITEM_TABLE, Item.class);
  }

  public Future<List<Item>> getItemsForHoldingRecord(Conn connection, String holdingRecordId) {
    final Criterion criterion = new Criterion(new Criteria().setJSONB(false)
      .addField("holdingsRecordId").setOperation("=").setVal(holdingRecordId));

    return get(connection, criterion);
  }

  /**
   * Delete by CQL. For each deleted record return a {@link Row} with the instance id String
   * and with the item's jsonb String.
   */
  public Future<RowSet<Row>> delete(String cql) {
    try {
      CQLWrapper cqlWrapper = new CQLWrapper(new CQL2PgJSON(tableName + ".jsonb"), cql, -1, -1);
      String sql = "DELETE FROM " + getFullTableName(tableName)
        + " " + cqlWrapper.getWhereClause()
        + " RETURNING (SELECT instanceId::text FROM holdings_record WHERE id = holdingsRecordId), jsonb::text";
      return postgresClient.execute(sql);
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  public Future<List<Map<String, Object>>> getReindexItemRecords(String fromId, String toId) {
    var sql = "SELECT i.jsonb || jsonb_build_object('instanceId', hr.instanceId)"
              + " FROM " + getFullTableName(ITEM_TABLE) + " i"
              + " JOIN " + getFullTableName(HOLDINGS_RECORD_TABLE)
              + " hr ON i.holdingsrecordid = hr.id"
              + " WHERE i.id >= '" + fromId + "' AND i.id <= '" + toId + "';";

    return postgresClient.select(sql).map(rows -> {
      var resultList = new LinkedList<Map<String, Object>>();
      for (var row : rows) {
        resultList.add(row.getJsonObject(0).getMap());
      }
      return resultList;
    });
  }

  public Future<List<Item>> updateItems(PgConnection conn, List<ItemPatch> items) {
    try {
      if (items.size() > MAX_ENTITIES) {
        var message = EXPECTED_A_MAXIMUM_RECORDS_TO_PREVENT_OUT_OF_MEMORY.formatted(MAX_ENTITIES, items.size());
        var errors = new Errors().withErrors(List.of(
          new Error()
            .withMessage(message)
            .withType("1")
            .withCode("VALIDATION_ERROR")));
        return Future.failedFuture(new ValidationException(errors));
      }
      OptimisticLockingUtil.unsetVersionIfMinusOne(items);

      var tuples = items.stream()
        .map(item -> Tuple.of(JsonObject.mapFrom(item), item.getId()))
        .toList();
      var sql = "UPDATE %s SET jsonb = jsonb || $1 WHERE id = $2 RETURNING jsonb::text"
        .formatted(getFullTableName(ITEM_TABLE));

      return conn.preparedQuery(sql).executeBatch(tuples)
        .map(rowSet -> {
          List<Item> updatedItems = new LinkedList<>();
          for (Row row : rowSet) {
            updatedItems.add(readValue(row.getString(0), Item.class));
          }
          return updatedItems;
        })
        .recover(throwable -> {
          logger.error("Failed to update items batch", throwable);
          return Future.failedFuture(throwable);
        });
    } catch (Exception e) {
      logger.error("Failed to update items batch", e);
      return Future.failedFuture(e);
    }
  }

  public Future<RowSet<Row>> getItemsWithCurrentHoldings(List<String> itemIds) {
    if (itemIds.isEmpty()) {
      return Future.succeededFuture();
    }

    var itemIdsArray = String.join("','", itemIds);
    var sql = """
      SELECT
        item.id::text as item_id,
        item.jsonb::text as item_json,
        current_holdings.jsonb::text as current_holdings_json,
        (item.jsonb->>'holdingsRecordId')::text as current_holdings_id
      FROM %s item
      LEFT JOIN %s current_holdings ON current_holdings.id = (item.jsonb->>'holdingsRecordId')::uuid
      WHERE item.id = ANY(ARRAY['%s']::uuid[])
      """.formatted(
      getFullTableName(ITEM_TABLE),
      getFullTableName(HOLDINGS_RECORD_TABLE),
      itemIdsArray
    );

    return postgresClient.execute(sql);
  }
}
