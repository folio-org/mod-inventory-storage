package org.folio.persist;

import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.Promise.promise;
import static java.lang.String.format;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.persist.interfaces.Results;

public abstract class AbstractRepository<T> {

  private static final String JSON_COLUMN = "jsonb";

  protected final PostgresClientFuturized postgresClientFuturized;
  protected final PostgresClient postgresClient;
  protected final String tableName;
  protected final Class<T> recordType;

  protected AbstractRepository(PostgresClient postgresClient, String tableName, Class<T> recordType) {

    this.postgresClientFuturized = new PostgresClientFuturized(postgresClient);
    this.postgresClient = postgresClient;
    this.tableName = tableName;
    this.recordType = recordType;
  }

  public Future<String> save(String id, T entity) {
    return postgresClientFuturized.save(tableName, id, entity);
  }

  public Future<List<T>> get(Criterion criterion) {
    return postgresClientFuturized.get(tableName, recordType, criterion);
  }

  public Future<List<T>> get(AsyncResult<SQLConnection> connection, Criterion criterion) {
    final Promise<Results<T>> getItemsResult = promise();

    postgresClient.get(connection, tableName, recordType, criterion, false, true, getItemsResult);

    return getItemsResult.future().map(Results::getResults);
  }

  public Future<T> getById(String id) {
    return postgresClientFuturized.getById(tableName, id, recordType);
  }

  public Future<Map<String, T>> getById(Collection<String> ids) {
    return postgresClientFuturized.getById(tableName, ids, recordType);
  }

  public <V> Future<Map<String, T>> getById(Collection<V> records, Function<V, String> mapper) {
    final Set<String> ids = records.stream()
      .map(mapper)
      .collect(Collectors.toSet());

    return getById(ids);
  }

  public Future<Boolean> exists(String id) {
    return postgresClient.execute(
        "select 1 from " + postgresClient.getSchemaName() + "." + tableName + " where id = $1 limit 1",
        Tuple.of(id))
      .map(ar -> ar != null && ar.rowCount() == 1);
  }

  public Future<RowSet<Row>> update(AsyncResult<SQLConnection> connection, String id, T entity) {
    final Promise<RowSet<Row>> promise = promise();

    postgresClient.update(connection, tableName, entity, JSON_COLUMN,
      format("WHERE id = '%s'", id), false, promise);

    return promise.future();
  }

  public Future<RowSet<Row>> update(SQLConnection connection, String id, T entity) {
    return update(succeededFuture(connection), id, entity);
  }

  public Future<RowSet<Row>> update(String id, T entity) {
    final Promise<RowSet<Row>> promise = promise();

    postgresClient.update(tableName, entity, id, promise);

    return promise.future();
  }

  public Future<RowSet<Row>> update(List<T> records) {
    final Promise<RowSet<Row>> promise = promise();

    postgresClient.upsertBatch(tableName, records, promise);

    return promise.future();
  }

  public Future<RowSet<Row>> updateBatch(List<T> records, SQLConnection connection) {
    final Promise<RowSet<Row>> promise = promise();

    postgresClient.upsertBatch(Future.succeededFuture(connection), tableName, records, promise);

    return promise.future();
  }

  public Future<RowSet<Row>> deleteAll() {
    return postgresClientFuturized.delete(tableName, new Criterion());
  }

  public Future<RowSet<Row>> deleteById(String id) {
    return postgresClientFuturized.deleteById(tableName, id);
  }
}
