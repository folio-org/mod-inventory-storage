package org.folio.rest.support;

import static io.vertx.core.Future.succeededFuture;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.pgclient.PgException;
import java.util.function.Function;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.cql.CQLQueryValidationException;
import org.folio.rest.tools.client.exceptions.ResponseException;

public final class EndpointFailureHandler {
  private static final Logger log = LogManager.getLogger();

  private EndpointFailureHandler() { }

  public static void handleFailure(
    Throwable error,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Function<Errors, Response> validationHandler,
    Function<String, Response> serverErrorHandler) {

    log.warn("Error occurred", error);

    Response response;
    if (error instanceof ValidationException) {
      response = validationHandler.apply(((ValidationException) error).getErrors());
    } else if (error instanceof PgException) {
      response = serverErrorHandler.apply(error.getMessage());
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
    return error -> asyncResultHandler.handle(succeededFuture(failureResponse(error)));
  }

  public static Response failureResponse(Throwable error) {
    log.warn("An error occurred", error);

    if (error instanceof BadRequestException || error instanceof CQLQueryValidationException) {
      return textPlainResponse(400, error);
    } else if (error instanceof NotFoundException) {
      return textPlainResponse(404, error);
    } else if (error instanceof ValidationException) {
      final Errors errors = ((ValidationException) error).getErrors();
      return failedValidationResponse(errors);
    } else if (PgExceptionUtil.isVersionConflict(error)) {
      return textPlainResponse(409, error);
    } else if (error instanceof ResponseException) {
      return ((ResponseException) error).getResponse();
    }
    String message = PgExceptionUtil.badRequestMessage(error);
    if (message != null) {
      return textPlainResponse(400, message);
    }
    return textPlainResponse(500, error);
  }

  private static Response textPlainResponse(int status, Throwable error) {
    return textPlainResponse(status, error.getMessage());
  }

  private static Response textPlainResponse(int status, String message) {
    return Response.status(status).header(CONTENT_TYPE, "text/plain")
      .entity(message).build();
  }

  private static Response failedValidationResponse(Object jsonEntity) {
    return Response.status(422).header(CONTENT_TYPE, "application/json")
      .entity(jsonEntity).build();
  }
}
