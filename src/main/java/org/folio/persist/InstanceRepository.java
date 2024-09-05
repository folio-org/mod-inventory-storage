package org.folio.persist;

import static org.folio.rest.impl.BoundWithPartApi.BOUND_WITH_TABLE;
import static org.folio.rest.impl.HoldingsStorageApi.HOLDINGS_RECORD_TABLE;
import static org.folio.rest.impl.ItemStorageApi.ITEM_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.RowStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.persist.cql.CQLQueryValidationException;
import org.folio.rest.persist.cql.CQLWrapper;

public class InstanceRepository extends AbstractRepository<Instance> {
  public static final String INSTANCE_TABLE = "instance";
  private static final String INSTANCE_SET_VIEW = "instance_set";

  public InstanceRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), INSTANCE_TABLE, Instance.class);
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

  public Future<List<String>> getReindexInstances(String fromId, String toId, boolean notConsortiumCentralTenant) {
    var sql = new StringBuilder("SELECT i.jsonb || jsonb_build_object('isBoundWith', EXISTS(SELECT 1 FROM ");
    sql.append(postgresClientFuturized.getFullTableName(BOUND_WITH_TABLE));
    sql.append(" as bw JOIN ");
    sql.append(postgresClientFuturized.getFullTableName(ITEM_TABLE));
    sql.append(" as it ON it.id = bw.itemid JOIN ");
    sql.append(postgresClientFuturized.getFullTableName(HOLDINGS_RECORD_TABLE));
    sql.append(" as hr ON hr.id = bw.holdingsrecordid WHERE hr.instanceId = i.id LIMIT 1)) FROM ");
    sql.append(postgresClientFuturized.getFullTableName(INSTANCE_TABLE));
    sql.append(" i WHERE i.id >= '").append(fromId).append("' AND i.id <= '").append(toId).append("'");
    if (notConsortiumCentralTenant) {
      sql.append(" AND i.jsonb->>'source' NOT LIKE 'CONSORTIUM-%'");
    }
    sql.append(";");

    return postgresClient.select(sql.toString()).map(rows -> {
      var resultList = new LinkedList<String>();
      for (var row : rows) {
        resultList.add(row.getJsonObject(0).encode());
      }
      return resultList;
    });
  }
}
