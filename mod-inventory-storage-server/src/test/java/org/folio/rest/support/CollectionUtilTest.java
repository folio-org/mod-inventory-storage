package org.folio.rest.support;

import static org.folio.rest.support.CollectionUtil.deepCopy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class CollectionUtilTest {
  @Test
  void shouldThrowIllegalArgumentExceptionWhenCannotSerializeToJson() {
    var collection = List.of(new Object());
    assertThrows(IllegalArgumentException.class, () -> deepCopy(collection, Object.class));
  }
}
