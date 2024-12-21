package org.folio.services;

import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.Errors;

public final class ResponseHandlerUtil {
  private static final String HRID_ERROR_MESSAGE = "lower(f_unaccent(jsonb ->> 'hrid'::text))";
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

  private static String getErrorMessage(Object responseEntity) {
    if (responseEntity instanceof Errors errors) {
      return errors.getErrors().get(0).getMessage();
    }
    return responseEntity.toString();
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

  public static Response failedValidationResponse(Response response) {
    var entity = (Errors) response.getEntity();
    var errors = entity.getErrors();
    errors.get(0).setMessage(errors.get(0).getMessage().replace(HRID_ERROR_MESSAGE, HRID));
    entity.setErrors(errors);
    return Response.fromResponse(response)
      .entity(entity)
      .build();
  }

  // todo: use helper methods from PgUtil class when they become public
  public static Response handleHridErrorInInstance(Response response) {
    var statusCode = response.getStatus();

    if (statusCode == 201) {
      return response;
    }

    var errorMessage = getErrorMessage(response.getEntity());
    if (errorMessage.contains(HRID_ERROR_MESSAGE)) {
      return createResponse(response, errorMessage);
    }
    return response;
  }

  private static String transformHridErrorMessage(String errorMessage) {
    var hridValue = extractHridValue(errorMessage);
    return hridValue != null
      ? String.format("%s value already exists in table %s: %s", HRID, TABLE_NAME, hridValue)
      : errorMessage;
  }

  private static String extractHridValue(String errorMessage) {
    var startIndex = errorMessage.indexOf("=(") + 2;
    var endIndex = errorMessage.indexOf(")", startIndex);
    return (startIndex > 1 && endIndex > startIndex) ? errorMessage.substring(startIndex, endIndex) : null;
  }
}
