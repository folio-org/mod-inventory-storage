package org.folio.rest.support;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class StatusUpdatedDateGenerator {
    private final static DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    public static String generateStatusUpdatedDate() {
      return df.format(ZonedDateTime.now());
    }
}
