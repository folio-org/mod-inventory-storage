package org.folio.util;

import java.util.Map;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.logging.FolioLoggingContext;

public class LoggingUtil {

  public static void populateLoggingContext(Map<String, String> okapiHeaders) {
    FolioLoggingContext.put("recordId", okapiHeaders.get("X-Okapi-Record-Id"));
    FolioLoggingContext.put("jobId", okapiHeaders.get("X-Okapi-Job-Execution-Id"));
  }

  public static void logRequestArrival(String endpointPath, Map<String, String> okapiHeaders, Logger log) {
    long requestArrival = System.currentTimeMillis();
    String requestStart = okapiHeaders.get("X-Okapi-Req-Start");
    long requestTransferringTime = requestStart != null ? requestArrival - Long.parseLong(requestStart) : -1;
    log.info("invoking {}, request was received in {} ms", endpointPath, requestTransferringTime);
  }

}
