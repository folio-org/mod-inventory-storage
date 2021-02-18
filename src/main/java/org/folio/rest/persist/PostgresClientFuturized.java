package org.folio.rest.persist;

import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.Promise.promise;

import io.vertx.sqlclient.RowStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.interfaces.Results;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public class PostgresClientFuturized {
  private final PostgresClient postgresClient;

  public PostgresClientFuturized(PostgresClient postgresClient) {
    this.postgresClient = postgresClient;
  }

  public <T> Future<String> save(String table, String id, T entity) {
    final Promise<String> saveResult = promise();

    postgresClient.save(table, id, entity, saveResult);

    return saveResult.future();
  }

  public <T> Future<T> saveAndReturnEntity(String table, String id, T entity) {
    final Promise<T> saveResult = promise();

    postgresClient.saveAndReturnUpdatedEntity(table, id, entity, saveResult);

    return saveResult.future();
  }

  public <T> Future<T> getById(String tableName, String id, Class<T> type) {
    final Promise<T> getByIdResult = promise();

    postgresClient.getById(tableName, id, type, getByIdResult);

    return getByIdResult.future();
  }

  public <T> Future<List<T>> get(String tableName, T object) {
    final Promise<Results<T>> getAllItemsResult = promise();

    postgresClient.get(tableName, object, false, getAllItemsResult);

    return getAllItemsResult.future().map(Results::getResults);
  }

  public <T> Future<List<T>> get(String tableName, Class<T> type, Criterion criterion) {
    final Promise<Results<T>> getItemsResult = promise();

    postgresClient.get(tableName, type, criterion, false, getItemsResult);

    return getItemsResult.future().map(Results::getResults);
  }

  public Future<RowSet<Row>> delete(String tableName, Criterion criterion) {
    final Promise<RowSet<Row>> removeAllResult = promise();

    postgresClient.delete(tableName, criterion, removeAllResult);

    return removeAllResult.future();
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
}
