package org.folio.rest.support;

import static org.folio.rest.support.CollectionUtil.deepCopy;
import static org.junit.Assert.assertThrows;

import java.util.List;
import org.junit.Test;

public class CollectionUtilTest {
  @Test
  public void shouldThrowIllegalArgumentExceptionWhenCannotSerializeToJson() {
    var collection = List.of(new Object());
    assertThrows(IllegalArgumentException.class, () -> deepCopy(collection, Object.class));
  }
}
