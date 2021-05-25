package org.folio.rest.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import static org.folio.rest.jaxrs.resource.OaiPmhView.GetOaiPmhViewInstancesResponse.respond500WithTextPlain;
import static io.vertx.core.Future.succeededFuture;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import javax.ws.rs.core.Response;

public class ConnectionErrorHandler {
  protected static final Logger log = LogManager.getLogger();
  protected final PostgresClient client;

  public ConnectionErrorHandler(PostgresClient Pgclient) {
    this.client = Pgclient;
  }

  public void writeErrorAndCloseConn(Throwable t, 
      AsyncResult<SQLConnection> conn, HttpServerResponse dataResponse, Handler<AsyncResult<Response>> asyncResultHandler) {
    respondWithError(dataResponse, t, asyncResultHandler);
    client.endTx(conn, h -> {
      if (h.failed()) {
        log.error("Unable to close database connection: " + h.cause().getMessage());
      }
    });
  }

  private static void respondWithError(HttpServerResponse dataResponse, Throwable t, Handler<AsyncResult<Response>> asyncResultHandler) {
    log.error(t);
    if (dataResponse.headWritten()) {
      log.error("HTTP head has already been written, closing TCP connection to signal error");
      dataResponse.close();
      asyncResultHandler.handle(succeededFuture());
      return;
    }
    asyncResultHandler.handle(succeededFuture(respond500WithTextPlain(t.getMessage())));
  }
}
