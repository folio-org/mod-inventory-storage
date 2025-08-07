package org.folio.persist;

import static io.vertx.core.Promise.promise;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;

public abstract class AbstractRepository<T> {

  protected final PostgresClient postgresClient;
  protected final String tableName;
  protected final Class<T> recordType;

  protected AbstractRepository(PostgresClient postgresClient, String tableName, Class<T> recordType) {
    this.postgresClient = postgresClient;
    this.tableName = tableName;
    this.recordType = recordType;
  }

  public Future<String> save(String id, T entity) {
    return postgresClient.save(tableName, id, entity);
  }

  public Future<List<T>> get(Criterion criterion) {
    return postgresClient.get(tableName, recordType, criterion, false)
      .map(Results::getResults);
  }

  public Future<List<T>> get(Conn connection, Criterion criterion) {
    return connection.get(tableName, recordType, criterion, false)
      .map(Results::getResults);
  }

  public Future<T> getById(String id) {
    return postgresClient.getById(tableName, id, recordType);
  }

  public Future<Map<String, T>> getByIds(Collection<String> ids) {
    final Promise<Map<String, T>> promise = promise();

    postgresClient.getById(tableName, new JsonArray(new ArrayList<>(ids)), recordType, promise);

    return promise.future();
  }

  public <V> Future<Map<String, T>> getByIds(Collection<V> records, Function<V, String> mapper) {
    final Set<String> ids = records.stream()
      .map(mapper)
      .collect(Collectors.toSet());

    return getByIds(ids);
  }

  public Future<Boolean> exists(String id) {
    return postgresClient.execute(
        "select 1 from " + postgresClient.getSchemaName() + "." + tableName + " where id = $1 limit 1",
        Tuple.of(id))
      .map(ar -> ar != null && ar.rowCount() == 1);
  }

  public Future<RowSet<Row>> update(Conn connection, String id, T entity) {
    return connection.update(tableName, entity, id);
  }

  public Future<RowSet<Row>> update(String id, T entity) {
    return postgresClient.update(tableName, entity, id);
  }

  public Future<RowSet<Row>> updateBatch(List<T> records, Conn connection) {
    return connection.upsertBatch(tableName, records);
  }

  public Future<T> fetchAndUpdate(String id, UnaryOperator<T> builder) {
    return postgresClient.withTrans(conn -> conn.getByIdForUpdate(tableName, id, recordType)
      .map(builder)
      .compose(response -> conn.update(tableName, response, id)
        .map(response)));
  }

  public Future<RowSet<Row>> deleteAll() {
    return postgresClient.delete(tableName, new Criterion());
  }

  public Future<RowSet<Row>> deleteById(String id) {
    return postgresClient.delete(tableName, id);
  }

  public String getFullTableName(String tableName) {
    return convertToPsqlStandard(postgresClient.getTenantId()) + "." + tableName;
  }

  public String getFullTableName() {
    return convertToPsqlStandard(postgresClient.getTenantId()) + "." + this.tableName;
  }

  public CQLWrapper getFetchCqlWrapper(String genericCql, int offset, int limit, String totalRecords,
                                       String specificCql)
    throws FieldException {
    var field = new CQL2PgJSON(tableName + ".jsonb");
    if (StringUtils.isBlank(genericCql)) {
      return new CQLWrapper(field, specificCql, limit, offset, totalRecords);
    }

    var cqlWrapper = new CQLWrapper(field, genericCql, limit, offset, totalRecords);
    var cqlWrapperForShadowLocations = new CQLWrapper(field, specificCql);
    return cqlWrapper.addWrapper(cqlWrapperForShadowLocations);
  }
}
