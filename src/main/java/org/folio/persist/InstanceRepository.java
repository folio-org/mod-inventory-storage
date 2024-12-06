package org.folio.persist;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.rest.impl.BoundWithPartApi.BOUND_WITH_TABLE;
import static org.folio.rest.impl.HoldingsStorageApi.HOLDINGS_RECORD_TABLE;
import static org.folio.rest.impl.ItemStorageApi.ITEM_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.RowStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.ResultInfo;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.persist.cql.CQLQueryValidationException;
import org.folio.rest.persist.cql.CQLWrapper;

public class InstanceRepository extends AbstractRepository<Instance> {
  public static final String INSTANCE_TABLE = "instance";
  private static final String INSTANCE_SET_VIEW = "instance_set";
  private static final String INSTANCE_HOLDINGS_ITEM_VIEW = "instance_holdings_item_view";
  private static final String INVENTORY_VIEW_JSONB_FIELD = "inventory_view.jsonb";
  private static final String INSTANCE_SUBJECT_SOURCE_TABLE = "instance_subject_source";
  private static final String INSTANCE_SUBJECT_TYPE_TABLE = "instance_subject_type";


  public InstanceRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), INSTANCE_TABLE, Instance.class);
  }

  public void linkInstanceWithSubjectSourceAndType(Instance instance) {
    instance.getSubjects().forEach(subject -> {
      var sql = new StringBuilder();
      if (subject.getSourceId() != null) {
        sql.append(" INSERT INTO ");
        sql.append(INSTANCE_SUBJECT_SOURCE_TABLE);
        sql.append(" (instance_id, source_id) ");
        sql.append(" VALUES ");
        sql.append(String.format("( '%s' , '%s' ); ", instance.getId(), subject.getSourceId()));
      }
      if (subject.getTypeId() != null) {
        sql.append(" INSERT INTO ");
        sql.append(INSTANCE_SUBJECT_TYPE_TABLE);
        sql.append(" (instance_id, type_id) ");
        sql.append(" VALUES ");
        sql.append(String.format("( '%s' , '%s' ); ", instance.getId(), subject.getTypeId()));
      }
      var sqlString = sql.toString();
      if (!isBlank(sqlString)) {
        postgresClient.execute(sql.toString());
      }
    });
  }


  public Future<RowStream<Row>> getAllIds(SQLConnection connection) {
    return postgresClientFuturized.selectStream(connection,
      "SELECT id FROM " + postgresClientFuturized.getFullTableName(INSTANCE_TABLE));
  }

  /**
   * Delete by CQL. For each deleted record return a {@link Row} with the instance id String
   * and with the instance jsonb String.
   *
   * <p>This automatically deletes connected marc records because the instance_source_marc foreign
   * key has "ON DELETE CASCADE".
   */
  public Future<RowSet<Row>> delete(String cql) {
    try {
      CQLWrapper cqlWrapper = new CQLWrapper(new CQL2PgJSON(tableName + ".jsonb"), cql, -1, -1);
      String sql = "DELETE FROM " + postgresClientFuturized.getFullTableName(tableName)
        + " " + cqlWrapper.getWhereClause()
        + " RETURNING id::text, jsonb::text";
      return postgresClient.execute(sql);
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  @SuppressWarnings("java:S107") // suppress "Methods should not have too many parameters"
  public Future<Response> getInstanceSet(boolean instance, boolean holdingsRecords, boolean items,
                                         boolean precedingTitles, boolean succeedingTitles,
                                         boolean superInstanceRelationships, boolean subInstanceRelationships,
                                         int offset, int limit, String query) {

    try {
      StringBuilder sql = new StringBuilder(200);
      sql.append("SELECT jsonb_build_object('id', id");
      if (instance) {
        sql.append(", 'instance', jsonb");
      }
      if (holdingsRecords) {
        sql.append(", 'holdingsRecords', holdings_records");
      }
      if (items) {
        sql.append(", 'items', items");
      }
      if (precedingTitles) {
        sql.append(", 'precedingTitles', preceding_titles");
      }
      if (succeedingTitles) {
        sql.append(", 'succeedingTitles', succeeding_titles");
      }
      if (superInstanceRelationships) {
        sql.append(", 'superInstanceRelationships', super_instance_relationships");
      }
      if (subInstanceRelationships) {
        sql.append(", 'subInstanceRelationships', sub_instance_relationships");
      }
      sql.append(")::text FROM ")
        .append(postgresClientFuturized.getFullTableName(INSTANCE_SET_VIEW))
        .append(" JOIN ")
        .append(postgresClientFuturized.getFullTableName(INSTANCE_TABLE))
        .append(" USING (id) ");

      var field = new CQL2PgJSON(INSTANCE_TABLE + ".jsonb");
      var cqlWrapper = new CQLWrapper(field, query, limit, offset, "none");
      sql.append(cqlWrapper);

      return postgresClient.select(sql.toString())
        .map(rowSet -> {
          StringBuilder json = new StringBuilder("{\"instanceSets\":[\n");
          boolean first = true;
          for (Row row : rowSet) {
            if (first) {
              first = false;
            } else {
              json.append(",\n");
            }
            json.append(row.getString(0));
          }
          json.append("\n]}");
          return Response.ok(json.toString(), MediaType.APPLICATION_JSON_TYPE).build();
        });
    } catch (CQLQueryValidationException e) {
      return Future.failedFuture(new BadRequestException(e.getMessage()));
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  public Future<List<Map<String, Object>>> getReindexInstances(String fromId, String toId,
                                                               boolean notConsortiumRecords) {
    var sql = new StringBuilder("SELECT i.jsonb || jsonb_build_object('isBoundWith', EXISTS(SELECT 1 FROM ");
    sql.append(postgresClientFuturized.getFullTableName(BOUND_WITH_TABLE));
    sql.append(" as bw JOIN ");
    sql.append(postgresClientFuturized.getFullTableName(ITEM_TABLE));
    sql.append(" as it ON it.id = bw.itemid JOIN ");
    sql.append(postgresClientFuturized.getFullTableName(HOLDINGS_RECORD_TABLE));
    sql.append(" as hr ON hr.id = bw.holdingsrecordid WHERE hr.instanceId = i.id LIMIT 1)) FROM ");
    sql.append(postgresClientFuturized.getFullTableName(INSTANCE_TABLE));
    sql.append(" i WHERE i.id >= '").append(fromId).append("' AND i.id <= '").append(toId).append("'");
    if (notConsortiumRecords) {
      sql.append(" AND i.jsonb->>'source' NOT LIKE 'CONSORTIUM-%'");
    }
    sql.append(";");

    return postgresClient.select(sql.toString()).map(rows -> {
      var resultList = new LinkedList<Map<String, Object>>();
      for (var row : rows) {
        resultList.add(row.getJsonObject(0).getMap());
      }
      return resultList;
    });
  }

  public Future<Response> getInventoryViewInstancesWithBoundedItems(int offset, int limit, String query) {
    try {
      var sql = buildInventoryViewQueryWithBoundedItems(query, limit, offset);
      return postgresClient.select(sql.toString())
        .map(this::buildInventoryViewResponse);
    } catch (CQLQueryValidationException e) {
      return Future.failedFuture(new BadRequestException(e.getMessage()));
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  private StringBuilder buildInventoryViewQueryWithBoundedItems(String query, int limit, int offset) {
    var sql = new StringBuilder("SELECT JSONB_BUILD_OBJECT(");
    sql.append("'instanceId', inventory_view.jsonb->>'instanceId', ");
    sql.append("'isBoundWith', inventory_view.jsonb->'isBoundWith', ");
    sql.append("'instance', inventory_view.jsonb->'instance', ");
    sql.append("'holdingsRecords', inventory_view.jsonb->'holdingsRecords', ");
    sql.append("'items', ").append(selectItemsWithBoundedRecords()).append(") AS jsonb ");
    sql.append("FROM ");
    sql.append(postgresClientFuturized.getFullTableName(INSTANCE_HOLDINGS_ITEM_VIEW));
    sql.append(" AS inventory_view ");
    sql.append(appendCqlQuery(query, limit, offset));
    return sql;
  }

  private Response buildInventoryViewResponse(RowSet<Row> rowSet) {
    try {
      var jsonResponse = createInventoryViewJsonResponse(rowSet);
      return Response.ok(jsonResponse.encode(), MediaType.APPLICATION_JSON_TYPE).build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
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

  private StringBuilder selectItemsWithBoundedRecords() {
    var sql = new StringBuilder("(SELECT jsonb_agg(combined_items.jsonb) FROM (");
    sql.append(selectItemsByInstance());
    sql.append(" UNION ");
    sql.append(selectBoundedItems());
    sql.append(") AS combined_items)");
    return sql;
  }

  private String appendCqlQuery(String query, int limit, int offset) {
    try {
      var field = new CQL2PgJSON(INVENTORY_VIEW_JSONB_FIELD);
      var cqlWrapper = new CQLWrapper(field, query, limit, offset);
      return cqlWrapper.toString();
    } catch (FieldException e) {
      throw new RuntimeException(e);
    }
  }

  private StringBuilder selectItemsByInstance() {
    var sql = new StringBuilder("SELECT item.jsonb FROM ");
    sql.append(postgresClientFuturized.getFullTableName(HOLDINGS_RECORD_TABLE));
    sql.append(" AS hr JOIN ");
    sql.append(postgresClientFuturized.getFullTableName(ITEM_TABLE));
    sql.append(" ON item.holdingsRecordId = hr.id AND hr.instanceId = inventory_view.id ");
    return sql;
  }

  private StringBuilder selectBoundedItems() {
    var sql = new StringBuilder("SELECT item.jsonb FROM ");
    sql.append(postgresClientFuturized.getFullTableName(ITEM_TABLE));
    sql.append(" JOIN ");
    sql.append(postgresClientFuturized.getFullTableName(BOUND_WITH_TABLE));
    sql.append(" AS bwp ON item.id = bwp.itemid ");
    sql.append("JOIN ");
    sql.append(postgresClientFuturized.getFullTableName(HOLDINGS_RECORD_TABLE));
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
