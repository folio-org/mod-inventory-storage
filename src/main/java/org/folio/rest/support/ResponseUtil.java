package org.folio.rest.support;

import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;

import javax.ws.rs.core.Response;

public final class ResponseUtil {
  private ResponseUtil() {}

  public static boolean isUpdateSuccessResponse(Response response) {
    return responseIsInStatus(response, HTTP_NO_CONTENT.toInt());
  }

  public static boolean isDeleteSuccessResponse(Response response) {
    return responseIsInStatus(response, HTTP_NO_CONTENT.toInt());
  }

  public static boolean isCreateSuccessResponse(Response response) {
    return responseIsInStatus(response, HTTP_CREATED.toInt());
  }

  private static boolean responseIsInStatus(Response response, int expectedStatus) {
    return response != null && response.getStatus() == expectedStatus;
  }
}
