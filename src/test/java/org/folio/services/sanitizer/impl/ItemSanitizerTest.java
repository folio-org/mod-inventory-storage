package org.folio.services.sanitizer.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ItemSanitizerTest {

  private final ItemSanitizer sanitizer = new ItemSanitizer();

  @Test
  void sanitizeShouldHandleNullItem() {
    assertDoesNotThrow(() -> sanitizer.sanitize(null));
  }

  @ParameterizedTest
  @MethodSource("administrativeNotesProvider")
  void sanitizeShouldHandleAdministrativeNotes(List<String> input, List<String> expected) {
    var item = new Item();
    item.setAdministrativeNotes(input);

    sanitizer.sanitize(item);

    assertNotNull(item.getAdministrativeNotes());
    if (expected.isEmpty()) {
      assertTrue(item.getAdministrativeNotes().isEmpty());
    } else {
      assertEquals(expected, item.getAdministrativeNotes());
    }
  }

  @ParameterizedTest
  @MethodSource("setFieldsProvider")
  void sanitizeShouldCleanSetFields(BiConsumer<Item, Set<String>> setter,
                                    Function<Item, Set<String>> getter,
                                    Set<String> input,
                                    List<String> expectedValues) {
    var item = new Item();
    setter.accept(item, input);

    sanitizer.sanitize(item);

    assertNotNull(getter.apply(item));
    if (expectedValues.isEmpty()) {
      assertTrue(getter.apply(item).isEmpty());
    } else {
      assertEquals(expectedValues.size(), getter.apply(item).size());
      expectedValues.forEach(value -> assertTrue(getter.apply(item).contains(value)));
    }
  }

  @ParameterizedTest
  @MethodSource("setFieldsNullProvider")
  void sanitizeShouldHandleNullSetFields(BiConsumer<Item, Set<String>> setter,
                                         Function<Item, Set<String>> getter) {
    var item = new Item();
    setter.accept(item, null);

    sanitizer.sanitize(item);

    assertNotNull(getter.apply(item));
    assertTrue(getter.apply(item).isEmpty());
  }

  @Test
  void sanitizeShouldCleanAllFieldsSimultaneously() {
    var item = new Item();
    item.setAdministrativeNotes(List.of("note1", "", "note2", "  "));
    item.setStatisticalCodeIds(new LinkedHashSet<>(List.of("code1", "", "code2")));
    item.setYearCaption(new LinkedHashSet<>(List.of("code1", "", "code2")));

    sanitizer.sanitize(item);

    assertEquals(2, item.getAdministrativeNotes().size());
    assertEquals(List.of("note1", "note2"), item.getAdministrativeNotes());
    assertEquals(2, item.getStatisticalCodeIds().size());
    assertTrue(item.getStatisticalCodeIds().contains("code1"));
    assertTrue(item.getStatisticalCodeIds().contains("code2"));
    assertEquals(2, item.getYearCaption().size());
    assertTrue(item.getYearCaption().contains("code1"));
    assertTrue(item.getYearCaption().contains("code2"));
  }

  @Test
  void sanitizeShouldPreserveOrderInAdministrativeNotes() {
    var item = new Item();
    var notes = new ArrayList<>(List.of("first", "", "second", "  ", "third"));
    item.setAdministrativeNotes(notes);

    sanitizer.sanitize(item);

    assertEquals(List.of("first", "second", "third"), item.getAdministrativeNotes());
  }

  @Test
  void sanitizeShouldPreserveOrderInStatisticalCodeIds() {
    var item = new Item();
    var codes = new LinkedHashSet<>(List.of("code1", "", "code2", "  ", "code3"));
    item.setStatisticalCodeIds(codes);

    sanitizer.sanitize(item);

    var codesList = new ArrayList<>(item.getStatisticalCodeIds());
    assertEquals(3, codesList.size());
    assertEquals("code1", codesList.get(0));
    assertEquals("code2", codesList.get(1));
    assertEquals("code3", codesList.get(2));
  }

  @Test
  void sanitizeShouldHandleAllBlankValues() {
    var item = new Item();
    item.setAdministrativeNotes(List.of("", "  ", "\t", "\n"));
    item.setStatisticalCodeIds(new LinkedHashSet<>(List.of("", "  ", "\t")));

    sanitizer.sanitize(item);

    assertTrue(item.getAdministrativeNotes().isEmpty());
    assertTrue(item.getStatisticalCodeIds().isEmpty());
  }

  @Test
  void sanitizeShouldReturnLinkedHashSetForStatisticalCodeIds() {
    Item item = new Item();
    item.setStatisticalCodeIds(new LinkedHashSet<>(List.of("code1", "code2")));

    sanitizer.sanitize(item);

    assertInstanceOf(LinkedHashSet.class, item.getStatisticalCodeIds());
  }

  @ParameterizedTest
  @MethodSource("tagsProvider")
  void sanitizeShouldHandleTags(Tags tags, Integer expectedSize, List<String> expectedValues) {
    var item = new Item();
    item.setTags(tags);

    sanitizer.sanitize(item);

    if (expectedSize == null) {
      assertNull(item.getTags());
    } else {
      assertNotNull(item.getTags());
      if (expectedSize == 0) {
        assertTrue(item.getTags().getTagList().isEmpty());
      } else {
        assertEquals(expectedSize, item.getTags().getTagList().size());
        assertEquals(expectedValues, item.getTags().getTagList());
      }
    }
  }

  @Test
  void sanitizeShouldCleanAllFieldsIncludingTags() {
    var item = new Item();
    item.setAdministrativeNotes(List.of("note1", "", "note2", "  "));
    item.setStatisticalCodeIds(new LinkedHashSet<>(List.of("code1", "", "code2")));
    item.setYearCaption(new LinkedHashSet<>(List.of("code1", "", "code2")));

    var tags = new Tags();
    tags.setTagList(List.of("tag1", "", "tag2", "  "));
    item.setTags(tags);

    sanitizer.sanitize(item);

    assertEquals(2, item.getAdministrativeNotes().size());
    assertEquals(2, item.getStatisticalCodeIds().size());
    assertEquals(2, item.getYearCaption().size());
    assertEquals(2, item.getTags().getTagList().size());
    assertEquals(List.of("tag1", "tag2"), item.getTags().getTagList());
  }

  private static Stream<Arguments> administrativeNotesProvider() {
    return Stream.of(
      Arguments.of(List.of("note1", "", "note2", "  ", "note3"), List.of("note1", "note2", "note3")),
      Arguments.of(null, List.of()),
      Arguments.of(new ArrayList<>(), List.of()),
      Arguments.of(List.of("valid note", "\t", "\n", "   "), List.of("valid note"))
    );
  }

  private static Stream<Arguments> setFieldsProvider() {
    return Stream.of(
      statisticalCodeIdsArg1(),
      statisticalCodeIdsArg2(),
      statisticalCodeIdsArg3(),
      yearCaptionArg1(),
      yearCaptionArg2(),
      yearCaptionArg3()
    );
  }

  private static Arguments statisticalCodeIdsArg1() {
    return Arguments.of(
      (BiConsumer<Item, Set<String>>) Item::setStatisticalCodeIds,
      (Function<Item, Set<String>>) Item::getStatisticalCodeIds,
      new LinkedHashSet<>(List.of("code1", "", "code2", "  ", "code3")),
      List.of("code1", "code2", "code3")
    );
  }

  private static Arguments statisticalCodeIdsArg2() {
    return Arguments.of(
      (BiConsumer<Item, Set<String>>) Item::setStatisticalCodeIds,
      (Function<Item, Set<String>>) Item::getStatisticalCodeIds,
      new LinkedHashSet<>(),
      List.of()
    );
  }

  private static Arguments statisticalCodeIdsArg3() {
    return Arguments.of(
      (BiConsumer<Item, Set<String>>) Item::setStatisticalCodeIds,
      (Function<Item, Set<String>>) Item::getStatisticalCodeIds,
      new LinkedHashSet<>(List.of("valid-code", "   ", "\t", "\n")),
      List.of("valid-code")
    );
  }

  private static Arguments yearCaptionArg1() {
    return Arguments.of(
      (BiConsumer<Item, Set<String>>) Item::setYearCaption,
      (Function<Item, Set<String>>) Item::getYearCaption,
      new LinkedHashSet<>(List.of("code1", "", "code2", "  ", "code3")),
      List.of("code1", "code2", "code3")
    );
  }

  private static Arguments yearCaptionArg2() {
    return Arguments.of(
      (BiConsumer<Item, Set<String>>) Item::setYearCaption,
      (Function<Item, Set<String>>) Item::getYearCaption,
      new LinkedHashSet<>(),
      List.of()
    );
  }

  private static Arguments yearCaptionArg3() {
    return Arguments.of(
      (BiConsumer<Item, Set<String>>) Item::setYearCaption,
      (Function<Item, Set<String>>) Item::getYearCaption,
      new LinkedHashSet<>(List.of("valid-code", "   ", "\t", "\n")),
      List.of("valid-code")
    );
  }

  private static Stream<Arguments> setFieldsNullProvider() {
    return Stream.of(
      Arguments.of(
        (BiConsumer<Item, Set<String>>) Item::setStatisticalCodeIds,
        (Function<Item, Set<String>>) Item::getStatisticalCodeIds
      ),
      Arguments.of(
        (BiConsumer<Item, Set<String>>) Item::setYearCaption,
        (Function<Item, Set<String>>) Item::getYearCaption
      )
    );
  }

  private static Stream<Arguments> tagsProvider() {
    var tags1 = new Tags();
    tags1.setTagList(List.of("tag1", "", "tag2", "  ", "tag3"));

    var tags2 = new Tags();
    tags2.setTagList(null);

    var tags3 = new Tags();
    tags3.setTagList(new ArrayList<>());

    var tags4 = new Tags();
    tags4.setTagList(List.of("valid tag", "\t", "\n", "   "));

    return Stream.of(
      Arguments.of(tags1, 3, List.of("tag1", "tag2", "tag3")),
      Arguments.of(null, null, null),
      Arguments.of(tags2, 0, null),
      Arguments.of(tags3, 0, null),
      Arguments.of(tags4, 1, List.of("valid tag"))
    );
  }
}
