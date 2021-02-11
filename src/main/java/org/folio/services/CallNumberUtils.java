package org.folio.services;

import org.marc4j.callnum.CallNumber;
import org.marc4j.callnum.LCCallNumber;

import java.util.Optional;

public class CallNumberUtils {
  public static Optional<String> getShelfKeyFromCallNumber(String callNumberParam) {
    if (callNumberParam == null) return Optional.empty();
    CallNumber callNumber = new LCCallNumber();
    callNumber.parse(callNumberParam);
    return  callNumber.isValid() ? Optional.of(callNumber.getShelfKey()) : Optional.empty();
  }
}
