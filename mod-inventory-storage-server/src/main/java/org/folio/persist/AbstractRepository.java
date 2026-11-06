package org.folio.persist;

import static io.vertx.core.Promise.promise;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.persist.helper.EntityAccessor;
import org.folio.persist.helper.EntityAccessors;
import org.folio.persist.helper.OptimisticLocking;
import org.folio.persist.helper.PgViolationMapper;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.PostgresClientStreamResult;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.folio.util.UuidUtil;

public abstract class AbstractRepository<T> {

  private static final String NOT_FOUND = "Not found";

  protected final Context context;
  protected final String tableName;
  protected final Class<T> recordType;
  protected final CQL2PgJSON cql2PgJson;

  private final EntityAccessor<T> accessor;

  protected AbstractRepository(Context context, String tableName, Class<T> recordType) {
    this.context = context;
    this.tableName = tableName;
    this.recordType = recordType;
    this.accessor = EntityAccessors.of(recordType);
    try {
      this.cql2PgJson = new CQL2PgJSON(tableName + ".jsonb");
    } catch (FieldException e) {
      throw new IllegalArgumentException("Unexpected error creating CQL2PgJSON", e);
    }
  }

  // ---- Reading ----

  /**
   * Gets the record by id, fails with {@link NotFoundException} when there is no such record.
   */
  public Future<T> findById(String id, String tenantId) {
    return getById(id, tenantId)
      .compose(entity -> entity == null
                         ? Future.failedFuture(new NotFoundException(NOT_FOUND))
                         : Future.succeededFuture(entity));
  }

  /**
   * Gets the record by id, null when there is no such record, see {@link #findById} to fail instead.
   */
  public Future<T> getById(String id, String tenantId) {
    if (!UuidUtil.isUuid(id)) {
      return Future.failedFuture(PgViolationMapper.invalidUuid(tableName, id));
    }
    return postgresClient(tenantId).getById(tableName, id, recordType);
  }

  /**
   * Gets the records by ids, the result is keyed by id.
   */
  public Future<Map<String, T>> getByIds(Collection<String> ids, String tenantId) {
    for (String id : ids) {
      if (!UuidUtil.isUuid(id)) {
        return Future.failedFuture(PgViolationMapper.invalidUuid(tableName, id));
      }
    }

    final Promise<Map<String, T>> promise = promise();

    postgresClient(tenantId).getById(tableName, new JsonArray(new ArrayList<>(ids)), recordType, promise::handle);

    return promise.future();
  }

  public <V> Future<Map<String, T>> getByIds(Collection<V> records, Function<V, String> mapper, String tenantId) {
    final Set<String> ids = records.stream()
      .map(mapper)
      .collect(Collectors.toSet());

    return getByIds(ids, tenantId);
  }

  /**
   * Gets the records matching the criterion.
   */
  public Future<List<T>> get(Criterion criterion, String tenantId) {
    return postgresClient(tenantId).get(tableName, recordType, criterion, false)
      .map(Results::getResults);
  }

  /**
   * Gets the records matching the criterion using the connection, for example inside a transaction.
   */
  public Future<List<T>> get(Conn connection, Criterion criterion) {
    return connection.get(tableName, recordType, criterion, false)
      .map(Results::getResults);
  }

  /**
   * Returns whether a record with the id exists.
   */
  public Future<Boolean> exists(String id, String tenantId) {
    if (!UuidUtil.isUuid(id)) {
      return Future.failedFuture(PgViolationMapper.invalidUuid(tableName, id));
    }
    return postgresClient(tenantId)
      .execute("select 1 from " + getFullTableName(tenantId) + " where id = $1 limit 1", Tuple.of(id))
      .map(ar -> ar != null && ar.rowCount() == 1);
  }

  /**
   * Finds records by CQL. A failing query is reported as {@link BadRequestException}.
   */
  public Future<Results<T>> query(String cql, int offset, int limit, String totalRecords, String tenantId) {
    try {
      var cqlWrapper = new CQLWrapper(cql2PgJson, cql, limit, offset, totalRecords);
      return postgresClient(tenantId).get(tableName, recordType, cqlWrapper, cqlWrapper.hasReturnCount())
        .recover(e -> Future.failedFuture(badRequest(e)));
    } catch (Exception e) {
      return Future.failedFuture(badRequest(e));
    }
  }

  /**
   * Finds records by CQL narrowed by an additional CQL condition that always applies, for example a filter
   * the endpoint adds to the query of the client. The additional CQL may be null or blank, then it is ignored.
   * The total number of records is always counted. A failing query is reported as {@link BadRequestException}.
   */
  public Future<Results<T>> query(String cql, String additionalCql, int offset, int limit, String totalRecords,
                                  String tenantId) {
    try {
      var cqlWrapper = cqlWrapper(cql, additionalCql, offset, limit, totalRecords);
      return postgresClient(tenantId).get(tableName, recordType, cqlWrapper, true)
        .recover(e -> Future.failedFuture(badRequest(e)));
    } catch (Exception e) {
      return Future.failedFuture(badRequest(e));
    }
  }

  /**
   * Streams the records matching the CQL query. A failing query is reported as {@link BadRequestException}.
   */
  public Future<PostgresClientStreamResult<T>> stream(String cql, int offset, int limit, String tenantId) {
    return stream(recordType, cql, offset, limit, tenantId);
  }

  /**
   * Same as {@link #stream(String, int, int, String)} but maps the records to another type, for example to a view
   * with additional properties.
   */
  public <V> Future<PostgresClientStreamResult<V>> stream(Class<V> type, String cql, int offset, int limit,
                                                          String tenantId) {
    try {
      return stream(type, new CQLWrapper(cql2PgJson, cql, limit, offset), tenantId);
    } catch (Exception e) {
      return Future.failedFuture(badRequest(e));
    }
  }

  public Future<PostgresClientStreamResult<T>> stream(CQLWrapper cqlWrapper, String tenantId) {
    return stream(recordType, cqlWrapper, tenantId);
  }

  public <V> Future<PostgresClientStreamResult<V>> stream(Class<V> type, CQLWrapper cqlWrapper, String tenantId) {
    return postgresClient(tenantId)
      .streamGet(tableName, type, "jsonb", cqlWrapper, true, null, null, 0)
      .recover(e -> {
        var message = PgExceptionUtil.badRequestMessage(e);
        return Future.failedFuture(new BadRequestException(
          (message != null ? message : e.getMessage()) + ": " + cqlWrapper.getQuery()));
      });
  }

  /**
   * Runs the query in a read only transaction and hands the stream of rows to the handler.
   */
  protected Future<Void> selectStream(String sql, Tuple params, Handler<RowStream<Row>> rowStreamHandler,
                                   String tenantId) {
    return postgresClient(tenantId)
      .withReadTrans(conn -> conn.selectStream(sql, params, rowStreamHandler));
  }

  // ---- Creating ----

  /**
   * Inserts the record, generating its id if absent, and returns the stored record.
   * Unique and foreign key violations are reported as {@link org.folio.rest.exceptions.ValidationException}.
   */
  public Future<T> create(T entity, String tenantId) {
    var id = accessor.id(entity);
    if (id == null) {
      id = UUID.randomUUID().toString();
      accessor.setId(entity, id);
    }
    if (!UuidUtil.isUuid(id)) {
      return Future.failedFuture(PgViolationMapper.invalidUuid(tableName, id));
    }
    var recordId = id;
    return postgresClient(tenantId).saveAndReturnUpdatedEntity(tableName, recordId, entity)
      .recover(e -> Future.failedFuture(PgViolationMapper.map(tableName, recordId, e)));
  }

  /**
   * Inserts the record with the id and returns the id, errors are passed on unchanged.
   */
  public Future<String> save(String id, T entity, String tenantId) {
    return postgresClient(tenantId).save(tableName, id, entity);
  }

  /**
   * Inserts the records. Unique and foreign key violations are reported as
   * {@link org.folio.rest.exceptions.ValidationException}.
   */
  public Future<Void> saveBatch(List<T> entities, String tenantId) {
    return postgresClient(tenantId).saveBatch(tableName, entities)
      .recover(e -> Future.failedFuture(PgViolationMapper.map(tableName, "", e)))
      .mapEmpty();
  }

  // ---- Updating ----

  /**
   * Replaces the record with the given id, fails with {@link NotFoundException} when there is no such record.
   */
  public Future<Void> replace(String id, T entity, String tenantId) {
    if (!UuidUtil.isUuid(id)) {
      return Future.failedFuture(PgViolationMapper.invalidUuid(tableName, id));
    }
    accessor.setId(entity, id);
    accessor.unsetVersionIfMinusOne(entity);
    return postgresClient(tenantId).update(tableName, entity, id)
      .recover(e -> Future.failedFuture(PgViolationMapper.map(tableName, id, e)))
      .compose(rows -> rows.rowCount() == 0
                       ? Future.failedFuture(new NotFoundException(NOT_FOUND))
                       : Future.succeededFuture());
  }

  /**
   * Updates the record, succeeds also when there is no such record, see {@link #replace}.
   */
  public Future<Void> update(String id, T entity, Conn connection) {
    return connection.update(tableName, entity, id).mapEmpty();
  }

  /**
   * Updates the record, succeeds also when there is no such record, see {@link #replace}.
   */
  public Future<Void> update(String id, T entity, String tenantId) {
    return postgresClient(tenantId).update(tableName, entity, id).mapEmpty();
  }

  /**
   * Updates the records using the connection.
   */
  public Future<Void> updateBatch(List<T> records, Conn connection) {
    return connection.updateBatch(tableName, records).mapEmpty();
  }

  /**
   * Locks the record, applies the builder to it and stores the result, all in one transaction.
   * Fails with {@link NotFoundException} when there is no such record.
   */
  public Future<T> fetchAndUpdate(String id, UnaryOperator<T> builder, String tenantId) {
    if (!UuidUtil.isUuid(id)) {
      return Future.failedFuture(PgViolationMapper.invalidUuid(tableName, id));
    }
    return inTransaction(tenantId, conn -> conn.getByIdForUpdate(tableName, id, recordType)
      .compose(entity -> entity == null
                         ? Future.failedFuture(new NotFoundException(NOT_FOUND))
                         : Future.succeededFuture(entity))
      .map(builder)
      .compose(response -> conn.update(tableName, response, id).map(response)));
  }

  /**
   * Inserts the record or replaces it when one with the id exists.
   */
  public Future<Void> upsert(String id, T entity, String tenantId) {
    return postgresClient(tenantId).upsert(tableName, id, entity).mapEmpty();
  }

  /**
   * Inserts or replaces the records using the connection.
   */
  public Future<Void> upsertBatch(List<T> records, Conn connection) {
    return connection.upsertBatch(tableName, records).mapEmpty();
  }

  /**
   * Inserts or replaces the records. Unique and foreign key violations are reported as
   * {@link org.folio.rest.exceptions.ValidationException}.
   */
  public Future<Void> upsertBatch(List<T> entities, String tenantId) {
    return postgresClient(tenantId).upsertBatch(tableName, entities)
      .recover(e -> Future.failedFuture(PgViolationMapper.map(tableName, "", e)))
      .mapEmpty();
  }

  // ---- Deleting ----

  /**
   * Removes the record with the given id, fails with {@link NotFoundException} when there is no such record.
   */
  public Future<Void> remove(String id, String tenantId) {
    if (!UuidUtil.isUuid(id)) {
      return Future.failedFuture(PgViolationMapper.invalidUuid(tableName, id));
    }
    return deleteById(id, tenantId)
      .recover(e -> Future.failedFuture(PgViolationMapper.map(tableName, id, e)))
      .compose(deleted -> Boolean.FALSE.equals(deleted)
                          ? Future.failedFuture(new NotFoundException(NOT_FOUND))
                          : Future.succeededFuture());
  }

  /**
   * Removes the record with the id, succeeds also when there is no such record.
   */
  public Future<Void> removeIfExists(String id, String tenantId) {
    return deleteById(id, tenantId).mapEmpty();
  }

  /**
   * Removes the records matching the CQL query. A blank or failing query is reported as {@link BadRequestException}.
   */
  public Future<Void> removeByQuery(String cql, String tenantId) {
    if (StringUtils.isBlank(cql)) {
      return Future.failedFuture(new BadRequestException("query with CQL expression is required"));
    }
    try {
      var cqlWrapper = new CQLWrapper(cql2PgJson, cql, -1, -1);
      return postgresClient(tenantId).delete(tableName, cqlWrapper)
        .recover(e -> Future.failedFuture(badRequest(e)))
        .mapEmpty();
    } catch (Exception e) {
      return Future.failedFuture(badRequest(e));
    }
  }

  /**
   * Deletes the record, returns whether a record was deleted.
   */
  public Future<Boolean> deleteById(String id, String tenantId) {
    return postgresClient(tenantId).delete(tableName, id).map(rowSet -> rowSet.rowCount() > 0);
  }

  /**
   * Deletes all records of the table.
   */
  public Future<Void> deleteAll(String tenantId) {
    return postgresClient(tenantId).delete(tableName, new Criterion()).mapEmpty();
  }

  // ---- Optimistic locking ----

  /**
   * Prepares the version of the records of a batch, see {@link OptimisticLocking#apply}.
   */
  public void applyOptimisticLocking(List<T> records, boolean optimisticLocking) {
    OptimisticLocking.apply(accessor, records, optimisticLocking);
  }

  // ---- Transactions ----

  /**
   * Runs the function in a transaction of the tenant, the transaction is committed when the returned future succeeds.
   */
  public <R> Future<R> inTransaction(String tenantId, Function<Conn, Future<R>> function) {
    return postgresClient(tenantId).withTrans(function);
  }

  // ---- Names ----

  /**
   * Returns the database schema of the tenant.
   */
  public String getSchemaName(String tenantId) {
    return convertToPsqlStandard(tenantId);
  }

  /**
   * Returns the schema qualified name of the table.
   */
  public String getFullTableName(String tableName, String tenantId) {
    return convertToPsqlStandard(tenantId) + "." + tableName;
  }

  /**
   * Returns the schema qualified name of the table of this repository.
   */
  public String getFullTableName(String tenantId) {
    return convertToPsqlStandard(tenantId) + "." + this.tableName;
  }

  /**
   * Returns the database client of the given tenant.
   */
  protected PostgresClient postgresClient(String tenantId) {
    if (PostgresClient.DEFAULT_SCHEMA.equals(tenantId)) {
      return PostgresClient.getInstance(context.owner());
    }
    return PostgresClient.getInstance(context.owner(), tenantId);
  }

  /**
   * Wrapper for the CQL without paging, for statements that apply to all matching records.
   */
  protected CQLWrapper cqlWrapper(String cql) {
    return new CQLWrapper(cql2PgJson, cql, -1, -1);
  }

  private CQLWrapper cqlWrapper(String cql, String additionalCql, int offset, int limit, String totalRecords) {
    if (StringUtils.isBlank(additionalCql)) {
      return new CQLWrapper(cql2PgJson, cql, limit, offset, totalRecords);
    }
    if (StringUtils.isBlank(cql)) {
      return new CQLWrapper(cql2PgJson, additionalCql, limit, offset, totalRecords);
    }
    return new CQLWrapper(cql2PgJson, cql, limit, offset, totalRecords)
      .addWrapper(new CQLWrapper(cql2PgJson, additionalCql));
  }

  private static Throwable badRequest(Throwable e) {
    var message = PgExceptionUtil.badRequestMessage(e);
    return new BadRequestException(message != null ? message : e.getMessage());
  }
}
