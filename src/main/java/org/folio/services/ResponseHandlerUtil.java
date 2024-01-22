package org.folio.services;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

import javax.ws.rs.core.Response;

public final class ResponseHandlerUtil {
  private static final String HRID_ERROR_MESSAGE = "lower(f_unaccent(jsonb ->> 'hrid'::text))";
  private static final String HRID = "HRID";

  private ResponseHandlerUtil() {
  }

  public static Response handleInstanceHridError(Response response) {
    if (response.getStatus() != 400) {
      return response;
    }
    var errorMessage = response.getEntity().toString();
    if (errorMessage.contains(HRID_ERROR_MESSAGE) && (errorMessage.contains("instance"))) {
      errorMessage = errorMessage.replace(HRID_ERROR_MESSAGE, HRID);
      return textPlainResponse(400, errorMessage);
    }
    return response;
  }

  private static Response textPlainResponse(int status, String message) {
    return Response.status(status).header(CONTENT_TYPE, "text/plain")
      .entity(message).build();
  }
}
