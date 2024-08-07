package org.folio.rest.support;

import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;

import javax.ws.rs.core.Response;
import org.folio.HttpStatus;

public final class ResponseUtil {

  public static final String SOURCE_CANNOT_BE_FOLIO =
    "Illegal operation: Source field cannot be set to folio";
  public static final String SOURCE_CANNOT_BE_UPDATED =
    "Illegal operation: Source field cannot be updated";

  private ResponseUtil() { }

  public static boolean isUpdateSuccessResponse(Response response) {
    return responseHasStatus(response, HTTP_NO_CONTENT);
  }

  public static boolean isDeleteSuccessResponse(Response response) {
    return responseHasStatus(response, HTTP_NO_CONTENT);
  }

  public static boolean isCreateSuccessResponse(Response response) {
    return responseHasStatus(response, HTTP_CREATED);
  }

  private static boolean responseHasStatus(Response response, HttpStatus expectedStatus) {
    return response != null && response.getStatus() == expectedStatus.toInt();
  }
}
