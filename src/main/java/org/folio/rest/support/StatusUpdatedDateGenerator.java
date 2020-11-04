package org.folio.rest.support;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class StatusUpdatedDateGenerator {
    public static String generateStatusUpdatedDate() {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
      return sdf.format(new Date());
    } 
}
