package org.folio.services;

import static org.folio.services.CallNumberConstants.DEWEY_CN_TYPE_ID;
import static org.folio.services.CallNumberConstants.LC_CN_TYPE_ID;
import static org.folio.services.CallNumberConstants.NLM_CN_TYPE_ID;
import static org.folio.services.CallNumberConstants.SU_DOC_CN_TYPE_ID;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.marc4j.callnum.CallNumber;
import org.marc4j.callnum.DeweyCallNumber;
import org.marc4j.callnum.LCCallNumber;
import org.marc4j.callnum.NlmCallNumber;

public final class CallNumberUtils {

  private static final Map<String, Function<String, Optional<String>>> logCallNumberMap = new HashMap<>();

  private CallNumberUtils() {
  }

  static {
    logCallNumberMap.put(DEWEY_CN_TYPE_ID, (String cn) -> getValidShelfKey(new DeweyCallNumber(cn)));
    logCallNumberMap.put(LC_CN_TYPE_ID, (String cn) -> getValidShelfKey(new LCCallNumber(cn)));
    logCallNumberMap.put(NLM_CN_TYPE_ID, (String cn) -> getValidShelfKey(new NlmCallNumber(cn)));
    logCallNumberMap.put(SU_DOC_CN_TYPE_ID, (String cn) -> getValidShelfKey(new SuDocCallNumber(cn)));
  }

  public static Optional<String> getShelfKeyFromCallNumber(String callNumberTypeId, String callNumber) {
    var function = logCallNumberMap.get(callNumberTypeId);
    if (function != null) {
      return function.apply(callNumber);
    }

    return Optional.ofNullable(callNumber)
      .map(String::trim);
  }

  private static Optional<String> getValidShelfKey(CallNumber value) {
    return Optional.of(value)
      .filter(CallNumber::isValid)
      .map(CallNumber::getShelfKey);
  }
}
