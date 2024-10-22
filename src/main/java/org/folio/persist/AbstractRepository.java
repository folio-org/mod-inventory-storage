package org.folio.persist;

import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.Promise.promise;
import static java.lang.String.format;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.core.MediaType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.HttpStatus;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.rest.jaxrs.model.Diagnostic;
import org.folio.rest.jaxrs.model.ResultInfo;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.persist.PostgresClientStreamResult;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.facets.FacetField;
import org.folio.rest.persist.facets.FacetManager;
import org.folio.rest.persist.interfaces.Results;
import org.folio.utils.ObjectConverterUtils;

public abstract class AbstractRepository<T> {

  private static final Logger logger = LogManager.getLogger();
  private static final ObjectMapper OBJECT_MAPPER = ObjectMapperTool.getMapper();
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

  public Future<RowSet<Row>> update(AsyncResult<SQLConnection> connection, String id, T entity) {
    final Promise<RowSet<Row>> promise = promise();

    postgresClient.update(connection, tableName, entity, "jsonb",
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

  public <S, R> void streamGet(String table, Class<S> clazz,
                               String cql, int offset, int limit, List<String> facets,
                               String element, int queryTimeout, RoutingContext routingContext,
                               Class<R> targetClazz) {

    var response = routingContext.response();
    try {
      var facetList = FacetManager.convertFacetStrings2FacetFields(facets, JSON_COLUMN);
      var wrapper = new CQLWrapper(new CQL2PgJSON(table + "." + JSON_COLUMN), cql, limit, offset);
      streamGetInstances(table, clazz, wrapper, facetList, element, queryTimeout, routingContext, targetClazz);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      response.setStatusCode(HttpStatus.HTTP_INTERNAL_SERVER_ERROR.toInt());
      response.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN);
      response.end(e.toString());
    }
  }

  private <S, R> void streamGetInstances(String table, Class<S> clazz,
                                         CQLWrapper filter, List<FacetField> facetList,
                                         String element, int queryTimeout,
                                         RoutingContext routingContext, Class<R> targetClazz) {

    var response = routingContext.response();
    postgresClient.streamGet(table, clazz, JSON_COLUMN, filter, true, null,
      facetList, queryTimeout, reply -> {
        if (reply.failed()) {
          handleFailure(filter, reply, response);
          return;
        }
        streamGetResult(reply.result(), element, response, targetClazz);
      });
  }

  private <S, R> void streamGetResult(PostgresClientStreamResult<S> result,
                                      String element, HttpServerResponse response,
                                      Class<R> targetClazz) {
    response.setStatusCode(HttpStatus.HTTP_OK.toInt());
    response.setChunked(true);
    response.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
    response.write("{\n");
    response.write(String.format("  \"%s\": [%n", element));
    AtomicBoolean first = new AtomicBoolean(true);
    result.exceptionHandler(res -> handleException(result, response, res));
    result.endHandler(res -> streamTrailer(response, result.resultInfo()));
    result.handler(res -> handleResult(response, targetClazz, res, first));
  }

  private <S> void handleFailure(CQLWrapper filter,
                                 AsyncResult<PostgresClientStreamResult<S>> reply,
                                 HttpServerResponse response) {
    var message = PgExceptionUtil.badRequestMessage(reply.cause());
    if (message == null) {
      message = reply.cause().getMessage();
    }
    message = String.format("%s: %s", message, filter.getQuery());
    logger.error(message, reply.cause());
    response.setStatusCode(HttpStatus.HTTP_BAD_REQUEST.toInt());
    response.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN);
    response.end(message);
  }

  private void streamTrailer(HttpServerResponse response, ResultInfo resultInfo) {
    response.write("],\n");
    if (resultInfo.getTotalRecords() != null) {
      response.write(String.format("  \"totalRecords\": %d,\n", resultInfo.getTotalRecords()));
    }
    response.end(String.format("  \"resultInfo\": %s\n}", Json.encode(resultInfo)));
  }

  private <S> void handleException(PostgresClientStreamResult<S> result, HttpServerResponse response, Throwable res) {
    var diagnostic = new Diagnostic()
      .withCode(HttpStatus.HTTP_INTERNAL_SERVER_ERROR.toString())
      .withMessage(res.getMessage());
    result.resultInfo().setDiagnostics(List.of(diagnostic));
    streamTrailer(response, result.resultInfo());
  }

  private <S, R> void handleResult(HttpServerResponse response, Class<R> targetClazz, S res, AtomicBoolean first) {
    String itemString;
    try {
      var targetObject = ObjectConverterUtils.convertObject(res, targetClazz);
      itemString = OBJECT_MAPPER.writeValueAsString(targetObject);
    } catch (JsonProcessingException ex) {
      logger.error(ex.getMessage(), ex);
      throw new IllegalArgumentException(ex.getCause());
    }
    if (first.get()) {
      first.set(false);
    } else {
      response.write(String.format(",%n"));
    }
    response.write(itemString);
  }
}
