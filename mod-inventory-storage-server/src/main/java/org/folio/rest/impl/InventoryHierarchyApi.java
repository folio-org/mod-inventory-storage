package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.resource.InventoryHierarchy.GetInventoryHierarchyUpdatedInstanceIdsResponse.respond400WithTextPlain;
import static org.folio.rest.jaxrs.resource.InventoryHierarchy.GetInventoryHierarchyUpdatedInstanceIdsResponse.respond500WithTextPlain;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.internal.ArrayTuple;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.InventoryHierarchyInstanceIds;
import org.folio.rest.jaxrs.resource.InventoryHierarchy;
import org.folio.rest.support.PostgresClientFactory;

public class InventoryHierarchyApi implements InventoryHierarchy {

  private static final Logger log = LogManager.getLogger(InventoryHierarchyApi.class);

  private static final String SQL_UPDATED_INSTANCES_IDS =
    "select * from get_updated_instance_ids_view($1,$2,$3,$4,$5,$6);";
  private static final String SQL_INSTANCES = "select * from get_items_and_holdings_view($1,$2);";
  private static final String SUPPRESSED_TRUE_FILTER = "(instance.jsonb ->> 'discoverySuppress')::bool = false";

  private static final String SQL_INITIAL_LOAD = """
    SELECT id as "instanceId",
           instance.jsonb ->> 'source' AS source,
           strToTimestamp(instance.jsonb -> 'metadata' ->> 'updatedDate') AS "updatedDate",
           (instance.jsonb ->> 'discoverySuppress')::bool AS "suppressFromDiscovery",
           false AS deleted
    FROM instance
    WHERE (CAST($1 as varchar) IS NULL OR (instance.jsonb ->> 'source')::varchar = $1)
    """;
  private static final String SQL_INITIAL_LOAD_DELETED_RECORDS_SUPPORT_PART = """
     UNION ALL
      (SELECT (jsonb #>> '{record,id}')::uuid            AS "instanceId",
            jsonb #>> '{record,source}'                 AS source,
            strToTimestamp(jsonb ->> 'createdDate')     AS "updatedDate",
            false                                       AS "suppressFromDiscovery",
            true                                        AS deleted
      FROM audit_instance
      WHERE (CAST($1 as varchar) IS NULL OR (jsonb ->> 'source')::varchar = $1))
    """;

  @Validate
  @Override
  public void getInventoryHierarchyUpdatedInstanceIds(String startDate, String endDate, boolean deletedRecordSupport,
                                                      boolean skipSuppressedFromDiscoveryRecords,
                                                      boolean onlyInstanceUpdateDate, String source,
                                                      RoutingContext routingContext, Map<String, String> okapiHeaders,
                                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                                      Context vertxContext) {
    if (StringUtils.isEmpty(startDate) && StringUtils.isEmpty(endDate)) {
      handleInitialLoad(deletedRecordSupport, skipSuppressedFromDiscoveryRecords, source,
        routingContext, okapiHeaders, asyncResultHandler, vertxContext);
    } else {
      handleUpdatedInstances(startDate, endDate, deletedRecordSupport, skipSuppressedFromDiscoveryRecords,
        onlyInstanceUpdateDate, source, routingContext, okapiHeaders, asyncResultHandler, vertxContext);
    }
  }

  @Validate
  @Override
  public void postInventoryHierarchyItemsAndHoldings(InventoryHierarchyInstanceIds entity,
                                                     RoutingContext routingContext,
                                                     Map<String, String> okapiHeaders,
                                                     Handler<AsyncResult<Response>> asyncResultHandler,
                                                     Context vertxContext) {

    UUID[] ids = entity.getInstanceIds().stream().map(UUID::fromString).toArray(UUID[]::new);

    fetchRecordsByQuery(SQL_INSTANCES,
      () -> createPostgresParams(ids, entity.getSkipSuppressedFromDiscoveryRecords()),
      routingContext, okapiHeaders, asyncResultHandler, vertxContext
    );
  }

  private void fetchRecordsByQuery(String sql, Supplier<Tuple> paramsSupplier, RoutingContext routingContext,
                                   Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    final HttpServerResponse response = getResponse(routingContext);
    try {
      Tuple params = paramsSupplier.get();
      log.debug("fetchRecordsByQuery::query params: {}", params);
      PostgresClientFactory.getInstance(vertxContext, okapiHeaders)
        .withReadTrans(conn -> conn.selectStream(sql, params,
          rowStream -> configureRowStream(rowStream, response, asyncResultHandler)))
        .onFailure(event -> respondWithError(response, event, asyncResultHandler));
    } catch (IllegalArgumentException e) {
      log.error(e);
      asyncResultHandler.handle(succeededFuture(respond400WithTextPlain(e.getMessage())));
    } catch (Exception e) {
      respondWithError(response, e, asyncResultHandler);
    }
  }

  private Tuple createPostgresParams(String startDate, String endDate, boolean deletedRecordSupport,
                                     boolean skipSuppressedFromDiscoveryRecords, Consumer<Tuple> applyExtraParams) {
    Tuple tuple = new ArrayTuple(4);

    try {
      if (StringUtils.isNotEmpty(startDate)) {
        tuple.addTemporal(OffsetDateTime.parse(startDate));
      } else {
        tuple.addValue(null);
      }
      if (StringUtils.isNotEmpty(endDate)) {
        tuple.addTemporal(OffsetDateTime.parse(endDate));
      } else {
        tuple.addValue(null);
      }

      tuple.addBoolean(deletedRecordSupport);
      tuple.addBoolean(skipSuppressedFromDiscoveryRecords);
      // Apply extra parameters
      applyExtraParams.accept(tuple);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }

    return tuple;
  }

  private Tuple createPostgresParams(UUID[] instancesIds, boolean skipSuppressedFromDiscoveryRecords) {
    Tuple tuple = new ArrayTuple(2);
    try {
      tuple.addArrayOfUUID(Optional.ofNullable(instancesIds).orElse(new UUID[0]));
      tuple.addBoolean(skipSuppressedFromDiscoveryRecords);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }

    return tuple;
  }

  private void handleInitialLoad(boolean deletedRecordSupport, boolean skipSuppressedFromDiscoveryRecords,
                                 String source, RoutingContext routingContext, Map<String, String> okapiHeaders,
                                 Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    String sql = buildInitialLoadSql(skipSuppressedFromDiscoveryRecords, deletedRecordSupport);
    Tuple tuple = new ArrayTuple(1).addValue(source);
    fetchRecordsByQuery(sql, () -> tuple, routingContext, okapiHeaders, asyncResultHandler, vertxContext);
  }

  private void handleUpdatedInstances(String startDate, String endDate, boolean deletedRecordSupport,
                                      boolean skipSuppressedFromDiscoveryRecords, boolean onlyInstanceUpdateDate,
                                      String source, RoutingContext routingContext, Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    fetchRecordsByQuery(SQL_UPDATED_INSTANCES_IDS,
      () -> createPostgresParams(startDate, endDate, deletedRecordSupport, skipSuppressedFromDiscoveryRecords,
        tuple -> {
          tuple.addBoolean(onlyInstanceUpdateDate);
          if (Objects.nonNull(source)) {
            tuple.addString(source);
          } else {
            tuple.addValue(null);
          }
        }),
      routingContext, okapiHeaders, asyncResultHandler, vertxContext);
  }

  private String buildInitialLoadSql(boolean skipSuppressedFromDiscoveryRecords, boolean deletedRecordSupport) {
    String sql = SQL_INITIAL_LOAD;
    if (skipSuppressedFromDiscoveryRecords) {
      sql += " AND " + SUPPRESSED_TRUE_FILTER;
    }
    if (deletedRecordSupport) {
      sql += SQL_INITIAL_LOAD_DELETED_RECORDS_SUPPORT_PART;
    }
    return sql;
  }

  private void configureRowStream(RowStream<Row> rowStream, HttpServerResponse response,
                                  Handler<AsyncResult<Response>> asyncResultHandler) {
    rowStream
      .exceptionHandler(e -> respondWithError(response, e, asyncResultHandler))
      .endHandler(end -> response.end())
      .handler(row -> {
        response.write(createJsonFromRow(row));
        if (response.writeQueueFull()) {
          rowStream.pause();
        }
      });
    response.drainHandler(drain -> rowStream.resume());
  }

  /**
   * Return a 500 response about Throwable t via the handler,
   * but if dataResponse's head has already been written
   * close the dataResponse TCP connection to signal the error and return null via the handler.
   */
  private static void respondWithError(HttpServerResponse dataResponse, Throwable t,
                                       Handler<AsyncResult<Response>> asyncResultHandler) {
    log.error(t);
    if (dataResponse.headWritten()) {
      log.error("HTTP head has already been written, closing TCP connection to signal error");
      dataResponse.reset();
      asyncResultHandler.handle(succeededFuture());
      return;
    }
    asyncResultHandler.handle(succeededFuture(respond500WithTextPlain(t.getMessage())));
  }

  private static String createJsonFromRow(Row row) {
    if (row == null) {
      return "";
    }
    JsonObject json = new JsonObject();
    for (int i = 0; i < row.size(); i++) {
      json.put(row.getColumnName(i), convertRowValue(row.getValue(i)));
    }
    return json.toString();
  }

  private static Object convertRowValue(Object value) {
    if (value == null) {
      return "";
    }
    return value instanceof JsonObject
           || value instanceof JsonArray ? value : value.toString();
  }

  private HttpServerResponse getResponse(RoutingContext routingContext) {
    final HttpServerResponse response = routingContext.response();
    response.setChunked(true);
    response.putHeader("Content-Type", "application/json");
    return response;
  }
}
