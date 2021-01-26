package org.folio.persist;

import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.Promise.promise;
import static java.lang.String.format;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public abstract class AbstractRepository<T> {
  protected final PostgresClientFuturized postgresClientFuturized;
  protected final PostgresClient postgresClient;
  protected final String tableName;
  protected final Class<T> recordType;

  protected AbstractRepository(PostgresClient postgresClient, String tableName,
    Class<T> recordType) {

    this.postgresClientFuturized = new PostgresClientFuturized(postgresClient);
    this.postgresClient = postgresClient;
    this.tableName = tableName;
    this.recordType = recordType;
  }

  public Future<String> save(String id, T entity) {
    return postgresClientFuturized.save(tableName, id, entity);
  }

  public Future<T> getById(String id) {
    return postgresClientFuturized.getById(tableName, id, recordType);
  }

  public Future<List<T>> get(Criterion criterion) {
    return postgresClientFuturized.get(tableName, recordType, criterion);
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

  public Future<RowSet<Row>> update(AsyncResult<SQLConnection> connection, String id, T record) {
    final Promise<RowSet<Row>> promise = promise();

    postgresClient.update(connection, tableName, record, "jsonb",
      format("WHERE id = '%s'", id), false, promise);

    return promise.future();
  }

  public Future<RowSet<Row>> update(SQLConnection connection, String id, T record) {
    return update(succeededFuture(connection), id, record);
  }

  public Future<RowSet<Row>> deleteAll() {
    return postgresClientFuturized.delete(tableName, new Criterion());
  }
}
