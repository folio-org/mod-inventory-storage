package org.folio.services;

import java.util.Optional;
import java.util.regex.Pattern;
import org.marc4j.callnum.CallNumber;
import org.marc4j.callnum.DeweyCallNumber;
import org.marc4j.callnum.LCCallNumber;

public class CallNumberUtils {

  private CallNumberUtils() {}

  private static final Pattern SHELF_KEY_PATTERN =
    Pattern.compile("([A-Z]+)\\s(\\d{2,}(\\.\\d+)?)(\\s[A-Z]\\d{1,10}){0,2}(\\s.{0,10}){0,10}");

  public static Optional<String> getShelfKeyFromCallNumber(String callNumber) {
    if (SHELF_KEY_PATTERN.matcher(callNumber).matches()) {
      return Optional.of(callNumber).map(String::trim);
    }
    return Optional.ofNullable(callNumber)
      .flatMap(cn -> getValidShelfKey(new LCCallNumber(cn))
        .or(() -> getValidShelfKey(new DeweyCallNumber(cn))))
      .or(() -> Optional.ofNullable(callNumber))
      .map(String::trim);
  }

  private static Optional<String> getValidShelfKey(CallNumber value) {
    return Optional.of(value)
      .filter(CallNumber::isValid)
      .map(CallNumber::getShelfKey);
  }
}
