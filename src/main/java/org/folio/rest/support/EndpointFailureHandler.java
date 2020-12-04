package org.folio.rest.support;

import java.util.function.Function;

import javax.ws.rs.core.Response;

import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.Errors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

public final class EndpointFailureHandler {
  private static final Logger log = LoggerFactory.getLogger(EndpointFailureHandler.class);

  private EndpointFailureHandler() {}

  public static void handleFailure(
      Throwable error,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Function<Errors, Response> validationHandler,
      Function<String, Response> serverErrorHandler) {

    log.warn("Error occurred", error);

    Response response;
    if (error instanceof ValidationException) {
      response = validationHandler.apply(((ValidationException) error).getErrors());
    } else {
      response = serverErrorHandler.apply(error.getMessage());
    }
    asyncResultHandler.handle(Future.succeededFuture(response));
  }

  /**
   * Use future.onError(handleFailure(asyncResultHandler, validationHandler, serverErrorHandler))
   */
  public static Handler<Throwable> handleFailure(
      Handler<AsyncResult<Response>> asyncResultHandler,
      Function<Errors, Response> validationHandler,
      Function<String, Response> serverErrorHandler) {
    return e -> handleFailure(e, asyncResultHandler, validationHandler, serverErrorHandler);
  }
}
