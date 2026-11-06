package org.folio.persist;

import static org.folio.persist.HoldingsRepository.HOLDINGS_RECORD_TABLE;
import static org.folio.persist.ItemRepository.ITEM_TABLE;
import static org.folio.rest.impl.BoundWithPartApi.BOUND_WITH_TABLE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.InstanceView;
import org.folio.rest.jaxrs.model.ResultInfo;
import org.folio.rest.persist.cql.CQLQueryValidationException;
import org.folio.rest.persist.cql.CQLWrapper;
import org.springframework.stereotype.Repository;

/**
 * Reads the inventory view of the instances: each instance with its holdings records and items.
 */
@Repository
public class InventoryViewRepository extends AbstractRepository<InstanceView> {
  public static final String INSTANCE_HOLDINGS_ITEM_VIEW = "instance_holdings_item_view";
  private static final String INVENTORY_VIEW_JSONB_FIELD = "inventory_view.jsonb";

  public InventoryViewRepository(Context context) {
    super(context, INSTANCE_HOLDINGS_ITEM_VIEW, InstanceView.class);
  }

  public Future<String> getInventoryViewInstancesWithBoundedItems(int offset, int limit, String query,
                                                                  String tenantId) {
    try {
      var sql = buildInventoryViewQueryWithBoundedItems(query, limit, offset, tenantId);
      return postgresClient(tenantId).select(sql.toString())
        .map(this::buildInventoryViewJson);
    } catch (CQLQueryValidationException e) {
      return Future.failedFuture(new BadRequestException(e.getMessage()));
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  /**
   * Looks up inventory view instances by HRID using the indexed {@code instance_hrid} column of the
   * view, so that only the matching instance is built. The HRID is matched exactly, ignoring case
   * and accents, the same normalisation as the {@code instance_hrid_idx_unique} index.
   */
  public Future<String> getInventoryViewInstancesByHrid(String hrid, boolean withBoundedItems,
                                                        int offset, int limit, String tenantId) {
    var sql = withBoundedItems
              ? selectInventoryViewWithBoundedItems(tenantId)
              : new StringBuilder("SELECT inventory_view.jsonb FROM "
                                  + getFullTableName(INSTANCE_HOLDINGS_ITEM_VIEW, tenantId)
                                  + " AS inventory_view ");
    sql.append("WHERE inventory_view.instance_hrid = lower(")
      .append(getFullTableName("f_unaccent", tenantId))
      .append("($1)) LIMIT $2 OFFSET $3");
    return postgresClient(tenantId).execute(sql.toString(), Tuple.of(hrid, limit, offset))
      .map(this::buildInventoryViewJson);
  }

  private StringBuilder buildInventoryViewQueryWithBoundedItems(String query, int limit, int offset, String tenantId) {
    var sql = selectInventoryViewWithBoundedItems(tenantId);
    sql.append(appendCqlQuery(query, limit, offset));
    return sql;
  }

  private StringBuilder selectInventoryViewWithBoundedItems(String tenantId) {
    var sql = new StringBuilder("SELECT JSONB_BUILD_OBJECT(");
    sql.append("'instanceId', inventory_view.jsonb->>'instanceId', ");
    sql.append("'isBoundWith', inventory_view.jsonb->'isBoundWith', ");
    sql.append("'instance', inventory_view.jsonb->'instance', ");
    sql.append("'holdingsRecords', inventory_view.jsonb->'holdingsRecords', ");
    sql.append("'items', ").append(selectItemsWithBoundedRecords(tenantId)).append(") AS jsonb ");
    sql.append("FROM ");
    sql.append(getFullTableName(INSTANCE_HOLDINGS_ITEM_VIEW, tenantId));
    sql.append(" AS inventory_view ");
    return sql;
  }

  private String buildInventoryViewJson(RowSet<Row> rowSet) {
    try {
      var jsonResponse = createInventoryViewJsonResponse(rowSet);
      return jsonResponse.encode();
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private JsonObject createInventoryViewJsonResponse(RowSet<Row> rowSet) throws JsonProcessingException {
    var instances = new JsonArray();
    rowSet.forEach(row -> {
      var rowJsonValue = row.getJsonObject(0);
      instances.add(filterNullFields(rowJsonValue));
    });
    var totalRecords = rowSet.size();
    var resultInfoString = ObjectMapperTool.getMapper().writeValueAsString(
      new ResultInfo().withTotalRecords(totalRecords));
    return new JsonObject()
      .put("instances", instances)
      .put("totalRecords", totalRecords)
      .put("resultInfo", new JsonObject(resultInfoString));
  }

  private StringBuilder selectItemsWithBoundedRecords(String tenantId) {
    var sql = new StringBuilder("(SELECT jsonb_agg(combined_items.jsonb) FROM (");
    sql.append(selectItemsByInstance(tenantId));
    sql.append(" UNION ");
    sql.append(selectBoundedItems(tenantId));
    sql.append(") AS combined_items)");
    return sql;
  }

  private String appendCqlQuery(String query, int limit, int offset) {
    try {
      var field = new CQL2PgJSON(INVENTORY_VIEW_JSONB_FIELD);
      var cqlWrapper = new CQLWrapper(field, query, limit, offset);
      return cqlWrapper.toString();
    } catch (FieldException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private StringBuilder selectItemsByInstance(String tenantId) {
    var sql = new StringBuilder("SELECT item.jsonb FROM ");
    sql.append(getFullTableName(HOLDINGS_RECORD_TABLE, tenantId));
    sql.append(" AS hr JOIN ");
    sql.append(getFullTableName(ITEM_TABLE, tenantId));
    sql.append(" ON item.holdingsRecordId = hr.id AND hr.instanceId = inventory_view.id ");
    return sql;
  }

  private StringBuilder selectBoundedItems(String tenantId) {
    var sql = new StringBuilder("SELECT item.jsonb FROM ");
    sql.append(getFullTableName(ITEM_TABLE, tenantId));
    sql.append(" JOIN ");
    sql.append(getFullTableName(BOUND_WITH_TABLE, tenantId));
    sql.append(" AS bwp ON item.id = bwp.itemid ");
    sql.append("JOIN ");
    sql.append(getFullTableName(HOLDINGS_RECORD_TABLE, tenantId));
    sql.append(" AS hr ON hr.id = bwp.holdingsrecordid AND hr.instanceId = inventory_view.id");
    return sql;
  }

  private JsonObject filterNullFields(JsonObject rowJsonValue) {
    return rowJsonValue
      .stream()
      .filter(field -> field.getValue() != null)
      .collect(
        Collectors.collectingAndThen(
          Collectors.toMap(Entry::getKey, Entry::getValue),
          JsonObject::new
        )
      );
  }
}
