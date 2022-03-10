package org.folio.services;

import java.util.Optional;
import org.marc4j.callnum.CallNumber;
import org.marc4j.callnum.DeweyCallNumber;
import org.marc4j.callnum.LCCallNumber;

public class CallNumberUtils {

  private CallNumberUtils() {}

  public static Optional<String> getShelfKeyFromCallNumber(String callNumber) {
    return Optional.ofNullable(callNumber)
      .flatMap(cn -> getValidShelfKey(new LCCallNumber(cn))
        .or(() -> getValidShelfKey(new DeweyCallNumber(cn))));
  }

  private static Optional<String> getValidShelfKey(CallNumber value) {
    return Optional.of(value)
      .filter(CallNumber::isValid)
      .map(CallNumber::getShelfKey);
  }
}
