package org.folio.services.sanitizer.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HoldingsRecordSanitizerTest {

  private HoldingsRecordSanitizer sanitizer;

  @BeforeEach
  void setUp() {
    sanitizer = new HoldingsRecordSanitizer();
  }

  @Test
  void sanitizeShouldHandleNullHoldingsRecord() {
    assertDoesNotThrow(() -> sanitizer.sanitize(null));
  }

  @Test
  void sanitizeShouldCleanAdministrativeNotes() {
    HoldingsRecord holdings = new HoldingsRecord();
    holdings.setAdministrativeNotes(Arrays.asList("note1", "", "note2", "  ", "note3"));
    sanitizer.sanitize(holdings);
    assertEquals(3, holdings.getAdministrativeNotes().size());
    assertEquals(Arrays.asList("note1", "note2", "note3"), holdings.getAdministrativeNotes());
  }

  @Test
  void sanitizeShouldHandleNullAdministrativeNotes() {
    HoldingsRecord holdings = new HoldingsRecord();
    holdings.setAdministrativeNotes(null);
    sanitizer.sanitize(holdings);
    assertNotNull(holdings.getAdministrativeNotes());
    assertTrue(holdings.getAdministrativeNotes().isEmpty());
  }

  @Test
  void sanitizeShouldHandleEmptyAdministrativeNotes() {
    HoldingsRecord holdings = new HoldingsRecord();
    holdings.setAdministrativeNotes(new ArrayList<>());
    sanitizer.sanitize(holdings);
    assertNotNull(holdings.getAdministrativeNotes());
    assertTrue(holdings.getAdministrativeNotes().isEmpty());
  }

  @Test
  void sanitizeShouldFilterOutWhitespaceOnlyAdministrativeNotes() {
    HoldingsRecord holdings = new HoldingsRecord();
    holdings.setAdministrativeNotes(Arrays.asList("valid note", "\t", "\n", "   "));
    sanitizer.sanitize(holdings);
    assertEquals(1, holdings.getAdministrativeNotes().size());
    assertEquals("valid note", holdings.getAdministrativeNotes().getFirst());
  }

  @Test
  void sanitizeShouldCleanStatisticalCodeIds() {
    HoldingsRecord holdings = new HoldingsRecord();
    holdings.setStatisticalCodeIds(new LinkedHashSet<>(Arrays.asList("code1", "", "code2", "  ", "code3")));
    sanitizer.sanitize(holdings);
    assertEquals(3, holdings.getStatisticalCodeIds().size());
    assertTrue(holdings.getStatisticalCodeIds().contains("code1"));
    assertTrue(holdings.getStatisticalCodeIds().contains("code2"));
    assertTrue(holdings.getStatisticalCodeIds().contains("code3"));
  }

  @Test
  void sanitizeShouldHandleNullStatisticalCodeIds() {
    HoldingsRecord holdings = new HoldingsRecord();
    holdings.setStatisticalCodeIds(null);
    sanitizer.sanitize(holdings);
    assertNotNull(holdings.getStatisticalCodeIds());
    assertTrue(holdings.getStatisticalCodeIds().isEmpty());
  }

  @Test
  void sanitizeShouldHandleEmptyStatisticalCodeIds() {
    HoldingsRecord holdings = new HoldingsRecord();
    holdings.setStatisticalCodeIds(new LinkedHashSet<>());
    sanitizer.sanitize(holdings);
    assertNotNull(holdings.getStatisticalCodeIds());
    assertTrue(holdings.getStatisticalCodeIds().isEmpty());
  }

  @Test
  void sanitizeShouldFilterOutWhitespaceOnlyStatisticalCodeIds() {
    HoldingsRecord holdings = new HoldingsRecord();
    holdings.setStatisticalCodeIds(new LinkedHashSet<>(Arrays.asList("valid-code", "   ", "\t", "\n")));
    sanitizer.sanitize(holdings);
    assertEquals(1, holdings.getStatisticalCodeIds().size());
    assertTrue(holdings.getStatisticalCodeIds().contains("valid-code"));
  }

  @Test
  void sanitizeShouldCleanFormerIds() {
    HoldingsRecord holdings = new HoldingsRecord();
    holdings.setFormerIds(new LinkedHashSet<>(Arrays.asList("id1", "", "id2", "  ", "id3")));
    sanitizer.sanitize(holdings);
    assertEquals(3, holdings.getFormerIds().size());
    assertTrue(holdings.getFormerIds().contains("id1"));
    assertTrue(holdings.getFormerIds().contains("id2"));
    assertTrue(holdings.getFormerIds().contains("id3"));
  }

  @Test
  void sanitizeShouldHandleNullFormerIds() {
    HoldingsRecord holdings = new HoldingsRecord();
    holdings.setFormerIds(null);
    sanitizer.sanitize(holdings);
    assertNotNull(holdings.getFormerIds());
    assertTrue(holdings.getFormerIds().isEmpty());
  }

  @Test
  void sanitizeShouldHandleEmptyFormerIds() {
    HoldingsRecord holdings = new HoldingsRecord();
    holdings.setFormerIds(new LinkedHashSet<>());
    sanitizer.sanitize(holdings);
    assertNotNull(holdings.getFormerIds());
    assertTrue(holdings.getFormerIds().isEmpty());
  }

  @Test
  void sanitizeShouldFilterOutWhitespaceOnlyFormerIds() {
    HoldingsRecord holdings = new HoldingsRecord();
    holdings.setFormerIds(new LinkedHashSet<>(Arrays.asList("valid-id", "   ", "\t", "\n")));
    sanitizer.sanitize(holdings);
    assertEquals(1, holdings.getFormerIds().size());
    assertTrue(holdings.getFormerIds().contains("valid-id"));
  }

  @Test
  void sanitizeShouldCleanAllFieldsSimultaneously() {
    HoldingsRecord holdings = new HoldingsRecord();
    holdings.setAdministrativeNotes(Arrays.asList("note1", "", "note2", "  "));
    holdings.setStatisticalCodeIds(new LinkedHashSet<>(Arrays.asList("code1", "", "code2")));
    holdings.setFormerIds(new LinkedHashSet<>(Arrays.asList("id1", "", "id2")));
    
    sanitizer.sanitize(holdings);
    
    assertEquals(2, holdings.getAdministrativeNotes().size());
    assertEquals(Arrays.asList("note1", "note2"), holdings.getAdministrativeNotes());
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
    List<String> notes = new ArrayList<>(Arrays.asList("first", "", "second", "  ", "third"));
    holdings.setAdministrativeNotes(notes);
    sanitizer.sanitize(holdings);
    assertEquals(Arrays.asList("first", "second", "third"), holdings.getAdministrativeNotes());
  }

  @Test
  void sanitizeShouldPreserveOrderInStatisticalCodeIds() {
    HoldingsRecord holdings = new HoldingsRecord();
    LinkedHashSet<String> codes = new LinkedHashSet<>(Arrays.asList("code1", "", "code2", "  ", "code3"));
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
    LinkedHashSet<String> ids = new LinkedHashSet<>(Arrays.asList("id1", "", "id2", "  ", "id3"));
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
    holdings.setAdministrativeNotes(Arrays.asList("", "  ", "\t", "\n"));
    holdings.setStatisticalCodeIds(new LinkedHashSet<>(Arrays.asList("", "  ", "\t")));
    holdings.setFormerIds(new LinkedHashSet<>(Arrays.asList("", "\n", "  ")));
    
    sanitizer.sanitize(holdings);
    
    assertTrue(holdings.getAdministrativeNotes().isEmpty());
    assertTrue(holdings.getStatisticalCodeIds().isEmpty());
    assertTrue(holdings.getFormerIds().isEmpty());
  }

  @Test
  void sanitizeShouldReturnLinkedHashSetForStatisticalCodeIds() {
    HoldingsRecord holdings = new HoldingsRecord();
    holdings.setStatisticalCodeIds(new LinkedHashSet<>(Arrays.asList("code1", "code2")));
    sanitizer.sanitize(holdings);
    assertInstanceOf(LinkedHashSet.class, holdings.getStatisticalCodeIds());
  }

  @Test
  void sanitizeShouldReturnLinkedHashSetForFormerIds() {
    HoldingsRecord holdings = new HoldingsRecord();
    holdings.setFormerIds(new LinkedHashSet<>(Arrays.asList("id1", "id2")));
    sanitizer.sanitize(holdings);
    assertInstanceOf(LinkedHashSet.class, holdings.getFormerIds());
  }
}
