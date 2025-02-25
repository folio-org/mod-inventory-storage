package org.folio.services;

import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.Errors;

public final class ResponseHandlerUtil {
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

  private static String getErrorMessage(Object responseEntity) {
    if (responseEntity instanceof Errors errors) {
      return errors.getErrors().getFirst().getMessage();
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
}
