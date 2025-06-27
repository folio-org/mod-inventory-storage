package org.folio.persist;

import static org.folio.rest.impl.HoldingsStorageApi.HOLDINGS_RECORD_TABLE;
import static org.folio.rest.impl.ItemStorageApi.ITEM_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.cql.CQLWrapper;

public class ItemRepository extends AbstractRepository<Item> {
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
}
