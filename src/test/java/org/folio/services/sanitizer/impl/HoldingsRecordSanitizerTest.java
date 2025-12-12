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
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HoldingsRecordSanitizerTest {

  private final HoldingsRecordSanitizer sanitizer = new HoldingsRecordSanitizer();

  @Test
  void sanitizeShouldHandleNullHoldingsRecord() {
    assertDoesNotThrow(() -> sanitizer.sanitize(null));
  }

  @ParameterizedTest
  @MethodSource("administrativeNotesProvider")
  void sanitizeShouldHandleAdministrativeNotes(List<String> input, List<String> expected) {
    HoldingsRecord holdings = new HoldingsRecord();
    holdings.setAdministrativeNotes(input);
    sanitizer.sanitize(holdings);

    assertNotNull(holdings.getAdministrativeNotes());
    if (expected.isEmpty()) {
      assertTrue(holdings.getAdministrativeNotes().isEmpty());
    } else {
      assertEquals(expected, holdings.getAdministrativeNotes());
    }
  }

  @ParameterizedTest
  @MethodSource("setFieldsProvider")
  void sanitizeShouldCleanSetFields(BiConsumer<HoldingsRecord, Set<String>> setter,
                                    Function<HoldingsRecord, Set<String>> getter,
                                    Set<String> input,
                                    List<String> expectedValues) {
    HoldingsRecord holdings = new HoldingsRecord();
    setter.accept(holdings, input);
    sanitizer.sanitize(holdings);

    assertNotNull(getter.apply(holdings));
    if (expectedValues.isEmpty()) {
      assertTrue(getter.apply(holdings).isEmpty());
    } else {
      assertEquals(expectedValues.size(), getter.apply(holdings).size());
      expectedValues.forEach(value -> assertTrue(getter.apply(holdings).contains(value)));
    }
  }

  @ParameterizedTest
  @MethodSource("setFieldsNullProvider")
  void sanitizeShouldHandleNullSetFields(BiConsumer<HoldingsRecord, Set<String>> setter,
                                         Function<HoldingsRecord, Set<String>> getter) {
    HoldingsRecord holdings = new HoldingsRecord();
    setter.accept(holdings, null);
    sanitizer.sanitize(holdings);
    assertNotNull(getter.apply(holdings));
    assertTrue(getter.apply(holdings).isEmpty());
  }

  @Test
  void sanitizeShouldCleanAllFieldsSimultaneously() {
    HoldingsRecord holdings = new HoldingsRecord();
    holdings.setAdministrativeNotes(List.of("note1", "", "note2", "  "));
    holdings.setStatisticalCodeIds(new LinkedHashSet<>(List.of("code1", "", "code2")));
    holdings.setFormerIds(new LinkedHashSet<>(List.of("id1", "", "id2")));

    sanitizer.sanitize(holdings);

    assertEquals(2, holdings.getAdministrativeNotes().size());
    assertEquals(List.of("note1", "note2"), holdings.getAdministrativeNotes());
    assertEquals(2, holdings.getStatisticalCodeIds().size());
    assertTrue(holdings.getStatisticalCodeIds().contains("code1"));
    assertTrue(holdings.getStatisticalCodeIds().contains("code2"));
    assertEquals(2, holdings.getFormerIds().size());
    assertTrue(holdings.getFormerIds().contains("id1"));
    assertTrue(holdings.getFormerIds().contains("id2"));
  }

  @Test
  void sanitizeShouldPreserveOrderInAdministrativeNotes() {
    HoldingsRecord holdings = new HoldingsRecord();
    List<String> notes = new ArrayList<>(List.of("first", "", "second", "  ", "third"));
    holdings.setAdministrativeNotes(notes);
    sanitizer.sanitize(holdings);
    assertEquals(List.of("first", "second", "third"), holdings.getAdministrativeNotes());
  }

  @Test
  void sanitizeShouldPreserveOrderInStatisticalCodeIds() {
    HoldingsRecord holdings = new HoldingsRecord();
    LinkedHashSet<String> codes = new LinkedHashSet<>(List.of("code1", "", "code2", "  ", "code3"));
    holdings.setStatisticalCodeIds(codes);
    sanitizer.sanitize(holdings);
    List<String> codesList = new ArrayList<>(holdings.getStatisticalCodeIds());
    assertEquals(3, codesList.size());
    assertEquals("code1", codesList.get(0));
    assertEquals("code2", codesList.get(1));
    assertEquals("code3", codesList.get(2));
  }

  @Test
  void sanitizeShouldPreserveOrderInFormerIds() {
    HoldingsRecord holdings = new HoldingsRecord();
    LinkedHashSet<String> ids = new LinkedHashSet<>(List.of("id1", "", "id2", "  ", "id3"));
    holdings.setFormerIds(ids);
    sanitizer.sanitize(holdings);
    List<String> idsList = new ArrayList<>(holdings.getFormerIds());
    assertEquals(3, idsList.size());
    assertEquals("id1", idsList.get(0));
    assertEquals("id2", idsList.get(1));
    assertEquals("id3", idsList.get(2));
  }

  @Test
  void sanitizeShouldHandleAllBlankValues() {
    HoldingsRecord holdings = new HoldingsRecord();
    holdings.setAdministrativeNotes(List.of("", "  ", "\t", "\n"));
    holdings.setStatisticalCodeIds(new LinkedHashSet<>(List.of("", "  ", "\t")));
    holdings.setFormerIds(new LinkedHashSet<>(List.of("", "\n", "  ")));

    sanitizer.sanitize(holdings);

    assertTrue(holdings.getAdministrativeNotes().isEmpty());
    assertTrue(holdings.getStatisticalCodeIds().isEmpty());
    assertTrue(holdings.getFormerIds().isEmpty());
  }

  @Test
  void sanitizeShouldReturnLinkedHashSetForStatisticalCodeIds() {
    HoldingsRecord holdings = new HoldingsRecord();
    holdings.setStatisticalCodeIds(new LinkedHashSet<>(List.of("code1", "code2")));
    sanitizer.sanitize(holdings);
    assertInstanceOf(LinkedHashSet.class, holdings.getStatisticalCodeIds());
  }

  @Test
  void sanitizeShouldReturnLinkedHashSetForFormerIds() {
    HoldingsRecord holdings = new HoldingsRecord();
    holdings.setFormerIds(new LinkedHashSet<>(List.of("id1", "id2")));
    sanitizer.sanitize(holdings);
    assertInstanceOf(LinkedHashSet.class, holdings.getFormerIds());
  }

  @ParameterizedTest
  @MethodSource("tagsProvider")
  void sanitizeShouldHandleTags(Tags tags, Integer expectedSize, List<String> expectedValues) {
    var holdings = new HoldingsRecord();
    holdings.setTags(tags);

    sanitizer.sanitize(holdings);

    if (expectedSize == null) {
      assertNull(holdings.getTags());
    } else {
      assertNotNull(holdings.getTags());
      if (expectedSize == 0) {
        assertTrue(holdings.getTags().getTagList().isEmpty());
      } else {
        assertEquals(expectedSize, holdings.getTags().getTagList().size());
        assertEquals(expectedValues, holdings.getTags().getTagList());
      }
    }
  }

  @Test
  void sanitizeShouldCleanAllFieldsIncludingTags() {
    var holdings = new HoldingsRecord();
    holdings.setAdministrativeNotes(List.of("note1", "", "note2", "  "));
    holdings.setStatisticalCodeIds(new LinkedHashSet<>(List.of("code1", "", "code2")));
    holdings.setFormerIds(new LinkedHashSet<>(List.of("id1", "", "id2")));

    var tags = new Tags();
    tags.setTagList(List.of("tag1", "", "tag2", "  "));
    holdings.setTags(tags);

    sanitizer.sanitize(holdings);

    assertEquals(2, holdings.getAdministrativeNotes().size());
    assertEquals(2, holdings.getStatisticalCodeIds().size());
    assertEquals(2, holdings.getFormerIds().size());
    assertEquals(2, holdings.getTags().getTagList().size());
    assertEquals(List.of("tag1", "tag2"), holdings.getTags().getTagList());
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
      formerIdsArg1(),
      formerIdsArg2(),
      formerIdsArg3()
    );
  }

  private static Arguments statisticalCodeIdsArg1() {
    return Arguments.of(
      (BiConsumer<HoldingsRecord, Set<String>>) HoldingsRecord::setStatisticalCodeIds,
      (Function<HoldingsRecord, Set<String>>) HoldingsRecord::getStatisticalCodeIds,
      new LinkedHashSet<>(List.of("code1", "", "code2", "  ", "code3")),
      List.of("code1", "code2", "code3")
    );
  }

  private static Arguments statisticalCodeIdsArg2() {
    return Arguments.of(
      (BiConsumer<HoldingsRecord, Set<String>>) HoldingsRecord::setStatisticalCodeIds,
      (Function<HoldingsRecord, Set<String>>) HoldingsRecord::getStatisticalCodeIds,
      new LinkedHashSet<>(),
      List.of()
    );
  }

  private static Arguments statisticalCodeIdsArg3() {
    return Arguments.of(
      (BiConsumer<HoldingsRecord, Set<String>>) HoldingsRecord::setStatisticalCodeIds,
      (Function<HoldingsRecord, Set<String>>) HoldingsRecord::getStatisticalCodeIds,
      new LinkedHashSet<>(List.of("valid-code", "   ", "\t", "\n")),
      List.of("valid-code")
    );
  }

  private static Arguments formerIdsArg1() {
    return Arguments.of(
      (BiConsumer<HoldingsRecord, Set<String>>) HoldingsRecord::setFormerIds,
      (Function<HoldingsRecord, Set<String>>) HoldingsRecord::getFormerIds,
      new LinkedHashSet<>(List.of("id1", "", "id2", "  ", "id3")),
      List.of("id1", "id2", "id3")
    );
  }

  private static Arguments formerIdsArg2() {
    return Arguments.of(
      (BiConsumer<HoldingsRecord, Set<String>>) HoldingsRecord::setFormerIds,
      (Function<HoldingsRecord, Set<String>>) HoldingsRecord::getFormerIds,
      new LinkedHashSet<>(),
      List.of()
    );
  }

  private static Arguments formerIdsArg3() {
    return Arguments.of(
      (BiConsumer<HoldingsRecord, Set<String>>) HoldingsRecord::setFormerIds,
      (Function<HoldingsRecord, Set<String>>) HoldingsRecord::getFormerIds,
      new LinkedHashSet<>(List.of("valid-id", "   ", "\t", "\n")),
      List.of("valid-id")
    );
  }

  private static Stream<Arguments> setFieldsNullProvider() {
    return Stream.of(
      Arguments.of(
        (BiConsumer<HoldingsRecord, Set<String>>) HoldingsRecord::setStatisticalCodeIds,
        (Function<HoldingsRecord, Set<String>>) HoldingsRecord::getStatisticalCodeIds
      ),
      Arguments.of(
        (BiConsumer<HoldingsRecord, Set<String>>) HoldingsRecord::setFormerIds,
        (Function<HoldingsRecord, Set<String>>) HoldingsRecord::getFormerIds
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
