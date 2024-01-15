package org.folio.rest.support;

import static io.vertx.core.Future.succeededFuture;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import javax.ws.rs.core.Response;


public final class EndpointHandler {
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

  public static Handler<Response> handleResponse(Handler<AsyncResult<Response>> asyncResultHandler) {
    return response -> asyncResultHandler.handle(succeededFuture(handleResponse(response)));
  }

  private static Response handleResponse(Response response) {
    var errorMessage = response.getEntity().toString();
    if (errorMessage.contains(HRID_ERROR_MESSAGE) && (errorMessage.contains("instance"))) {
      return createResponse(errorMessage);
    } else {
      return response;
    }
  }

  private static Response createResponse(String errorMessage) {
    var message = extractErrorMessage(errorMessage, HRID_ERROR_MESSAGE);
    return textPlainResponse(400, HRID + message);
  }

  private static Response textPlainResponse(int status, String message) {
    return Response.status(status).header(CONTENT_TYPE, "text/plain")
      .entity(message).build();
  }

  public static String extractErrorMessage(String input, String substringToFind) {
    int index = input.indexOf(substringToFind);
    if (index != -1) {
      return input.substring(index + substringToFind.length()).trim();
    } else {
      return input;
    }
  }
}
