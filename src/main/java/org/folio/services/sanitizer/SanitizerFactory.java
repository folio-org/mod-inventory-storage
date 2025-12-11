package org.folio.services.sanitizer;

import java.util.HashMap;
import java.util.Map;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Item;
import org.folio.services.sanitizer.impl.HoldingsRecordSanitizer;
import org.folio.services.sanitizer.impl.InstanceSanitizer;
import org.folio.services.sanitizer.impl.ItemSanitizer;

public final class SanitizerFactory {

  private static final Map<Class<?>, Sanitizer<?>> SANITIZERS = createSanitizers();

  private SanitizerFactory() {
    throw new UnsupportedOperationException("Factory class");
  }

  private static Map<Class<?>, Sanitizer<?>> createSanitizers() {
    Map<Class<?>, Sanitizer<?>> map = new HashMap<>();
    map.put(Instance.class, new InstanceSanitizer());
    map.put(Item.class, new ItemSanitizer());
    map.put(HoldingsRecord.class, new HoldingsRecordSanitizer());
    return Map.copyOf(map);
  }

  @SuppressWarnings("unchecked")
  public static <T> Sanitizer<T> getSanitizer(Class<T> clazz) {
    var sanitizer = SANITIZERS.get(clazz);
    if (sanitizer == null) {
      throw new IllegalArgumentException("Sanitizer not found for class " + clazz.getName());
    }
    return (Sanitizer<T>) sanitizer;
  }
}
