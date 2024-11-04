package org.folio.utils;

import java.util.Map;
import org.folio.okapi.common.logging.FolioLoggingContext;

public class LoggingUtil {

  public static void populateLoggingContext(Map<String, String> okapiHeaders) {
    FolioLoggingContext.put("recordId", okapiHeaders.get("X-Okapi-Record-Id"));
    FolioLoggingContext.put("jobId", okapiHeaders.get("X-Okapi-Job-Execution-Id"));
  }

}
