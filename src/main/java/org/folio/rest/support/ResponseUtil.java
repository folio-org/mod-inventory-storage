package org.folio.rest.support;

import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;

import javax.ws.rs.core.Response;

public final class ResponseUtil {
  private ResponseUtil() {}

  public static boolean isUpdateSuccessResponse(Response response) {
    return response != null && response.getStatus() == HTTP_NO_CONTENT.toInt();
  }

  public static boolean isDeleteSuccessResponse(Response response) {
    return response != null && response.getStatus() == HTTP_NO_CONTENT.toInt();
  }

  public static boolean isCreateSuccessResponse(Response response) {
    return response != null && response.getStatus() == HTTP_CREATED.toInt();
  }
}
