package org.folio.services.sanitizer.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import org.folio.rest.jaxrs.model.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InstanceSanitizerTest {

  private InstanceSanitizer sanitizer;

  @BeforeEach
  void setUp() {
    sanitizer = new InstanceSanitizer();
  }

  @Test
  void sanitizeShouldHandleNullInstance() {
    assertDoesNotThrow(() -> sanitizer.sanitize(null));
  }

  @Test
  void sanitizeShouldCleanInstanceFormatIds() {
    var instance = new Instance();
    instance.setInstanceFormatIds(Arrays.asList("id1", "", "id2", "  ", "id3"));
    sanitizer.sanitize(instance);
    assertEquals(3, instance.getInstanceFormatIds().size());
    assertEquals(Arrays.asList("id1", "id2", "id3"), instance.getInstanceFormatIds());
  }

  @Test
  void sanitizeShouldHandleNullInstanceFormatIds() {
    var instance = new Instance();
    instance.setInstanceFormatIds(null);
    sanitizer.sanitize(instance);
    assertNotNull(instance.getInstanceFormatIds());
    assertTrue(instance.getInstanceFormatIds().isEmpty());
  }

  @Test
  void sanitizeShouldCleanPhysicalDescriptions() {
    var instance = new Instance();
    instance.setPhysicalDescriptions(Arrays.asList("desc1", "", "desc2", "   "));
    sanitizer.sanitize(instance);
    assertEquals(2, instance.getPhysicalDescriptions().size());
    assertEquals(Arrays.asList("desc1", "desc2"), instance.getPhysicalDescriptions());
  }

  @Test
  void sanitizeShouldHandleNullPhysicalDescriptions() {
    var instance = new Instance();
    instance.setPhysicalDescriptions(null);
    sanitizer.sanitize(instance);
    assertNotNull(instance.getPhysicalDescriptions());
    assertTrue(instance.getPhysicalDescriptions().isEmpty());
  }

  @Test
  void sanitizeShouldCleanLanguages() {
    var instance = new Instance();
    instance.setLanguages(Arrays.asList("eng", "", "spa", "\t"));
    sanitizer.sanitize(instance);
    assertEquals(2, instance.getLanguages().size());
    assertEquals(Arrays.asList("eng", "spa"), instance.getLanguages());
  }

  @Test
  void sanitizeShouldHandleNullLanguages() {
    var instance = new Instance();
    instance.setLanguages(null);
    sanitizer.sanitize(instance);
    assertNotNull(instance.getLanguages());
    assertTrue(instance.getLanguages().isEmpty());
  }

  @Test
  void sanitizeShouldCleanAdministrativeNotes() {
    var instance = new Instance();
    instance.setAdministrativeNotes(Arrays.asList("note1", "", "note2"));
    sanitizer.sanitize(instance);
    assertEquals(2, instance.getAdministrativeNotes().size());
    assertEquals(Arrays.asList("note1", "note2"), instance.getAdministrativeNotes());
  }

  @Test
  void sanitizeShouldHandleNullAdministrativeNotes() {
    var instance = new Instance();
    instance.setAdministrativeNotes(null);
    sanitizer.sanitize(instance);
    assertNotNull(instance.getAdministrativeNotes());
    assertTrue(instance.getAdministrativeNotes().isEmpty());
  }

  @Test
  void sanitizeShouldCleanEditions() {
    var instance = new Instance();
    instance.setEditions(new LinkedHashSet<>(Arrays.asList("1st ed.", "", "2nd ed.", "  ")));
    sanitizer.sanitize(instance);
    assertEquals(2, instance.getEditions().size());
    assertTrue(instance.getEditions().contains("1st ed."));
    assertTrue(instance.getEditions().contains("2nd ed."));
  }

  @Test
  void sanitizeShouldHandleNullEditions() {
    var instance = new Instance();
    instance.setEditions(null);
    sanitizer.sanitize(instance);
    assertNotNull(instance.getEditions());
    assertTrue(instance.getEditions().isEmpty());
  }

  @Test
  void sanitizeShouldCleanPublicationRange() {
    var instance = new Instance();
    instance.setPublicationRange(new LinkedHashSet<>(Arrays.asList("2000-2010", "", "2011-2020")));
    sanitizer.sanitize(instance);
    assertEquals(2, instance.getPublicationRange().size());
    assertTrue(instance.getPublicationRange().contains("2000-2010"));
    assertTrue(instance.getPublicationRange().contains("2011-2020"));
  }

  @Test
  void sanitizeShouldHandleNullPublicationRange() {
    var instance = new Instance();
    instance.setPublicationRange(null);
    sanitizer.sanitize(instance);
    assertNotNull(instance.getPublicationRange());
    assertTrue(instance.getPublicationRange().isEmpty());
  }

  @Test
  void sanitizeShouldCleanPublicationFrequency() {
    var instance = new Instance();
    instance.setPublicationFrequency(new LinkedHashSet<>(Arrays.asList("monthly", "", "quarterly")));
    sanitizer.sanitize(instance);
    assertEquals(2, instance.getPublicationFrequency().size());
    assertTrue(instance.getPublicationFrequency().contains("monthly"));
    assertTrue(instance.getPublicationFrequency().contains("quarterly"));
  }

  @Test
  void sanitizeShouldHandleNullPublicationFrequency() {
    var instance = new Instance();
    instance.setPublicationFrequency(null);
    sanitizer.sanitize(instance);
    assertNotNull(instance.getPublicationFrequency());
    assertTrue(instance.getPublicationFrequency().isEmpty());
  }

  @Test
  void sanitizeShouldCleanNatureOfContentTermIds() {
    var instance = new Instance();
    instance.setNatureOfContentTermIds(new LinkedHashSet<>(Arrays.asList("term1", "", "term2")));
    sanitizer.sanitize(instance);
    assertEquals(2, instance.getNatureOfContentTermIds().size());
    assertTrue(instance.getNatureOfContentTermIds().contains("term1"));
    assertTrue(instance.getNatureOfContentTermIds().contains("term2"));
  }

  @Test
  void sanitizeShouldHandleNullNatureOfContentTermIds() {
    var instance = new Instance();
    instance.setNatureOfContentTermIds(null);
    sanitizer.sanitize(instance);
    assertNotNull(instance.getNatureOfContentTermIds());
    assertTrue(instance.getNatureOfContentTermIds().isEmpty());
  }

  @Test
  void sanitizeShouldCleanStatisticalCodeIds() {
    var instance = new Instance();
    instance.setStatisticalCodeIds(new LinkedHashSet<>(Arrays.asList("code1", "", "code2")));
    sanitizer.sanitize(instance);
    assertEquals(2, instance.getStatisticalCodeIds().size());
    assertTrue(instance.getStatisticalCodeIds().contains("code1"));
    assertTrue(instance.getStatisticalCodeIds().contains("code2"));
  }

  @Test
  void sanitizeShouldHandleNullStatisticalCodeIds() {
    var instance = new Instance();
    instance.setStatisticalCodeIds(null);
    sanitizer.sanitize(instance);
    assertNotNull(instance.getStatisticalCodeIds());
    assertTrue(instance.getStatisticalCodeIds().isEmpty());
  }

  @Test
  void sanitizeShouldCleanAllFieldsSimultaneously() {
    var instance = new Instance();
    instance.setInstanceFormatIds(Arrays.asList("format1", "", "format2"));
    instance.setPhysicalDescriptions(Arrays.asList("desc1", "  "));
    instance.setLanguages(Arrays.asList("", "eng"));
    instance.setAdministrativeNotes(Arrays.asList("note1", ""));
    instance.setEditions(new LinkedHashSet<>(Arrays.asList("ed1", "")));
    instance.setPublicationRange(new LinkedHashSet<>(Arrays.asList("", "2020")));
    instance.setPublicationFrequency(new LinkedHashSet<>(Arrays.asList("monthly", "")));
    instance.setNatureOfContentTermIds(new LinkedHashSet<>(Arrays.asList("", "term1")));
    instance.setStatisticalCodeIds(new LinkedHashSet<>(Arrays.asList("code1", "")));

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
    var languages = new ArrayList<>(Arrays.asList("eng", "", "spa", "  ", "fra"));
    instance.setLanguages(languages);
    sanitizer.sanitize(instance);
    assertEquals(Arrays.asList("eng", "spa", "fra"), instance.getLanguages());
  }

  @Test
  void sanitizeShouldPreserveOrderInSets() {
    var instance = new Instance();
    var editions = new LinkedHashSet<>(Arrays.asList("1st", "", "2nd", "  ", "3rd"));
    instance.setEditions(editions);
    sanitizer.sanitize(instance);
    var editionsList = new ArrayList<>(instance.getEditions());
    assertEquals(3, editionsList.size());
    assertEquals("1st", editionsList.get(0));
    assertEquals("2nd", editionsList.get(1));
    assertEquals("3rd", editionsList.get(2));
  }
}
