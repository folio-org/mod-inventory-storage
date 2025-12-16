package org.folio.services.sanitizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Tags;
import org.junit.jupiter.api.Test;

class SanitizerFactoryTest {

  @Test
  void getSanitizerShouldReturnInstanceSanitizer() {
    Sanitizer<Instance> sanitizer = SanitizerFactory.getSanitizer(Instance.class);
    assertNotNull(sanitizer);
  }

  @Test
  void getSanitizerShouldReturnItemSanitizer() {
    Sanitizer<Item> sanitizer = SanitizerFactory.getSanitizer(Item.class);
    assertNotNull(sanitizer);
  }

  @Test
  void getSanitizerShouldReturnHoldingsRecordSanitizer() {
    Sanitizer<HoldingsRecord> sanitizer = SanitizerFactory.getSanitizer(HoldingsRecord.class);
    assertNotNull(sanitizer);
  }

  @Test
  void getSanitizerShouldReturnTagsSanitizer() {
    Sanitizer<Tags> sanitizer = SanitizerFactory.getSanitizer(Tags.class);
    assertNotNull(sanitizer);
  }

  @Test
  void getSanitizerShouldThrowExceptionForUnsupportedClass() {
    IllegalArgumentException exception = assertThrows(
      IllegalArgumentException.class,
      () -> SanitizerFactory.getSanitizer(String.class)
    );
    assertEquals("Sanitizer not found for class java.lang.String", exception.getMessage());
  }

  @Test
  void getSanitizerShouldReturnSameSanitizerInstanceForSameClass() {
    Sanitizer<Instance> sanitizer1 = SanitizerFactory.getSanitizer(Instance.class);
    Sanitizer<Instance> sanitizer2 = SanitizerFactory.getSanitizer(Instance.class);
    assertEquals(sanitizer1, sanitizer2);
  }

  @Test
  void constructorShouldThrowUnsupportedOperationException() throws NoSuchMethodException {
    var constructor = SanitizerFactory.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    var exception = assertThrows(Exception.class, constructor::newInstance);
    assertInstanceOf(UnsupportedOperationException.class, exception.getCause());
    assertEquals("Factory class", exception.getCause().getMessage());
  }
}
