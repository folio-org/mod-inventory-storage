package org.folio.rest.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import org.folio.dbschema.ObjectMapperTool;

public final class CollectionUtil {
  private CollectionUtil() { }

  /**
   * Makes deep copy of the collection using serialization to/from json.
   *
   * @throws IllegalArgumentException - if can not serialize/deserialize json.
   */
  public static <T> Collection<T> deepCopy(Collection<T> collection, Class<T> type) {
    if (collection == null) {
      return Collections.emptyList();
    }

    return collection.stream()
      .map(r -> clone(r, type))
      .collect(Collectors.toList());
  }

  public static <T> T getFirst(Collection<T> collection) {
    return collection != null && !collection.isEmpty() ? collection.iterator().next() : null;
  }

  /**
   * Serialize/deserialize to/from json.
   *
   * @throws IllegalArgumentException - if can not serialize/deserialize to/from json
   */
  private static <T> T clone(T obj, Class<T> type) {
    try {
      final ObjectMapper jsonMapper = ObjectMapperTool.getMapper();
      return jsonMapper.readValue(jsonMapper.writeValueAsString(obj), type);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException(ex);
    }
  }
}
