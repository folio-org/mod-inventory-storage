package org.folio.rest.support;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class StatusUpdatedDateGenerator {

  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

  private StatusUpdatedDateGenerator() {
  }

  public static String generateStatusUpdatedDate() {
    return FORMATTER.format(ZonedDateTime.now(ZoneOffset.UTC));
  }
}
