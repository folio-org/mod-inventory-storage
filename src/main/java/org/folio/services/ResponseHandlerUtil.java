package org.folio.services;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.Errors;

public final class ResponseHandlerUtil {
  private static final String HRID_ERROR_MESSAGE = "lower(f_unaccent(jsonb ->> 'hrid'::text))";
  private static final String HRID = "HRID";

  private ResponseHandlerUtil() {
  }

  public static Response handleHridError(Response response) {
    var statusCode = response.getStatus();

    if (statusCode == 201) {
      return response;
    }

    var errorMessage = getErrorMessage(response.getEntity());
    if (errorMessage.contains(HRID_ERROR_MESSAGE)
      && errorMessage.contains("instance") && statusCode == 400) {
      return createResponse(400, errorMessage);
    } else if (errorMessage.contains(HRID_ERROR_MESSAGE)
      && (errorMessage.contains("item") || errorMessage.contains("holdings_record")) && statusCode == 422) {
      return createResponse(422, errorMessage);
    }
    return response;
  }

  private static String getErrorMessage(Object responseEntity) {
    var errorMessage = responseEntity.toString();
    if (responseEntity instanceof Errors errors) {
      errorMessage = errors.getErrors().get(0).getMessage();
    } else if (responseEntity instanceof String message) {
      errorMessage = message;
    }
    return errorMessage;
  }

  private static Response createResponse(int status, String message) {
    if (status == 400) {
      return textPlainResponse(status, message.replace(HRID_ERROR_MESSAGE, HRID));
    } else {
      return failedValidationResponse(message.replace(HRID_ERROR_MESSAGE, HRID));
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
