package org.folio.utils;

import static org.folio.rest.persist.PostgresClient.pojo2JsonObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.folio.rest.jaxrs.model.Item;

public final class ComparisonUtils {

  private static final String METADATA_FIELD = "metadata";

  private static final Map<Class<?>, List<Consumer<JsonObject>>> CLASS_SPECIFIC_ACTIONS;

  static {
    CLASS_SPECIFIC_ACTIONS = Map.of(Item.class, List.of(
      json -> json.getJsonObject("status").remove("date")
    ));
  }

  private ComparisonUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static boolean equalsIgnoringMetadata(Object o1, Object o2) throws JsonProcessingException {
    var o1json = pojo2JsonObject(o1);
    var o2json = pojo2JsonObject(o2);
    o1json.remove(METADATA_FIELD);
    o2json.remove(METADATA_FIELD);

    CLASS_SPECIFIC_ACTIONS.forEach((clazz, actions) -> {
      if (o1.getClass() == o2.getClass() && o1.getClass().equals(clazz)) {
        actions.forEach(action -> action.accept(o1json));
        actions.forEach(action -> action.accept(o2json));
      }
    });

    return Objects.equals(o1json, o2json);
  }
}
