package org.folio.services;

import java.util.Optional;
import org.marc4j.callnum.CallNumber;
import org.marc4j.callnum.DeweyCallNumber;
import org.marc4j.callnum.LCCallNumber;
import org.marc4j.callnum.NlmCallNumber;

public final class CallNumberUtils {

  private CallNumberUtils() { }

  public static Optional<String> getShelfKeyFromCallNumber(String callNumber) {
    return Optional.ofNullable(callNumber)
      .flatMap(cn -> getValidShelfKey(new NlmCallNumber(cn))
        .or(() -> getValidShelfKey(new LCCallNumber(cn)))
        .or(() -> getValidShelfKey(new DeweyCallNumber(cn)))
        .or(() -> getValidShelfKey(new SuDocCallNumber(cn))))
      .or(() -> Optional.ofNullable(callNumber))
      .map(String::trim);
  }

  private static Optional<String> getValidShelfKey(CallNumber value) {
    return Optional.of(value)
      .filter(CallNumber::isValid)
      .map(CallNumber::getShelfKey);
  }
}
