package org.folio.services.persist;

import static io.vertx.core.Promise.promise;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.folio.rest.persist.PostgresClient;
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

  public <T> Future<T> getById(String tableName, String id, Class<T> type) {
    final Promise<T> getByIdResult = promise();

    postgresClient.getById(tableName, id, type, getByIdResult);

    return getByIdResult.future();
  }

  public <T> Future<List<T>> get(String tableName, T sample) {
    final Promise<Results<T>> getAllItemsResult = promise();

    postgresClient.get(tableName, sample, false, getAllItemsResult);

    return getAllItemsResult.future().map(Results::getResults);
  }

  public Future<RowSet<Row>> execute(String query) {
    final Promise<RowSet<Row>> removeAllResult = promise();

    postgresClient.execute(query, removeAllResult);

    return removeAllResult.future();
  }

  public <T> Future<Map<String, T>> getById(String tableName, Collection<String> ids, Class<T> type) {
    final Promise<Map<String, T>> promise = promise();

    postgresClient.getById(tableName, new JsonArray(new ArrayList<>(ids)), type, promise);

    return promise.future();
  }
}
