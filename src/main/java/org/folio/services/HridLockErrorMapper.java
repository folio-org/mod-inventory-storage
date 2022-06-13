package org.folio.services;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;

public class HridLockErrorMapper {
  /** custom PostgreSQL error code defined in hrid_lock() trigger */
  public static final String HRID_LOCK_ERROR_CODE = "23578";
  private static final String HRID_LOCK_ERROR_SUFFIX = " (" + HRID_LOCK_ERROR_CODE + ")";

  private HridLockErrorMapper() {}

  public static Response map(Response response) {
    if (response.getStatus() == 500 && endsWith(response.getEntity(), HRID_LOCK_ERROR_SUFFIX)) {
      return Response.status(400).type(MediaType.TEXT_PLAIN).entity(response.getEntity()).build();
    }
    return response;
  }

  private static boolean endsWith(Object object, CharSequence suffix) {
    if (object == null) {
      return false;
    }
    return StringUtils.endsWith(object.toString(), suffix);
  }
}
