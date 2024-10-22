package org.folio.utils;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ObjectConverterUtils {

  private static final Logger log = LoggerFactory.getLogger(ObjectConverterUtils.class);
  private static final Gson GSON = new Gson();

  private ObjectConverterUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static <T> T convertObject(Object source, Class<T> targetClass) {
    try {
      log.debug("convertObject:: convert from {} to {}",
        source.getClass().getSimpleName(), targetClass.getSimpleName());

      var sourceJson = GSON.toJson(source);
      return GSON.fromJson(sourceJson, targetClass);
    } catch (Exception e) {
      throw new IllegalArgumentException(
        String.format("Failed to convert %s to %s: %s",
          source.getClass().getSimpleName(), targetClass.getSimpleName(), e.getMessage()), e);
    }
  }
}
