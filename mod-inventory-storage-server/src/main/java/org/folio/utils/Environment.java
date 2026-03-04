package org.folio.utils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record Environment() {

  static final String MAX_REQUEST_SIZE = "KAFKA_REINDEX_PRODUCER_MAX_REQUEST_SIZE_BYTES";

  public static String getValue(@NonNull String key) {
    return getValue(key, null);
  }

  public static String getValue(@NonNull String key, @Nullable String defaultValue) {
    return System.getProperty(key, System.getenv().getOrDefault(key, defaultValue));
  }

  public static String getValueOrEmpty(@NonNull String key) {
    return getValue(key, "");
  }

  public static String getValueOrFail(@NonNull String key) {
    var value = getValue(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Required S3 configuration property is missing: " + key);
    }
    return value;
  }

  public static Boolean getBoolValue(@NonNull String key, @Nullable Boolean defaultValue) {
    var value = getValue(key);
    if (value == null) {
      return defaultValue;
    }
    return Boolean.parseBoolean(value);
  }

  public static int getIntValue(@NonNull String key, @Nullable Integer defaultValue) {
    var value = getValue(key);
    if (value == null) {
      if (defaultValue == null) {
        throw new IllegalStateException("Required configuration property is missing: " + key);
      }
      return defaultValue;
    }
    return Integer.parseInt(value);
  }

  public static int getKafkaProducerMaxRequestSize() {
    return getIntValue(MAX_REQUEST_SIZE, 10485760); // 10MB
  }
}
