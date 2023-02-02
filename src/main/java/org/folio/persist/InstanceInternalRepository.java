package org.folio.persist;

import static io.vertx.core.Promise.promise;
import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.RowStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.persist.entity.InstanceInternal;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.persist.cql.CQLQueryValidationException;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;

public class InstanceInternalRepository extends AbstractRepository<Instance> {

  public static final String INSTANCE_TABLE = "instance";

  private static final String INSTANCE_SET_VIEW = "instance_set";

  public InstanceInternalRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), INSTANCE_TABLE, Instance.class);
  }

  private static Instance toInstance(InstanceInternal internal) {
    return internal != null ? internal.toInstanceDto() : null;
  }

  private static List<Instance> toInstanceList(List<InstanceInternal> internalList) {
    if (internalList == null) {
      return Collections.emptyList();
    }
    return internalList.stream()
      .map(InstanceInternal::toInstanceDto).collect(Collectors.toList());
  }

  private static Map<String, Instance> toInstancesMap(Map<String, InstanceInternal> internalMap) {
    if (internalMap == null) {
      return Collections.emptyMap();
    }
    return internalMap.entrySet().stream()
      .collect(Collectors.toMap(Map.Entry::getKey, entry -> toInstance(entry.getValue())));
  }

  @Override
  public Future<Instance> getById(String id) {
    return postgresClientFuturized.getById(tableName, id, InstanceInternal.class)
      .map(instanceInternal -> Objects.nonNull(instanceInternal) ? instanceInternal.toInstanceDto() : null);
  }

  @Override
  public Future<List<Instance>> get(Criterion criterion) {
    return postgresClientFuturized.get(tableName, InstanceInternal.class, criterion)
      .map(InstanceInternalRepository::toInstanceList);
  }

  @Override
  public Future<List<Instance>> get(AsyncResult<SQLConnection> connection, Criterion criterion) {
    final Promise<Results<InstanceInternal>> getItemsResult = promise();

    postgresClient.get(connection, tableName, InstanceInternal.class, criterion, false, true, getItemsResult);

    return getItemsResult.future().map(Results::getResults).map(InstanceInternalRepository::toInstanceList);
  }

  @Override
  public Future<Map<String, Instance>> getById(Collection<String> ids) {
    return postgresClientFuturized.getById(tableName, ids, InstanceInternal.class)
      .map(InstanceInternalRepository::toInstancesMap);
  }

  public Future<RowStream<Row>> getAllIds(SQLConnection connection) {
    return postgresClientFuturized.selectStream(connection,
      "SELECT id FROM " + postgresClientFuturized.getFullTableName(INSTANCE_TABLE));
  }

  public Future<RowSet<Row>> delete(String cql) {
    try {
      CQLWrapper cqlWrapper = new CQLWrapper(new CQL2PgJSON(tableName + ".jsonb"), cql, -1, -1);
      String sql = String.format("DELETE FROM %s %s RETURNING id::text, jsonb::text",
        postgresClientFuturized.getFullTableName(tableName), cqlWrapper.getWhereClause());
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
        sql.append(", 'instance', migrate_series_and_subjects(jsonb)");
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
      sql.append(cqlWrapper.toString());

      return postgresClient.select(sql.toString())
        .map(rowSet -> {
          StringBuilder json = new StringBuilder("{\"instanceSets\":[\n");
          boolean first = true;
          var iterator = rowSet.iterator();
          while (iterator.hasNext()) {
            if (first) {
              first = false;
            } else {
              json.append(",\n");
            }
            json.append(iterator.next().getString(0));
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

}
