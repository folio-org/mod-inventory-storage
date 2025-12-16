package org.folio.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComparisonUtilsTest {

  @Test
  void testPrivateConstructor() throws NoSuchMethodException {
    var constructor = ComparisonUtils.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    assertThrows(InvocationTargetException.class, constructor::newInstance);
  }

  @Test
  void testEqualsIgnoringMetadata_true() throws JsonProcessingException {
    var obj1 = testMap(1, 1);
    var obj2 = testMap(1, 2);

    assertTrue(ComparisonUtils.equalsIgnoringMetadata(obj1, obj2));
  }

  @Test
  void testEqualsIgnoringMetadata_false() throws JsonProcessingException {
    var obj1 = testMap(1, 1);
    var obj2 = testMap(2, 2);

    assertFalse(ComparisonUtils.equalsIgnoringMetadata(obj1, obj2));
  }

  private Map<String, Object> testMap(int fieldValue, int metadataValue) {
    return Map.of("field1", "value1",
    "field2", fieldValue,
    "metadata", Map.of("createdBy", metadataValue));
  }
}
