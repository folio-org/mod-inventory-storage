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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;

class SanitizerTest {

  private static final Sanitizer<String> TEST_SANITIZER = entity -> { };

  @ParameterizedTest
  @NullSource
  @MethodSource("emptyListProvider")
  void cleanListShouldReturnEmptyListWhenInputIsNullOrEmpty(List<String> input) {
    List<String> result = TEST_SANITIZER.cleanList(input);
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  private static Stream<List<String>> emptyListProvider() {
    return Stream.of(new ArrayList<>());
  }

  @ParameterizedTest
  @MethodSource("listFilteringProvider")
  void cleanListShouldFilterOutBlankAndWhitespaceStrings(List<String> input, List<String> expected) {
    List<String> result = TEST_SANITIZER.cleanList(input);
    assertEquals(expected, result);
  }

  private static Stream<Arguments> listFilteringProvider() {
    return Stream.of(
      Arguments.of(Arrays.asList("valid", "", "  ", null, "another"), Arrays.asList("valid", "another")),
      Arguments.of(Arrays.asList("value1", "   ", "\t", "\n", "value2"), Arrays.asList("value1", "value2"))
    );
  }

  @Test
  void cleanListShouldPreserveOrder() {
    List<String> input = Arrays.asList("first", "", "second", "  ", "third");
    List<String> result = TEST_SANITIZER.cleanList(input);
    assertEquals(Arrays.asList("first", "second", "third"), result);
  }

  @ParameterizedTest
  @NullSource
  @MethodSource("emptySetProvider")
  void cleanSetShouldReturnEmptySetWhenInputIsNullOrEmpty(Set<String> input) {
    Set<String> result = TEST_SANITIZER.cleanSet(input);
    assertNotNull(result);
    assertTrue(result.isEmpty());
    assertInstanceOf(LinkedHashSet.class, result);
  }

  private static Stream<Set<String>> emptySetProvider() {
    return Stream.of(new LinkedHashSet<>());
  }

  @ParameterizedTest
  @MethodSource("setFilteringProvider")
  void cleanSetShouldFilterOutBlankAndWhitespaceStrings(
    Set<String> input,
    int expectedSize,
    List<String> expectedValues
  ) {
    Set<String> result = TEST_SANITIZER.cleanSet(input);
    assertEquals(expectedSize, result.size());
    expectedValues.forEach(value -> assertTrue(result.contains(value)));
  }

  private static Stream<Arguments> setFilteringProvider() {
    return Stream.of(
      Arguments.of(
        new LinkedHashSet<>(Arrays.asList("valid", "", "  ", "another")),
        2,
        Arrays.asList("valid", "another")
      ),
      Arguments.of(
        new LinkedHashSet<>(Arrays.asList("value1", "   ", "\t", "\n", "value2")),
        2,
        Arrays.asList("value1", "value2")
      )
    );
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
