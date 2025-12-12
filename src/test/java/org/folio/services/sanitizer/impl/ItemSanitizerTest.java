package org.folio.services.sanitizer.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import org.folio.rest.jaxrs.model.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ItemSanitizerTest {

  private ItemSanitizer sanitizer;

  @BeforeEach
  void setUp() {
    sanitizer = new ItemSanitizer();
  }

  @Test
  void sanitizeShouldHandleNullItem() {
    assertDoesNotThrow(() -> sanitizer.sanitize(null));
  }

  @Test
  void sanitizeShouldCleanAdministrativeNotes() {
    var item = new Item();
    item.setAdministrativeNotes(Arrays.asList("note1", "", "note2", "  ", "note3"));

    sanitizer.sanitize(item);

    assertEquals(3, item.getAdministrativeNotes().size());
    assertEquals(Arrays.asList("note1", "note2", "note3"), item.getAdministrativeNotes());
  }

  @Test
  void sanitizeShouldHandleNullAdministrativeNotes() {
    var item = new Item();
    item.setAdministrativeNotes(null);

    sanitizer.sanitize(item);

    assertNotNull(item.getAdministrativeNotes());
    assertTrue(item.getAdministrativeNotes().isEmpty());
  }

  @Test
  void sanitizeShouldHandleEmptyAdministrativeNotes() {
    var item = new Item();
    item.setAdministrativeNotes(new ArrayList<>());

    sanitizer.sanitize(item);

    assertNotNull(item.getAdministrativeNotes());
    assertTrue(item.getAdministrativeNotes().isEmpty());
  }

  @Test
  void sanitizeShouldFilterOutWhitespaceOnlyAdministrativeNotes() {
    var item = new Item();
    item.setAdministrativeNotes(Arrays.asList("valid note", "\t", "\n", "   "));

    sanitizer.sanitize(item);

    assertEquals(1, item.getAdministrativeNotes().size());
    assertEquals("valid note", item.getAdministrativeNotes().getFirst());
  }

  @Test
  void sanitizeShouldCleanStatisticalCodeIds() {
    var item = new Item();
    item.setStatisticalCodeIds(new LinkedHashSet<>(Arrays.asList("code1", "", "code2", "  ", "code3")));

    sanitizer.sanitize(item);

    assertEquals(3, item.getStatisticalCodeIds().size());
    assertTrue(item.getStatisticalCodeIds().contains("code1"));
    assertTrue(item.getStatisticalCodeIds().contains("code2"));
    assertTrue(item.getStatisticalCodeIds().contains("code3"));
  }

  @Test
  void sanitizeShouldHandleNullStatisticalCodeIds() {
    var item = new Item();
    item.setStatisticalCodeIds(null);

    sanitizer.sanitize(item);

    assertNotNull(item.getStatisticalCodeIds());
    assertTrue(item.getStatisticalCodeIds().isEmpty());
  }

  @Test
  void sanitizeShouldHandleEmptyStatisticalCodeIds() {
    var item = new Item();
    item.setStatisticalCodeIds(new LinkedHashSet<>());

    sanitizer.sanitize(item);

    assertNotNull(item.getStatisticalCodeIds());
    assertTrue(item.getStatisticalCodeIds().isEmpty());
  }

  @Test
  void sanitizeShouldFilterOutWhitespaceOnlyStatisticalCodeIds() {
    Item item = new Item();
    item.setStatisticalCodeIds(new LinkedHashSet<>(Arrays.asList("valid-code", "   ", "\t", "\n")));

    sanitizer.sanitize(item);

    assertEquals(1, item.getStatisticalCodeIds().size());
    assertTrue(item.getStatisticalCodeIds().contains("valid-code"));
  }

  @Test
  void sanitizeShouldCleanYearCaption() {
    var item = new Item();
    item.setYearCaption(new LinkedHashSet<>(Arrays.asList("code1", "", "code2", "  ", "code3")));

    sanitizer.sanitize(item);

    assertEquals(3, item.getYearCaption().size());
    assertTrue(item.getYearCaption().contains("code1"));
    assertTrue(item.getYearCaption().contains("code2"));
    assertTrue(item.getYearCaption().contains("code3"));
  }

  @Test
  void sanitizeShouldHandleNullYearCaption() {
    var item = new Item();
    item.setYearCaption(null);

    sanitizer.sanitize(item);

    assertNotNull(item.getYearCaption());
    assertTrue(item.getYearCaption().isEmpty());
  }

  @Test
  void sanitizeShouldHandleEmptyYearCaption() {
    var item = new Item();
    item.setYearCaption(new LinkedHashSet<>());

    sanitizer.sanitize(item);

    assertNotNull(item.getYearCaption());
    assertTrue(item.getYearCaption().isEmpty());
  }

  @Test
  void sanitizeShouldFilterOutWhitespaceOnlyYearCaption() {
    Item item = new Item();
    item.setYearCaption(new LinkedHashSet<>(Arrays.asList("valid-code", "   ", "\t", "\n")));

    sanitizer.sanitize(item);

    assertEquals(1, item.getYearCaption().size());
    assertTrue(item.getYearCaption().contains("valid-code"));
  }

  @Test
  void sanitizeShouldCleanAllFieldsSimultaneously() {
    var item = new Item();
    item.setAdministrativeNotes(Arrays.asList("note1", "", "note2", "  "));
    item.setStatisticalCodeIds(new LinkedHashSet<>(Arrays.asList("code1", "", "code2")));
    item.setYearCaption(new LinkedHashSet<>(Arrays.asList("code1", "", "code2")));

    sanitizer.sanitize(item);

    assertEquals(2, item.getAdministrativeNotes().size());
    assertEquals(Arrays.asList("note1", "note2"), item.getAdministrativeNotes());
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
    var notes = new ArrayList<>(Arrays.asList("first", "", "second", "  ", "third"));
    item.setAdministrativeNotes(notes);

    sanitizer.sanitize(item);

    assertEquals(Arrays.asList("first", "second", "third"), item.getAdministrativeNotes());
  }

  @Test
  void sanitizeShouldPreserveOrderInStatisticalCodeIds() {
    var item = new Item();
    var codes = new LinkedHashSet<>(Arrays.asList("code1", "", "code2", "  ", "code3"));
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
    item.setAdministrativeNotes(Arrays.asList("", "  ", "\t", "\n"));
    item.setStatisticalCodeIds(new LinkedHashSet<>(Arrays.asList("", "  ", "\t")));

    sanitizer.sanitize(item);

    assertTrue(item.getAdministrativeNotes().isEmpty());
    assertTrue(item.getStatisticalCodeIds().isEmpty());
  }

  @Test
  void sanitizeShouldReturnLinkedHashSetForStatisticalCodeIds() {
    Item item = new Item();
    item.setStatisticalCodeIds(new LinkedHashSet<>(Arrays.asList("code1", "code2")));

    sanitizer.sanitize(item);

    assertInstanceOf(LinkedHashSet.class, item.getStatisticalCodeIds());
  }
}
