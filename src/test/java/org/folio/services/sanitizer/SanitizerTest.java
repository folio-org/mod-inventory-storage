package org.folio.services.sanitizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SanitizerTest {

  private static final Sanitizer<String> TEST_SANITIZER = entity -> { };

  @Test
  void cleanListShouldReturnEmptyListWhenInputIsNull() {
    List<String> result = TEST_SANITIZER.cleanList(null);
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void cleanListShouldReturnEmptyListWhenInputIsEmpty() {
    List<String> result = TEST_SANITIZER.cleanList(new ArrayList<>());
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void cleanListShouldFilterOutBlankStrings() {
    List<String> input = Arrays.asList("valid", "", "  ", null, "another");
    List<String> result = TEST_SANITIZER.cleanList(input);
    assertEquals(2, result.size());
    assertEquals("valid", result.get(0));
    assertEquals("another", result.get(1));
  }

  @Test
  void cleanListShouldFilterOutWhitespaceOnlyStrings() {
    List<String> input = Arrays.asList("value1", "   ", "\t", "\n", "value2");
    List<String> result = TEST_SANITIZER.cleanList(input);
    assertEquals(2, result.size());
    assertEquals("value1", result.get(0));
    assertEquals("value2", result.get(1));
  }

  @Test
  void cleanListShouldPreserveOrder() {
    List<String> input = Arrays.asList("first", "", "second", "  ", "third");
    List<String> result = TEST_SANITIZER.cleanList(input);
    assertEquals(Arrays.asList("first", "second", "third"), result);
  }

  @Test
  void cleanSetShouldReturnEmptySetWhenInputIsNull() {
    Set<String> result = TEST_SANITIZER.cleanSet(null);
    assertNotNull(result);
    assertTrue(result.isEmpty());
    assertInstanceOf(LinkedHashSet.class, result);
  }

  @Test
  void cleanSetShouldReturnEmptySetWhenInputIsEmpty() {
    Set<String> result = TEST_SANITIZER.cleanSet(new LinkedHashSet<>());
    assertNotNull(result);
    assertTrue(result.isEmpty());
    assertInstanceOf(LinkedHashSet.class, result);
  }

  @Test
  void cleanSetShouldFilterOutBlankStrings() {
    Set<String> input = new LinkedHashSet<>(Arrays.asList("valid", "", "  ", "another"));
    Set<String> result = TEST_SANITIZER.cleanSet(input);
    assertEquals(2, result.size());
    assertTrue(result.contains("valid"));
    assertTrue(result.contains("another"));
  }

  @Test
  void cleanSetShouldFilterOutWhitespaceOnlyStrings() {
    Set<String> input = new LinkedHashSet<>(Arrays.asList("value1", "   ", "\t", "\n", "value2"));
    Set<String> result = TEST_SANITIZER.cleanSet(input);
    assertEquals(2, result.size());
    assertTrue(result.contains("value1"));
    assertTrue(result.contains("value2"));
  }

  @Test
  void cleanSetShouldPreserveInsertionOrder() {
    Set<String> input = new LinkedHashSet<>(Arrays.asList("first", "", "second", "  ", "third"));
    Set<String> result = TEST_SANITIZER.cleanSet(input);
    assertEquals(3, result.size());
    List<String> resultList = new ArrayList<>(result);
    assertEquals("first", resultList.get(0));
    assertEquals("second", resultList.get(1));
    assertEquals("third", resultList.get(2));
  }

  @Test
  void cleanSetShouldReturnLinkedHashSetType() {
    Set<String> input = new LinkedHashSet<>(Arrays.asList("a", "b", "c"));
    Set<String> result = TEST_SANITIZER.cleanSet(input);
    assertInstanceOf(LinkedHashSet.class, result);
  }
}
