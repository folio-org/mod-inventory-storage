package org.folio.services.sanitizer.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InstanceSanitizerTest {

  private final InstanceSanitizer sanitizer = new InstanceSanitizer();

  @Test
  void sanitizeShouldHandleNullInstance() {
    assertDoesNotThrow(() -> sanitizer.sanitize(null));
  }

  @ParameterizedTest
  @MethodSource("listFieldsProvider")
  void sanitizeShouldCleanListFields(BiConsumer<Instance, List<String>> setter,
                                     Function<Instance, List<String>> getter,
                                     List<String> input,
                                     List<String> expected) {
    var instance = new Instance();
    setter.accept(instance, input);
    sanitizer.sanitize(instance);
    assertEquals(expected.size(), getter.apply(instance).size());
    assertEquals(expected, getter.apply(instance));
  }

  @ParameterizedTest
  @MethodSource("listFieldsNullProvider")
  void sanitizeShouldHandleNullListFields(BiConsumer<Instance, List<String>> setter,
                                          Function<Instance, List<String>> getter) {
    var instance = new Instance();
    setter.accept(instance, null);
    sanitizer.sanitize(instance);
    assertNotNull(getter.apply(instance));
    assertTrue(getter.apply(instance).isEmpty());
  }

  @ParameterizedTest
  @MethodSource("setFieldsProvider")
  void sanitizeShouldCleanSetFields(BiConsumer<Instance, Set<String>> setter,
                                    Function<Instance, Set<String>> getter,
                                    Set<String> input,
                                    List<String> expectedValues) {
    var instance = new Instance();
    setter.accept(instance, input);
    sanitizer.sanitize(instance);
    assertEquals(expectedValues.size(), getter.apply(instance).size());
    expectedValues.forEach(value -> assertTrue(getter.apply(instance).contains(value)));
  }

  @ParameterizedTest
  @MethodSource("setFieldsNullProvider")
  void sanitizeShouldHandleNullSetFields(BiConsumer<Instance, Set<String>> setter,
                                         Function<Instance, Set<String>> getter) {
    var instance = new Instance();
    setter.accept(instance, null);
    sanitizer.sanitize(instance);
    assertNotNull(getter.apply(instance));
    assertTrue(getter.apply(instance).isEmpty());
  }

  @Test
  void sanitizeShouldCleanAllFieldsSimultaneously() {
    var instance = new Instance();
    instance.setInstanceFormatIds(List.of("format1", "", "format2"));
    instance.setPhysicalDescriptions(List.of("desc1", "  "));
    instance.setLanguages(List.of("", "eng"));
    instance.setAdministrativeNotes(List.of("note1", ""));
    instance.setEditions(new LinkedHashSet<>(List.of("ed1", "")));
    instance.setPublicationRange(new LinkedHashSet<>(List.of("", "2020")));
    instance.setPublicationFrequency(new LinkedHashSet<>(List.of("monthly", "")));
    instance.setNatureOfContentTermIds(new LinkedHashSet<>(List.of("", "term1")));
    instance.setStatisticalCodeIds(new LinkedHashSet<>(List.of("code1", "")));

    sanitizer.sanitize(instance);

    assertEquals(2, instance.getInstanceFormatIds().size());
    assertEquals(1, instance.getPhysicalDescriptions().size());
    assertEquals(1, instance.getLanguages().size());
    assertEquals(1, instance.getAdministrativeNotes().size());
    assertEquals(1, instance.getEditions().size());
    assertEquals(1, instance.getPublicationRange().size());
    assertEquals(1, instance.getPublicationFrequency().size());
    assertEquals(1, instance.getNatureOfContentTermIds().size());
    assertEquals(1, instance.getStatisticalCodeIds().size());
  }

  @Test
  void sanitizeShouldPreserveOrderInLists() {
    var instance = new Instance();
    var languages = new ArrayList<>(List.of("eng", "", "spa", "  ", "fra"));
    instance.setLanguages(languages);
    sanitizer.sanitize(instance);
    assertEquals(List.of("eng", "spa", "fra"), instance.getLanguages());
  }

  @Test
  void sanitizeShouldPreserveOrderInSets() {
    var instance = new Instance();
    var editions = new LinkedHashSet<>(List.of("1st", "", "2nd", "  ", "3rd"));
    instance.setEditions(editions);
    sanitizer.sanitize(instance);
    var editionsList = new ArrayList<>(instance.getEditions());
    assertEquals(3, editionsList.size());
    assertEquals("1st", editionsList.get(0));
    assertEquals("2nd", editionsList.get(1));
    assertEquals("3rd", editionsList.get(2));
  }

  @ParameterizedTest
  @MethodSource("tagsProvider")
  void sanitizeShouldHandleTags(Tags tags, Integer expectedSize, List<String> expectedValues) {
    var instance = new Instance();
    instance.setTags(tags);

    sanitizer.sanitize(instance);

    if (expectedSize == null) {
      assertNull(instance.getTags());
    } else {
      assertNotNull(instance.getTags());
      if (expectedSize == 0) {
        assertTrue(instance.getTags().getTagList().isEmpty());
      } else {
        assertEquals(expectedSize, instance.getTags().getTagList().size());
        assertEquals(expectedValues, instance.getTags().getTagList());
      }
    }
  }

  @Test
  void sanitizeShouldCleanAllFieldsIncludingTags() {
    var instance = new Instance();
    instance.setInstanceFormatIds(List.of("format1", "", "format2"));
    instance.setStatisticalCodeIds(new LinkedHashSet<>(List.of("code1", "", "code2")));

    var tags = new Tags();
    tags.setTagList(List.of("tag1", "", "tag2", "  "));
    instance.setTags(tags);

    sanitizer.sanitize(instance);

    assertEquals(2, instance.getInstanceFormatIds().size());
    assertEquals(2, instance.getStatisticalCodeIds().size());
    assertEquals(2, instance.getTags().getTagList().size());
    assertEquals(List.of("tag1", "tag2"), instance.getTags().getTagList());
  }

  private static Stream<Arguments> listFieldsProvider() {
    return Stream.of(
      instanceFormatIdsArg(),
      physicalDescriptionsArg(),
      languagesArg(),
      administrativeNotesArg()
    );
  }

  private static Arguments instanceFormatIdsArg() {
    return Arguments.of(
      (BiConsumer<Instance, List<String>>) Instance::setInstanceFormatIds,
      (Function<Instance, List<String>>) Instance::getInstanceFormatIds,
      List.of("id1", "", "id2", "  ", "id3"),
      List.of("id1", "id2", "id3")
    );
  }

  private static Arguments physicalDescriptionsArg() {
    return Arguments.of(
      (BiConsumer<Instance, List<String>>) Instance::setPhysicalDescriptions,
      (Function<Instance, List<String>>) Instance::getPhysicalDescriptions,
      List.of("desc1", "", "desc2", "   "),
      List.of("desc1", "desc2")
    );
  }

  private static Arguments languagesArg() {
    return Arguments.of(
      (BiConsumer<Instance, List<String>>) Instance::setLanguages,
      (Function<Instance, List<String>>) Instance::getLanguages,
      List.of("eng", "", "spa", "\t"),
      List.of("eng", "spa")
    );
  }

  private static Arguments administrativeNotesArg() {
    return Arguments.of(
      (BiConsumer<Instance, List<String>>) Instance::setAdministrativeNotes,
      (Function<Instance, List<String>>) Instance::getAdministrativeNotes,
      List.of("note1", "", "note2"),
      List.of("note1", "note2")
    );
  }

  private static Stream<Arguments> listFieldsNullProvider() {
    return Stream.of(
      Arguments.of(
        (BiConsumer<Instance, List<String>>) Instance::setInstanceFormatIds,
        (Function<Instance, List<String>>) Instance::getInstanceFormatIds
      ),
      Arguments.of(
        (BiConsumer<Instance, List<String>>) Instance::setPhysicalDescriptions,
        (Function<Instance, List<String>>) Instance::getPhysicalDescriptions
      ),
      Arguments.of(
        (BiConsumer<Instance, List<String>>) Instance::setLanguages,
        (Function<Instance, List<String>>) Instance::getLanguages
      ),
      Arguments.of(
        (BiConsumer<Instance, List<String>>) Instance::setAdministrativeNotes,
        (Function<Instance, List<String>>) Instance::getAdministrativeNotes
      )
    );
  }

  private static Stream<Arguments> setFieldsProvider() {
    return Stream.of(
      editionsArg(),
      publicationRangeArg(),
      publicationFrequencyArg(),
      natureOfContentTermIdsArg(),
      statisticalCodeIdsArg()
    );
  }

  private static Arguments editionsArg() {
    return Arguments.of(
      (BiConsumer<Instance, Set<String>>) Instance::setEditions,
      (Function<Instance, Set<String>>) Instance::getEditions,
      new LinkedHashSet<>(List.of("1st ed.", "", "2nd ed.", "  ")),
      List.of("1st ed.", "2nd ed.")
    );
  }

  private static Arguments publicationRangeArg() {
    return Arguments.of(
      (BiConsumer<Instance, Set<String>>) Instance::setPublicationRange,
      (Function<Instance, Set<String>>) Instance::getPublicationRange,
      new LinkedHashSet<>(List.of("2000-2010", "", "2011-2020")),
      List.of("2000-2010", "2011-2020")
    );
  }

  private static Arguments publicationFrequencyArg() {
    return Arguments.of(
      (BiConsumer<Instance, Set<String>>) Instance::setPublicationFrequency,
      (Function<Instance, Set<String>>) Instance::getPublicationFrequency,
      new LinkedHashSet<>(List.of("monthly", "", "quarterly")),
      List.of("monthly", "quarterly")
    );
  }

  private static Arguments natureOfContentTermIdsArg() {
    return Arguments.of(
      (BiConsumer<Instance, Set<String>>) Instance::setNatureOfContentTermIds,
      (Function<Instance, Set<String>>) Instance::getNatureOfContentTermIds,
      new LinkedHashSet<>(List.of("term1", "", "term2")),
      List.of("term1", "term2")
    );
  }

  private static Arguments statisticalCodeIdsArg() {
    return Arguments.of(
      (BiConsumer<Instance, Set<String>>) Instance::setStatisticalCodeIds,
      (Function<Instance, Set<String>>) Instance::getStatisticalCodeIds,
      new LinkedHashSet<>(List.of("code1", "", "code2")),
      List.of("code1", "code2")
    );
  }

  private static Stream<Arguments> setFieldsNullProvider() {
    return Stream.of(
      Arguments.of(
        (BiConsumer<Instance, Set<String>>) Instance::setEditions,
        (Function<Instance, Set<String>>) Instance::getEditions
      ),
      Arguments.of(
        (BiConsumer<Instance, Set<String>>) Instance::setPublicationRange,
        (Function<Instance, Set<String>>) Instance::getPublicationRange
      ),
      Arguments.of(
        (BiConsumer<Instance, Set<String>>) Instance::setPublicationFrequency,
        (Function<Instance, Set<String>>) Instance::getPublicationFrequency
      ),
      Arguments.of(
        (BiConsumer<Instance, Set<String>>) Instance::setNatureOfContentTermIds,
        (Function<Instance, Set<String>>) Instance::getNatureOfContentTermIds
      ),
      Arguments.of(
        (BiConsumer<Instance, Set<String>>) Instance::setStatisticalCodeIds,
        (Function<Instance, Set<String>>) Instance::getStatisticalCodeIds
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
