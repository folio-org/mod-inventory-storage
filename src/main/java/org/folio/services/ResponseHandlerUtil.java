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
      return createResponse(response);
    } else if (errorMessage.contains(HRID_ERROR_MESSAGE)
      && (errorMessage.contains("item") || errorMessage.contains("holdings_record")) && statusCode == 422) {
      return createResponse(response);
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

  private static Response createResponse(Response response) {
    if (response.getStatus() == 400) {
      return Response.fromResponse(response).header(CONTENT_TYPE, "text/plain")
        .entity(response.getEntity().toString().replace(HRID_ERROR_MESSAGE, HRID)).build();
    } else {
      return failedValidationResponse(response);
    }
  }

  public static Response failedValidationResponse(Response response) {
    var entity = (Errors) response.getEntity();
    var errors = entity.getErrors();
    errors.get(0).setMessage(errors.get(0).getMessage().replace(HRID_ERROR_MESSAGE, HRID));
    entity.setErrors(errors);
    return Response.fromResponse(response)
      .entity(entity)
      .header(CONTENT_TYPE, "application/json")
      .build();
  }
}
