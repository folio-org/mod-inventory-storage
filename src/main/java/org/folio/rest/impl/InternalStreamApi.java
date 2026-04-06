package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.resource.InternalInstanceStorage.GetInternalInstanceStorageInstancesStreamResponse.respond400WithTextPlain;
import static org.folio.rest.jaxrs.resource.InternalInstanceStorage.GetInternalInstanceStorageInstancesStreamResponse.respond500WithTextPlain;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.RowStream;
import io.vertx.sqlclient.Tuple;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.resource.InternalInstanceStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;

public class InternalStreamApi implements InternalInstanceStorage {

  private static final Logger log = LogManager.getLogger();

  private static final String HEADER_NEXT_CURSOR = "X-Next-Cursor";
  private static final String HEADER_HAS_MORE = "X-Has-More";
  private static final String CONTENT_TYPE_NDJSON = "application/x-ndjson";
  private static final int MAX_PAGE_LIMIT = 200_000;
  private static final String REPEATABLE_READ_SQL = "SET TRANSACTION ISOLATION LEVEL REPEATABLE READ";

  private static final ResourceQueries INSTANCE_QUERIES = new ResourceQueries(
    "SELECT id::text FROM instance ORDER BY id LIMIT ($1 + 1)",
    "SELECT id::text FROM instance WHERE id > $1 ORDER BY id LIMIT ($2 + 1)",
    "SELECT jsonb::text FROM instance ORDER BY id LIMIT $1",
    "SELECT jsonb::text FROM instance WHERE id > $1 ORDER BY id LIMIT $2"
  );

  private static final ResourceQueries HOLDINGS_QUERIES = new ResourceQueries(
    "SELECT id::text FROM holdings_record ORDER BY id LIMIT ($1 + 1)",
    "SELECT id::text FROM holdings_record WHERE id > $1 ORDER BY id LIMIT ($2 + 1)",
    "SELECT jsonb::text FROM holdings_record ORDER BY id LIMIT $1",
    "SELECT jsonb::text FROM holdings_record WHERE id > $1 ORDER BY id LIMIT $2"
  );

  private static final ResourceQueries ITEM_QUERIES = new ResourceQueries(
    "SELECT i.id::text FROM item i"
      + " INNER JOIN holdings_record hr ON i.holdingsrecordid = hr.id"
      + " ORDER BY i.id LIMIT ($1 + 1)",
    "SELECT i.id::text FROM item i"
      + " INNER JOIN holdings_record hr ON i.holdingsrecordid = hr.id"
      + " WHERE i.id > $1 ORDER BY i.id LIMIT ($2 + 1)",
    "SELECT (i.jsonb || jsonb_build_object('instanceId', hr.instanceid::text))::text"
      + " FROM item i"
      + " INNER JOIN holdings_record hr ON i.holdingsrecordid = hr.id"
      + " ORDER BY i.id LIMIT $1",
    "SELECT (i.jsonb || jsonb_build_object('instanceId', hr.instanceid::text))::text"
      + " FROM item i"
      + " INNER JOIN holdings_record hr ON i.holdingsrecordid = hr.id"
      + " WHERE i.id > $1 ORDER BY i.id LIMIT $2"
  );

  @Override
  public void getInternalInstanceStorageInstancesStream(String cursor, int limit,
                                                        RoutingContext routingContext,
                                                        Map<String, String> okapiHeaders,
                                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                                        Context vertxContext) {
    streamResource(INSTANCE_QUERIES, cursor, limit,
      routingContext, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  public void getInternalInstanceStorageHoldingsStream(String cursor, int limit,
                                                       RoutingContext routingContext,
                                                       Map<String, String> okapiHeaders,
                                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                                       Context vertxContext) {
    streamResource(HOLDINGS_QUERIES, cursor, limit,
      routingContext, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  public void getInternalInstanceStorageItemsStream(String cursor, int limit,
                                                    RoutingContext routingContext,
                                                    Map<String, String> okapiHeaders,
                                                    Handler<AsyncResult<Response>> asyncResultHandler,
                                                    Context vertxContext) {
    streamResource(ITEM_QUERIES, cursor, limit,
      routingContext, okapiHeaders, asyncResultHandler, vertxContext);
  }

  private void streamResource(ResourceQueries queries, String cursor, int limit,
                               RoutingContext routingContext,
                               Map<String, String> okapiHeaders,
                               Handler<AsyncResult<Response>> asyncResultHandler,
                               Context vertxContext) {

    UUID cursorUuid = null;
    if (cursor != null && !cursor.isEmpty()) {
      try {
        cursorUuid = UUID.fromString(cursor);
      } catch (IllegalArgumentException e) {
        log.warn("Invalid cursor UUID: {}", cursor);
        asyncResultHandler.handle(succeededFuture(respond400WithTextPlain(
          "Invalid cursor: must be a valid UUID")));
        return;
      }
    }

    if (limit < 1 || limit > MAX_PAGE_LIMIT) {
      asyncResultHandler.handle(succeededFuture(respond400WithTextPlain(
        "Invalid limit: must be between 1 and " + MAX_PAGE_LIMIT)));
      return;
    }

    final HttpServerResponse response = routingContext.response();
    response.setChunked(true);
    response.putHeader("Content-Type", CONTENT_TYPE_NDJSON);

    PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);
    AtomicBoolean finalized = new AtomicBoolean(false);

    String boundarySql;
    Tuple boundaryParams;
    String streamSql;
    Tuple streamParams;

    if (cursorUuid == null) {
      boundarySql = queries.boundaryInitial();
      boundaryParams = Tuple.of(limit);
      streamSql = queries.streamInitial();
      streamParams = Tuple.of(limit);
    } else {
      boundarySql = queries.boundaryCursor();
      boundaryParams = Tuple.of(cursorUuid, limit);
      streamSql = queries.streamCursor();
      streamParams = Tuple.of(cursorUuid, limit);
    }

    postgresClient.startTx(tx -> {
      if (tx.failed()) {
        log.error("Failed to start transaction", tx.cause());
        asyncResultHandler.handle(succeededFuture(
          respond500WithTextPlain("Internal server error")));
        return;
      }

      postgresClient.execute(tx, REPEATABLE_READ_SQL, isolationAr -> {
        if (isolationAr.failed()) {
          log.error("Failed to set transaction isolation level", isolationAr.cause());
          finalizeOnce(finalized, () -> rollbackAndRespond(postgresClient, tx, response,
            isolationAr.cause(), asyncResultHandler));
          return;
        }

        // Execute boundary query to determine pagination headers
        postgresClient.select(tx, boundarySql, boundaryParams, boundaryAr -> {
          if (boundaryAr.failed()) {
            log.error("Boundary query failed", boundaryAr.cause());
            finalizeOnce(finalized, () -> rollbackAndRespond(postgresClient, tx, response,
              boundaryAr.cause(), asyncResultHandler));
            return;
          }

          RowSet<Row> boundaryRows = boundaryAr.result();
          int count = 0;
          String cursorId = null;
          for (Row row : boundaryRows) {
            count++;
            if (count == limit) {
              cursorId = row.getString(0);
            }
          }

          if (count > limit) {
            response.putHeader(HEADER_HAS_MORE, "true");
            response.putHeader(HEADER_NEXT_CURSOR, cursorId);
          } else {
            response.putHeader(HEADER_HAS_MORE, "false");
          }

          // Execute stream query
          postgresClient.selectStream(tx, streamSql, streamParams, streamAr -> {
            if (streamAr.failed()) {
              log.error("Stream query failed", streamAr.cause());
              finalizeOnce(finalized, () -> rollbackAndRespond(postgresClient, tx, response,
                streamAr.cause(), asyncResultHandler));
              return;
            }

            RowStream<Row> rowStream = streamAr.result();

            // Client disconnect cleanup
            response.closeHandler(v -> {
              log.info("Client disconnected, cleaning up stream");
              rowStream.close();
              finalizeOnce(finalized, () -> postgresClient.rollbackTx(tx, h -> {
                if (h.failed()) {
                  log.error("Failed to rollback after client disconnect", h.cause());
                }
              }));
            });

            rowStream
              .exceptionHandler(e -> {
                log.error("Row stream error", e);
                rowStream.close();
                finalizeOnce(finalized, () -> {
                  postgresClient.rollbackTx(tx, h -> {
                    if (h.failed()) {
                      log.error("Failed to rollback after stream error", h.cause());
                    }
                  });
                  if (!response.headWritten()) {
                    asyncResultHandler.handle(succeededFuture(
                      respond500WithTextPlain("Internal server error")));
                  } else {
                    response.reset();
                    asyncResultHandler.handle(succeededFuture());
                  }
                });
              })
              .endHandler(end -> finalizeOnce(finalized, () ->
                postgresClient.endTx(tx, h -> {
                  if (h.failed()) {
                    log.error("Failed to commit transaction", h.cause());
                    if (!response.headWritten()) {
                      asyncResultHandler.handle(succeededFuture(
                        respond500WithTextPlain("Internal server error")));
                    } else {
                      response.reset();
                      asyncResultHandler.handle(succeededFuture());
                    }
                    return;
                  }
                  response.end();
                })
              ))
              .handler(row -> {
                String jsonText = row.getString(0);
                if (jsonText != null) {
                  response.write(jsonText + "\n");
                  if (response.writeQueueFull()) {
                    rowStream.pause();
                  }
                }
              })
            ;

            response.drainHandler(drain -> rowStream.resume());
          });
        });
      });
    });
  }

  private static void finalizeOnce(AtomicBoolean finalized, Runnable action) {
    if (finalized.compareAndSet(false, true)) {
      action.run();
    }
  }

  private static void rollbackAndRespond(PostgresClient postgresClient,
                                          AsyncResult<SQLConnection> tx,
                                          HttpServerResponse response,
                                          Throwable cause,
                                          Handler<AsyncResult<Response>> asyncResultHandler) {
    postgresClient.rollbackTx(tx, h -> {
      if (h.failed()) {
        log.error("Failed to rollback transaction", h.cause());
      }
    });
    if (!response.headWritten()) {
      asyncResultHandler.handle(succeededFuture(
        respond500WithTextPlain("Internal server error")));
    } else {
      response.reset();
      asyncResultHandler.handle(succeededFuture());
    }
  }

  record ResourceQueries(
    String boundaryInitial, String boundaryCursor,
    String streamInitial, String streamCursor
  ) { }
}
