package org.folio.services;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

import javax.ws.rs.core.Response;

public final class ResponseHandlerUtil {
  private static final String HRID_ERROR_MESSAGE = "lower(f_unaccent(jsonb ->> 'hrid'::text))";
  private static final String HRID = "HRID ";

  private ResponseHandlerUtil() {
  }

  public static Response handleResponse(Response response) {
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
