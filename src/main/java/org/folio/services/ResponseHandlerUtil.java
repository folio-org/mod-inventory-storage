package org.folio.services;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

import java.util.logging.Logger;
import javax.ws.rs.core.Response;

public final class ResponseHandlerUtil {
  private static final Logger logger = Logger.getLogger(ResponseHandlerUtil.class.getName());
  private static final String HRID_ERROR_MESSAGE = "lower(f_unaccent(jsonb ->> 'hrid'::text))";
  private static final String HRID = "HRID";

  private ResponseHandlerUtil() {
  }

  public static Response handleInstanceHridError(Response response) {
    logger.info("Response status code" + response.getStatus());
    if (response.getStatus() != 400) {
      return response;
    }
    var errorMessage = response.getEntity().toString();
    if (errorMessage.contains(HRID_ERROR_MESSAGE)
      && (errorMessage.contains("instance") || errorMessage.contains("item")
      || errorMessage.contains("holdings_record"))) {
      logger.info("Inside the if statement with the error message: " + errorMessage);
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
