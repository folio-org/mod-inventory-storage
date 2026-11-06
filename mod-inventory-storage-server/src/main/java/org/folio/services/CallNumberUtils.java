package org.folio.services;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.marc4j.callnum.CallNumber;
import org.marc4j.callnum.DeweyCallNumber;
import org.marc4j.callnum.LCCallNumber;
import org.marc4j.callnum.NlmCallNumber;

public final class CallNumberUtils {

  public static final String DEWEY_CN_TYPE_ID = "03dd64d0-5626-4ecd-8ece-4531e0069f35";
  public static final String LC_CN_TYPE_ID = "95467209-6d7b-468b-94df-0f5d7ad2747d";
  public static final String NLM_CN_TYPE_ID = "054d460d-d6b9-4469-9e37-7a78a2266655";
  public static final String SU_DOC_CN_TYPE_ID = "fc388041-6cd0-4806-8a74-ebe3b9ab4c6e";
  private static final Map<String, Function<String, Optional<String>>> logCallNumberMap = new HashMap<>();

  private CallNumberUtils() {
  }

  static {
    logCallNumberMap.put(DEWEY_CN_TYPE_ID, (String cn) -> getShelfKey(new DeweyCallNumber(cn)));
    logCallNumberMap.put(LC_CN_TYPE_ID, (String cn) -> getShelfKey(new LCCallNumber(cn)));
    logCallNumberMap.put(NLM_CN_TYPE_ID, (String cn) -> getShelfKey(new NlmCallNumber(cn)));
    logCallNumberMap.put(SU_DOC_CN_TYPE_ID, (String cn) -> getShelfKey(new SuDocCallNumber(cn)));
  }

  public static Optional<String> getShelfKeyFromCallNumber(String callNumberTypeId, String callNumber) {
    var function = logCallNumberMap.get(callNumberTypeId);
    if (function != null) {
      return function.apply(callNumber)
        .map(String::trim);
    }

    return Optional.ofNullable(callNumber)
      .map(String::trim);
  }

  private static Optional<String> getShelfKey(CallNumber value) {
    return Optional.of(value)
      .map(CallNumber::getShelfKey);
  }
}
