package org.folio.rest.support;

import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import javax.ws.rs.core.Response;

public final class EndpointHandler {
  private EndpointHandler() { }

  /**
   * On success pass the result to asyncResultHandler. On failure use
   * {@link EndpointFailureHandler} to create an error Response for the Throwable
   * and pass it to asyncResultHandler.
   */
  public static Handler<AsyncResult<Response>> handle(Handler<AsyncResult<Response>> asyncResultHandler) {
    return result -> {
      if (result.succeeded()) {
        asyncResultHandler.handle(succeededFuture(result.result()));
      } else {
        EndpointFailureHandler.handleFailure(asyncResultHandler).handle(result.cause());
      }
    };
  }
}
