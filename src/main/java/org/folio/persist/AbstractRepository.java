package org.folio.persist;

import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.persist.interfaces.Results;

public abstract class AbstractRepository<T> {

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

  public void save(String id, T entity, Handler<AsyncResult<T>> replyHandler) {
    postgresClientFuturized.save(tableName, id, entity, replyHandler);
  }

  public Future<List<T>> get(Criterion criterion) {
    return postgresClientFuturized.get(tableName, recordType, criterion);
  }

  public Future<List<T>> get(AsyncResult<SQLConnection> connection, Criterion criterion) {
    return postgresClient.withConn(connection, conn -> conn.get(tableName, recordType, criterion, false))
      .map(Results::getResults);
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
    return postgresClient.withConn(connection, conn -> conn.update(tableName, entity, id));
  }

  public Future<RowSet<Row>> update(SQLConnection connection, String id, T entity) {
    return update(succeededFuture(connection), id, entity);
  }

  public Future<RowSet<Row>> update(String id, T entity) {
    return postgresClient.update(tableName, entity, id);
  }

  public Future<RowSet<Row>> updateBatch(List<T> records, SQLConnection connection) {
    return postgresClient.withConn(succeededFuture(connection), conn -> conn.upsertBatch(tableName, records));
  }

  public Future<T> fetchAndUpdate(String id, UnaryOperator<T> builder) {
    return postgresClient.withTrans(conn -> conn.getByIdForUpdate(tableName, id, recordType)
      .map(builder)
      .compose(response -> conn.update(tableName, response, id)
        .map(response)));
  }

  public Future<RowSet<Row>> deleteAll() {
    return postgresClientFuturized.delete(tableName, new Criterion());
  }

  public Future<RowSet<Row>> deleteById(String id) {
    return postgresClientFuturized.deleteById(tableName, id);
  }
}
