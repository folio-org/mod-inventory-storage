package org.folio.persist;

import static org.folio.dbschema.ObjectMapperTool.readValue;
import static org.folio.persist.HoldingsRepository.HOLDINGS_RECORD_TABLE;
import static org.folio.rest.impl.StorageHelper.MAX_ENTITIES;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnection;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.RowStream;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.entity.DeletedRecord;
import org.folio.persist.entity.ItemWithHoldings;
import org.folio.persist.helper.EntityAccessor;
import org.folio.persist.helper.EntityAccessors;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.ItemPatchRequest;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.utils.DatabaseUtils;
import org.springframework.stereotype.Repository;

@Repository
public class ItemRepository extends AbstractRepository<Item> {

  public static final String ITEM_TABLE = "item";

  private static final Logger logger = LogManager.getLogger(ItemRepository.class);

  private static final EntityAccessor<ItemPatchRequest> PATCH_REQUEST_ACCESSOR =
    EntityAccessors.of(ItemPatchRequest.class);

  private static final String EXPECTED_A_MAXIMUM_RECORDS_TO_PREVENT_OUT_OF_MEMORY =
    "Expected a maximum of %s records to prevent out of memory but got %s";

  public ItemRepository(Context context) {
    super(context, ITEM_TABLE, Item.class);
  }

  public Future<List<Item>> getItemsForHoldingRecord(Conn connection, String holdingRecordId) {
    final Criterion criterion = new Criterion(new Criteria().setJSONB(false)
      .addField("holdingsRecordId").setOperation("=").setVal(holdingRecordId));

    return get(connection, criterion);
  }

  /**
   * Delete by CQL. For each deleted record return a {@link DeletedRecord} with the instance id
   * and  the item's jsonb String.
   */
  public Future<List<DeletedRecord>> delete(String cql, String tenantId) {
    try {
      var fullTableName = getFullTableName(tenantId);
      var whereClause = cqlWrapper(cql).getWhereClause();
      String sql = "DELETE FROM " + fullTableName + " " + whereClause
                   + " RETURNING (SELECT instanceId::text FROM holdings_record"
                   + " WHERE id = holdingsRecordId), jsonb::text";
      return postgresClient(tenantId).execute(sql)
        .map(rows -> {
          var deleted = new ArrayList<DeletedRecord>(rows.size());
          rows.forEach(row -> deleted.add(new DeletedRecord(row.getString(0), row.getString(1))));
          return deleted;
        });
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  public Future<List<Map<String, Object>>> getReindexItemRecords(String fromId, String toId, String tenantId) {
    return postgresClient(tenantId).select(buildReindexItemsSql(fromId, toId, tenantId))
      .map(rows -> rows.stream()
        .map(row -> row.getJsonObject(0).getMap())
        .collect(Collectors.toCollection(LinkedList::new)));
  }

  public Future<RowStream<Row>> streamReindexItemRecords(Conn conn, String fromId, String toId, String tenantId) {
    return DatabaseUtils.selectStream(conn, buildReindexItemsSql(fromId, toId, tenantId));
  }

  /**
   * Returns the item and the holdings record with the given id, empty if there is no such item.
   * The holdings of the result is null if there is no such holdings record.
   */
  public Future<Optional<ItemWithHoldings>> getItemAndHolding(String itemId, String holdingsId, String tenantId) {
    var sql = "SELECT item.jsonb::text, holdings_record.jsonb::text "
              + "FROM " + getFullTableName(tenantId) + " "
              + "LEFT JOIN " + getFullTableName(HOLDINGS_RECORD_TABLE, tenantId)
              + "  ON holdings_record.id = $2 "
              + "WHERE item.id = $1";
    return postgresClient(tenantId).execute(sql, Tuple.of(itemId, holdingsId))
      .map(rowSet -> {
        if (rowSet.size() == 0) {
          return Optional.empty();
        }
        var row = rowSet.iterator().next();
        return Optional.of(new ItemWithHoldings(readValue(row.getString(0), Item.class),
          toHoldings(row.getString(1))));
      });
  }

  /**
   * Updates the item and returns the stored item, empty if there is no item with the id.
   */
  public Future<Optional<Item>> updateReturning(Item item, String tenantId) {
    JsonObject itemJson;
    try {
      itemJson = PostgresClient.pojo2JsonObject(item);
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
    return postgresClient(tenantId)
      .execute("UPDATE " + getFullTableName(tenantId) + " SET jsonb=$1 WHERE id=$2 RETURNING jsonb::text",
        Tuple.of(itemJson, item.getId()))
      .map(rowSet -> rowSet.size() == 1
                     ? Optional.of(readValue(rowSet.iterator().next().getString(0), Item.class))
                     : Optional.empty());
  }

  public Future<List<Item>> updateItems(Conn conn, List<ItemPatchRequest> items, String tenantId) {
    try {
      if (items.size() > MAX_ENTITIES) {
        return Future.failedFuture(buildItemsSizeException(items.size()));
      }

      items.forEach(PATCH_REQUEST_ACCESSOR::unsetVersionIfMinusOne);

      var tuples = items.stream()
        .map(item -> Tuple.of(JsonObject.mapFrom(item), item.getId()))
        .toList();
      var sql = "UPDATE %s SET jsonb = jsonb || $1 WHERE id = $2 RETURNING jsonb::text"
        .formatted(getFullTableName(tenantId));

      return executeBatchUpdate(conn.getPgConnection(), sql, tuples);
    } catch (Exception e) {
      logger.error("Failed to update items batch", e);
      return Future.failedFuture(e);
    }
  }

  /**
   * Returns the existing items of the ids, each with its current holdings record, null if it has none.
   */
  public Future<List<ItemWithHoldings>> getItemsWithCurrentHoldings(List<String> itemIds, String tenantId) {
    if (itemIds.isEmpty()) {
      return Future.succeededFuture(List.of());
    }

    var itemIdsArray = String.join("','", itemIds);
    var sql = """
      SELECT item.jsonb::text, current_holdings.jsonb::text
      FROM %s item
      LEFT JOIN %s current_holdings ON current_holdings.id = (item.jsonb->>'holdingsRecordId')::uuid
      WHERE item.id = ANY(ARRAY['%s']::uuid[])
      """.formatted(
      getFullTableName(tenantId),
      getFullTableName(HOLDINGS_RECORD_TABLE, tenantId),
      itemIdsArray
    );

    return postgresClient(tenantId).execute(sql)
      .map(rowSet -> {
        var result = new ArrayList<ItemWithHoldings>(rowSet.size());
        rowSet.forEach(row -> result.add(new ItemWithHoldings(
          readValue(row.getString(0), Item.class), toHoldings(row.getString(1)))));
        return result;
      });
  }

  private HoldingsRecord toHoldings(String json) {
    return json == null ? null : readValue(json, HoldingsRecord.class);
  }

  private String buildReindexItemsSql(String fromId, String toId, String tenantId) {
    return "SELECT i.jsonb || jsonb_build_object('instanceId', hr.instanceId)"
           + " FROM " + getFullTableName(tenantId) + " i"
           + " JOIN " + getFullTableName(HOLDINGS_RECORD_TABLE, tenantId)
           + " hr ON i.holdingsrecordid = hr.id"
           + " WHERE i.id >= '" + fromId + "' AND i.id <= '" + toId + "';";
  }

  private ValidationException buildItemsSizeException(int actualSize) {
    var message = EXPECTED_A_MAXIMUM_RECORDS_TO_PREVENT_OUT_OF_MEMORY.formatted(MAX_ENTITIES, actualSize);
    var errors = new Errors().withErrors(List.of(
      new Error()
        .withMessage(message)
        .withType("1")
        .withCode("VALIDATION_ERROR")));
    return new ValidationException(errors);
  }

  private Future<List<Item>> executeBatchUpdate(PgConnection conn, String sql, List<Tuple> tuples) {
    return conn.preparedQuery(sql).executeBatch(tuples)
      .map(rowSet -> {
        List<Item> updatedItems = new LinkedList<>();

        RowSet<Row> current = rowSet;
        while (current != null) {
          for (Row row : current) {
            updatedItems.add(readValue(row.getString(0), Item.class));
          }
          current = current.next(); // Move to the result of the next tuple in the batch
        }

        return updatedItems;
      })
      .recover(throwable -> {
        logger.error("Failed to update items batch", throwable);
        return Future.failedFuture(throwable);
      });
  }
}
