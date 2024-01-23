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
    var statusCode = response.getStatus();
    var errorMessage = response.getEntity().toString();

    logger.info("Status code is" + statusCode + " and error message is " + errorMessage);

    if (errorMessage.contains(HRID_ERROR_MESSAGE)
      && errorMessage.contains("instance") && statusCode == 400) {
      return createResponse(400, errorMessage);
    } else if (errorMessage.contains(HRID_ERROR_MESSAGE)
      && (errorMessage.contains("item") || errorMessage.contains("holdings_record")) && statusCode == 422) {
      return createResponse(422, errorMessage);
    }
    return response;
  }

  private static Response createResponse(int status, String message) {
    return textPlainResponse(status, message.replace(HRID_ERROR_MESSAGE, HRID));
  }

  private static Response textPlainResponse(int status, String message) {
    return Response.status(status).header(CONTENT_TYPE, "text/plain")
      .entity(message).build();
  }
}
