package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.resource.InventoryHierarchy.GetInventoryHierarchyUpdatedInstanceIdsResponse.respond400WithTextPlain;
import static org.folio.rest.jaxrs.resource.InventoryHierarchy.GetInventoryHierarchyUpdatedInstanceIdsResponse.respond500WithTextPlain;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import java.util.Map;
import java.util.function.Function;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.entity.UpdatedInstanceIdsParams;
import org.folio.rest.annotations.Validate;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.InventoryHierarchyInstanceIds;
import org.folio.rest.jaxrs.resource.InventoryHierarchy;
import org.folio.services.hierarchy.InventoryHierarchyService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class InventoryHierarchyApi implements InventoryHierarchy {

  private static final Logger log = LogManager.getLogger(InventoryHierarchyApi.class);

  @Autowired
  private InventoryHierarchyService hierarchyService;

  public InventoryHierarchyApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void getInventoryHierarchyUpdatedInstanceIds(String startDate, String endDate, boolean deletedRecordSupport,
                                                      boolean skipSuppressedFromDiscoveryRecords,
                                                      boolean onlyInstanceUpdateDate, String source,
                                                      RoutingContext routingContext, Map<String, String> headers,
                                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                                      Context vertxContext) {
    var params = new UpdatedInstanceIdsParams(startDate, endDate, deletedRecordSupport,
      skipSuppressedFromDiscoveryRecords, onlyInstanceUpdateDate, source);

    stream(routingContext, asyncResultHandler, rowStreamHandler ->
      hierarchyService.streamUpdatedInstanceIds(params, rowStreamHandler, headers));
  }

  @Validate
  @Override
  public void postInventoryHierarchyItemsAndHoldings(InventoryHierarchyInstanceIds entity,
                                                     RoutingContext routingContext,
                                                     Map<String, String> headers,
                                                     Handler<AsyncResult<Response>> asyncResultHandler,
                                                     Context vertxContext) {
    stream(routingContext, asyncResultHandler, rowStreamHandler ->
      hierarchyService.streamItemsAndHoldings(entity.getInstanceIds(), entity.getSkipSuppressedFromDiscoveryRecords(),
        rowStreamHandler, headers));
  }

  private void stream(RoutingContext routingContext, Handler<AsyncResult<Response>> asyncResultHandler,
                      Function<Handler<RowStream<Row>>, Future<Void>> streamer) {
    final HttpServerResponse response = getResponse(routingContext);
    try {
      streamer.apply(rowStream -> configureRowStream(rowStream, response, asyncResultHandler))
        .onFailure(failure -> respondWithFailure(response, failure, asyncResultHandler));
    } catch (Exception e) {
      respondWithFailure(response, e, asyncResultHandler);
    }
  }

  private void respondWithFailure(HttpServerResponse response, Throwable failure,
                                  Handler<AsyncResult<Response>> asyncResultHandler) {
    if (failure instanceof BadRequestException) {
      log.error(failure);
      asyncResultHandler.handle(succeededFuture(respond400WithTextPlain(failure.getMessage())));
    } else {
      respondWithError(response, failure, asyncResultHandler);
    }
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
