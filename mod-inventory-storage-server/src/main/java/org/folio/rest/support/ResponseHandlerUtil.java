package org.folio.rest.support;

import static io.vertx.core.Future.succeededFuture;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.exceptions.RequestEntityTooLargeException;
import org.folio.rest.exceptions.SettingsValidationException;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.cql.CQLQueryValidationException;
import org.folio.rest.tools.client.exceptions.ResponseException;

public final class ResponseHandlerUtil {

  private static final Logger log = LogManager.getLogger(ResponseHandlerUtil.class);

  private static final String HRID_ERROR_MESSAGE = "lower(f_unaccent(jsonb ->> 'hrid'::text))";
  private static final String MATCH_KEY_ERROR_MESSAGE = "lower(f_unaccent(jsonb ->> 'matchKey'::text))";
  private static final String HRID = "HRID";
  private static final String TABLE_NAME = "instance";

  private ResponseHandlerUtil() {
  }

  public static Response handleHridError(Response response) {
    var statusCode = response.getStatus();

    if (statusCode == 201) {
      return response;
    }

    var errorMessage = getErrorMessage(response.getEntity());
    if (errorMessage.contains(HRID_ERROR_MESSAGE)) {
      return createResponse(response);
    }
    return response;
  }

  public static Response failedValidationResponse(Response response) {
    var entity = (Errors) response.getEntity();
    var errors = entity.getErrors();
    errors.getFirst().setMessage(errors.getFirst().getMessage().replace(HRID_ERROR_MESSAGE, HRID));
    entity.setErrors(errors);
    return Response.fromResponse(response)
      .entity(entity)
      .build();
  }

  public static Response handleHridErrorInInstance(Response response) {
    var statusCode = response.getStatus();

    if (statusCode == 201) {
      return response;
    }

    var errorMessage = getErrorMessage(response.getEntity());
    if (errorMessage.contains(HRID_ERROR_MESSAGE)) {
      return createResponse(response, errorMessage);
    } else if (errorMessage.contains(MATCH_KEY_ERROR_MESSAGE)) {
      return createMatchKeyResponse(response);
    }
    return response;
  }

  /**
   * On success pass the result to asyncResultHandler. On failure create an error Response for the Throwable
   * and pass it to asyncResultHandler.
   */
  public static Handler<AsyncResult<Response>> handle(Handler<AsyncResult<Response>> asyncResultHandler) {
    return result -> {
      if (result.succeeded()) {
        asyncResultHandler.handle(succeededFuture(result.result()));
      } else {
        handleFailure(asyncResultHandler).handle(result.cause());
      }
    };
  }

  public static void handleFailure(Throwable error,
                                   Handler<AsyncResult<Response>> asyncResultHandler,
                                   Function<Errors, Response> validationHandler,
                                   Function<String, Response> serverErrorHandler) {

    log.warn("Error occurred", error);

    Response response;
    if (error instanceof ValidationException validationException) {
      response = validationHandler.apply(validationException.getErrors());
    } else {
      response = serverErrorHandler.apply(error.getMessage());
    }
    asyncResultHandler.handle(succeededFuture(response));
  }

  /**
   * Use future.onError(handleFailure(asyncResultHandler, validationHandler, serverErrorHandler)).
   */
  public static Handler<Throwable> handleFailure(Handler<AsyncResult<Response>> asyncResultHandler,
                                                 Function<Errors, Response> validationHandler,
                                                 Function<String, Response> serverErrorHandler) {
    return e -> handleFailure(e, asyncResultHandler, validationHandler, serverErrorHandler);
  }

  public static Handler<Throwable> handleFailure(Handler<AsyncResult<Response>> asyncResultHandler) {
    return error -> asyncResultHandler.handle(succeededFuture(failureResponse(error)));
  }

  /**
   * Same as {@link #handleFailure(Handler)} but renders validation failures with the given handler. Use it for
   * endpoints that do not declare a 422 response, where RMB reports those failures with another status.
   */
  public static Handler<Throwable> handleFailure(Handler<AsyncResult<Response>> asyncResultHandler,
                                                 Function<Errors, Response> validationHandler) {
    return error -> asyncResultHandler.handle(succeededFuture(failureResponse(error, validationHandler)));
  }

  public static String firstErrorMessage(Errors errors) {
    return errors.getErrors().getFirst().getMessage();
  }

  /**
   * Same as {@link #handleFailure(Handler)} but lets the mapper adjust the failure response, for example to
   * rewrite database specific messages.
   */
  public static Handler<Throwable> handleFailureWith(Handler<AsyncResult<Response>> asyncResultHandler,
                                                     UnaryOperator<Response> responseMapper) {
    return error -> asyncResultHandler.handle(succeededFuture(responseMapper.apply(failureResponse(error))));
  }

  public static Response failureResponse(Throwable error) {
    log.warn("An error occurred", error);

    if (error instanceof BadRequestException || error instanceof CQLQueryValidationException) {
      return textPlainResponse(400, error);
    } else if (error instanceof NotFoundException) {
      return textPlainResponse(404, error);
    } else if (error instanceof RequestEntityTooLargeException) {
      return textPlainResponse(413, error);
    } else if (error instanceof ValidationException validationException) {
      final Errors errors = validationException.getErrors();
      return unprocessableContentResponse(errors);
    } else if (error instanceof SettingsValidationException) {
      return textPlainResponse(422, error);
    } else if (PgExceptionUtil.isVersionConflict(error)) {
      return textPlainResponse(409, error);
    } else if (error instanceof ResponseException responseException) {
      return responseException.getResponse();
    }
    String message = PgExceptionUtil.badRequestMessage(error);
    if (message != null) {
      return textPlainResponse(400, message);
    }
    return textPlainResponse(500, error);
  }

  public static Response failureResponse(Throwable error, Function<Errors, Response> validationHandler) {
    if (error instanceof ValidationException validationException) {
      log.warn("Validation failed", error);
      return validationHandler.apply(validationException.getErrors());
    }
    return failureResponse(error);
  }

  /**
   * Returns the message of the error response, an empty string when there is none: no entity, no errors
   * or an error without a message.
   */
  private static String getErrorMessage(Object responseEntity) {
    if (responseEntity instanceof Errors errors) {
      var errorList = errors.getErrors();
      if (errorList == null || errorList.isEmpty()) {
        return "";
      }
      return Objects.toString(errorList.getFirst().getMessage(), "");
    }
    return Objects.toString(responseEntity, "");
  }

  private static Response createResponse(Response response) {
    if (response.getStatus() == 400) {
      return Response.fromResponse(response)
        .entity(response.getEntity().toString().replace(HRID_ERROR_MESSAGE, HRID)).build();
    } else {
      return failedValidationResponse(response);
    }
  }

  private static Response createResponse(Response response, String errorMessage) {
    var transformedMessage = transformHridErrorMessage(errorMessage);
    return Response.fromResponse(response)
      .entity(transformedMessage)
      .build();
  }

  private static Response createMatchKeyResponse(Response response) {
    var entity = response.getEntity().toString();
    var matchKeyValue = extractValue(entity);
    var remappedMessage = String.format("%s value already exists in table instance: %s",
      MATCH_KEY_ERROR_MESSAGE, matchKeyValue);
    return Response.fromResponse(response)
      .entity(remappedMessage)
      .build();
  }

  private static String transformHridErrorMessage(String errorMessage) {
    var hridValue = extractValue(errorMessage);
    return hridValue != null
           ? String.format("%s value already exists in table %s: %s", HRID, TABLE_NAME, hridValue)
           : errorMessage;
  }

  private static String extractValue(String errorMessage) {
    var startIndex = errorMessage.indexOf("=(") + 2;
    var endIndex = errorMessage.indexOf(")", startIndex);
    return (startIndex > 1 && endIndex > startIndex) ? errorMessage.substring(startIndex, endIndex) : null;
  }

  private static Response textPlainResponse(int status, Throwable error) {
    return textPlainResponse(status, error.getMessage());
  }

  private static Response textPlainResponse(int status, String message) {
    return Response.status(status).header(CONTENT_TYPE, "text/plain")
      .entity(message).build();
  }

  private static Response unprocessableContentResponse(Object jsonEntity) {
    return Response.status(422).header(CONTENT_TYPE, "application/json")
      .entity(jsonEntity).build();
  }
}
