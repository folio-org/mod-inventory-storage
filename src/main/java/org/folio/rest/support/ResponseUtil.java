package org.folio.rest.support;

import javax.ws.rs.core.Response;

/**
 * Utility methods for Response object.
 */
public final class ResponseUtil {

  private ResponseUtil() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  public static boolean hasCreatedStatus(Response response) {
    return response != null
      && response.getStatus() == Response.Status.CREATED.getStatusCode();
  }

  public static <T> Response copyResponseWithNewEntity(Response originalResponse, T newEntity) {
    if (originalResponse == null) {
      return null;
    }

    return Response.fromResponse(originalResponse)
      .entity(newEntity)
      .build();
  }
}
