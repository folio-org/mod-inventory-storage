package org.folio.rest.impl;

import static io.vertx.core.http.HttpHeaders.CONNECTION;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static org.folio.rest.persist.PgUtil.streamGet;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.rxjava3.FlowableHelper;
import io.vertx.rxjava3.WriteStreamSubscriber;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.IdsRequest;
import org.folio.rest.jaxrs.model.InventoryViewInstance;
import org.folio.rest.jaxrs.resource.InventoryViewInstances;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.EndpointFailureHandler;
import org.folio.rest.tools.utils.TenantTool;

public class InventoryViewApi implements InventoryViewInstances {

  protected static final Logger log = LogManager.getLogger();

  @Validate
  @Override
  public void getInventoryViewInstances(String totalRecords, int offset, int limit, String query,
                                        RoutingContext routingContext, Map<String, String> okapiHeaders,
                                        Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    streamGet("instance_holdings_item_view", InventoryViewInstance.class, query,
      offset, limit, null, "instances", routingContext, okapiHeaders, vertxContext);
  }

  @Validate
  @Override
  public void postInventoryViewInstances(IdsRequest entity, RoutingContext routingContext,
                                         Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                         Context vertxContext) {
    if (entity.getIds() == null) {
      asyncResultHandler.handle(
        Future.succeededFuture(EndpointFailureHandler
          .failureResponse(new RuntimeException("no id(s) presents"))));
    }
    if (entity.getIds().size() > 30_000) {
      asyncResultHandler.handle(
        Future.succeededFuture(EndpointFailureHandler
          .failureResponse(new RuntimeException("Over the limit of the identifiers present"))));
    }
    log.info("postInventoryViewInstances:: {} id(s)", entity.getIds().size());

    //region create subscriber with the response object so that objects can be piped to it
    HttpServerResponse response = prepareStreamResponse(routingContext);
    WriteStreamSubscriber<Buffer> responseSubscriber = io.vertx.rxjava3.RxHelper.toSubscriber(response);
    responseSubscriber.onError(throwable -> {
      if (!response.headWritten() && response.closed()) {
        response.setStatusCode(500).end("oops");
      } else {
        log.error(throwable);
      }
    });
    responseSubscriber.onWriteStreamEnd(() -> {
      log.info("write stream complete");
    });
    //endregion

    String tenantId = TenantTool.tenantId(okapiHeaders);
    PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);
    postgresClient.withTrans(conn ->
        conn.execute("create temp table if not exists id_temp(id uuid) on commit delete rows;")
          .compose(ar ->
            conn
              .execute("insert into id_temp values ($1);",
                entity.getIds().stream().map(Tuple::of).toList()))
          .compose(ar ->
            conn.selectStream(
              String.format("select id, jsonb from %s_%s.instance_holdings_item_view "
                  + "where id in (select id from id_temp);",
                tenantId, "mod_inventory_storage"),
              Tuple.tuple(),
              rowStream -> {
                FlowableHelper.toFlowable(rowStream)
                  .map(this::convertRow)
                  .subscribe(responseSubscriber);
              }
            )
          )
          .onFailure(th -> {
            log.error(th.getMessage(), th);
            asyncResultHandler.handle(Future.succeededFuture(EndpointFailureHandler.failureResponse(th)));
          })
    );

  }

  private Buffer convertRow(Row row) {
    // appended newline to conform to ndjson
    return row.getJsonObject(1).toBuffer().appendString("\r\n");
  }

  private HttpServerResponse prepareStreamResponse(RoutingContext routingContext) {
    return routingContext.response()
      .setStatusCode(200)
      .setChunked(true)
      .putHeader(CONTENT_TYPE, "application/x-ndjson")
      .putHeader(CONNECTION, "keep-alive");
  }
}
