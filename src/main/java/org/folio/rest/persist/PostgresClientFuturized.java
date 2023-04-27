package org.folio.rest.persist;

import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.Promise.promise;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.RowStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.interfaces.Results;

public class PostgresClientFuturized {
  private final PostgresClient postgresClient;

  public PostgresClientFuturized(PostgresClient postgresClient) {
    this.postgresClient = postgresClient;
  }

  public <T> Future<String> save(String table, String id, T entity) {
    return postgresClient.save(table, id, entity);
  }

  public <T> Future<List<T>> get(String tableName, Class<T> type, Criterion criterion) {
    return postgresClient.get(tableName, type, criterion, false).map(Results::getResults);
  }

  public Future<RowSet<Row>> delete(String tableName, Criterion criterion) {
    return postgresClient.delete(tableName, criterion);
  }

  public Future<RowSet<Row>> deleteById(String tableName, String id) {
    return postgresClient.delete(tableName, id);
  }

  public <T> Future<T> getById(String tableName, String id, Class<T> type) {
    return postgresClient.getById(tableName, id, type);
  }

  public <T> Future<Map<String, T>> getById(String tableName, Collection<String> ids, Class<T> type) {
    final Promise<Map<String, T>> promise = promise();

    postgresClient.getById(tableName, new JsonArray(new ArrayList<>(ids)), type, promise);

    return promise.future();
  }

  public Future<SQLConnection> startTx() {
    Promise<SQLConnection> result = promise();

    postgresClient.startTx(result);

    return result.future();
  }

  public Future<RowStream<Row>> selectStream(SQLConnection con, String query) {
    Promise<RowStream<Row>> result = promise();

    postgresClient.selectStream(succeededFuture(con), query, result);

    return result.future();
  }

  public Future<Void> endTx(SQLConnection connection) {
    Promise<Void> result = promise();

    postgresClient.endTx(succeededFuture(connection), result);

    return result.future();
  }

  public String getFullTableName(String tableName) {
    return convertToPsqlStandard(postgresClient.getTenantId()) + "." + tableName;
  }
}
