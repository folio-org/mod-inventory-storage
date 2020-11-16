package org.folio.rest.support;

import java.util.function.Function;

import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.Errors;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

public final class EndpointFailureHandler {
  private static final Logger log = LogManager.getLogger();

  private EndpointFailureHandler() {}

  public static <T> Function<Throwable, T> handleFailure(
    Handler<AsyncResult<Response>> asyncResultHandler,
    Function<Errors, Response> validationHandler,
    Function<String, Response> serverErrorHandler) {

    return error -> {
      log.warn("Error occurred", error);

      if (error instanceof ValidationException) {
        final ValidationException validationError = (ValidationException) error;

        asyncResultHandler.handle(Future.succeededFuture(
          validationHandler.apply(validationError.getErrors())));
      } else {
        asyncResultHandler.handle(Future.succeededFuture(
          serverErrorHandler.apply(error.getMessage())));
      }

      return null;
    };
  }
}
