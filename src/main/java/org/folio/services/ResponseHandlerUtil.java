package org.folio.services;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

import java.util.logging.Logger;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.Errors;

public final class ResponseHandlerUtil {
  private static final Logger logger = Logger.getLogger(ResponseHandlerUtil.class.getName());
  private static final String HRID_ERROR_MESSAGE = "lower(f_unaccent(jsonb ->> 'hrid'::text))";
  private static final String HRID = "HRID";

  private ResponseHandlerUtil() {
  }

  public static Response handleHridError(Response response) {
    var statusCode = response.getStatus();
    var errorMessage = getErrorMessage(response.getEntity());

    logger.info("Status code is" + statusCode + " and error message is " + errorMessage);

    if (errorMessage.contains(HRID_ERROR_MESSAGE)
      && errorMessage.contains("instance") && statusCode == 400) {
      logger.info("Constructing response with 400 status code and message: " + errorMessage);
      return createResponse(400, errorMessage);
    } else if (errorMessage.contains(HRID_ERROR_MESSAGE)
      && (errorMessage.contains("item") || errorMessage.contains("holdings_record")) && statusCode == 422) {
      logger.info("Constructing response with 422 status code and message: " + errorMessage);
      return createResponse(422, errorMessage);
    }
    return response;
  }

  private static String getErrorMessage(Object responseEntity) {
    var errorMessage = responseEntity.toString();
    if (responseEntity instanceof Errors errors) {
      logger.info("responseEntity is instance of Errors");
      errorMessage = errors.getErrors().get(0).getMessage();
    } else if (responseEntity instanceof String message) {
      logger.info("responseEntity is instance of String");
      errorMessage = message;
    }
    logger.info("Error message is " + errorMessage);
    return errorMessage;
  }

  private static Response createResponse(int status, String message) {
    if (status == 400) {
      return textPlainResponse(status, message.replace(HRID_ERROR_MESSAGE, HRID));
    } else {
      return failedValidationResponse(message);
    }
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
