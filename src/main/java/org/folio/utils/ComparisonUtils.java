package org.folio.utils;

import static org.folio.rest.persist.PostgresClient.pojo2JsonObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Objects;

public final class ComparisonUtils {

  private static final String METADATA_FIELD = "metadata";

  private ComparisonUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static boolean equalsIgnoringMetadata(Object o1, Object o2) throws JsonProcessingException {
    var o1json = pojo2JsonObject(o1);
    var o2json = pojo2JsonObject(o2);
    o1json.remove(METADATA_FIELD);
    o2json.remove(METADATA_FIELD);

    return Objects.equals(o1json, o2json);
  }
}
