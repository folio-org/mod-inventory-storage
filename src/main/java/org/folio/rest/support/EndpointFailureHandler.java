package org.folio.rest.support;

import static io.vertx.core.Future.succeededFuture;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

import java.util.function.Function;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.Errors;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public final class EndpointFailureHandler {
  private static final Logger log = LogManager.getLogger();

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
    asyncResultHandler.handle(succeededFuture(response));
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

  public static Handler<Throwable> handleFailure(Handler<AsyncResult<Response>> asyncResultHandler) {
    return error -> {
      log.error("An error occurred", error);
      Response responseToReturn;

      if (error instanceof BadRequestException) {
        responseToReturn = textPlainResponse(400, error);
      } else if (error instanceof NotFoundException) {
        responseToReturn = textPlainResponse(404, error);
      } else if (error instanceof ValidationException) {
        final Errors errors = ((ValidationException) error).getErrors();
        responseToReturn = failedValidationResponse(errors);
      } else {
        responseToReturn = textPlainResponse(500, error);
      }

      asyncResultHandler.handle(succeededFuture(responseToReturn));
    };
  }

  private static Response textPlainResponse(int status, Throwable error) {
    return Response.status(status).header(CONTENT_TYPE, "text/plain")
      .entity(error.getMessage()).build();
  }

  private static Response failedValidationResponse(Object jsonEntity) {
    return Response.status(422).header(CONTENT_TYPE, "application/json")
      .entity(jsonEntity).build();
  }
}
