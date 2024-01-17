package org.folio.rest.support;

import static io.vertx.core.Future.succeededFuture;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import java.util.logging.Logger;
import javax.ws.rs.core.Response;


public final class EndpointHandler {
  private static final Logger logger = Logger.getLogger(EndpointHandler.class.getName());
  private static final String HRID_ERROR_MESSAGE = "lower(f_unaccent(jsonb ->> 'hrid'::text))";
  private static final String HRID = "HRID ";

  private EndpointHandler() {
  }

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

  public static Response handleResponse(Response response) {
    var errorMessage = response.getEntity().toString();
    logger.info("Error message in handleResponse: " + errorMessage);
    if (errorMessage.contains(HRID_ERROR_MESSAGE) && (errorMessage.contains("instance"))) {
      logger.info("Entered the if statement in the handleResponse");
      return createResponse(errorMessage);
    } else {
      return response;
    }
  }

  private static Response createResponse(String errorMessage) {
    logger.info("Error message in the createResponse: " + errorMessage);
    var message = extractErrorMessage(errorMessage, HRID_ERROR_MESSAGE);
    return textPlainResponse(400, HRID + message);
  }

  private static Response textPlainResponse(int status, String message) {
    return Response.status(status).header(CONTENT_TYPE, "text/plain")
      .entity(message).build();
  }

  public static String extractErrorMessage(String input, String substringToFind) {
    logger.info("Error message in the extractErrorMessage: " + input);
    int index = input.indexOf(substringToFind);
    if (index != -1) {
      logger.info("Entered the if statement in the extractErrorMessage");
      return input.substring(index + substringToFind.length()).trim();
    } else {
      return input;
    }
  }
}
