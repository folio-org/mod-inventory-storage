package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.resource.OaiPmhView.GetOaiPmhViewInstancesResponse.respond400WithTextPlain;
import static org.folio.rest.jaxrs.resource.OaiPmhView.GetOaiPmhViewInstancesResponse.respond500WithTextPlain;

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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.support.PostgresClientFactory;

public abstract class AbstractInstanceRecordsApi {

  protected static final Logger log = LogManager.getLogger();

  protected void fetchRecordsByQuery(String sql, Supplier<Tuple> paramsSupplier, RoutingContext routingContext,
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

  protected Tuple createPostgresParams(String startDate, String endDate, boolean deletedRecordSupport,
                                       boolean skipSuppressedFromDiscoveryRecords) {

    return createPostgresParams(startDate, endDate, deletedRecordSupport, skipSuppressedFromDiscoveryRecords,
      empty -> { });
  }

  protected Tuple createPostgresParams(String startDate, String endDate, boolean deletedRecordSupport,
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

  protected Tuple createPostgresParams(UUID[] instancesIds, boolean skipSuppressedFromDiscoveryRecords) {

    Tuple tuple = new ArrayTuple(2);
    try {
      tuple.addArrayOfUUID(Optional.ofNullable(instancesIds).orElse(new UUID[0]));
      tuple.addBoolean(skipSuppressedFromDiscoveryRecords);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }

    return tuple;
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
