package org.folio.utils;

import org.apache.commons.lang3.StringUtils;

public record  Environment() {

  static final String MAX_REQUEST_SIZE = "KAFKA_REINDEX_PRODUCER_MAX_REQUEST_SIZE_BYTES";

  public static String getEnvVar(String key, String defaultVal) {
    return System.getenv().getOrDefault(key, defaultVal);
  }

  public static int getKafkaProducerMaxRequestSize() {
    return Integer.parseInt(StringUtils.firstNonBlank(
      getEnv(MAX_REQUEST_SIZE),
      System.getProperty(MAX_REQUEST_SIZE),
      "10485760")); // 10MB
  }

  static String getEnv(String key) {
    return System.getenv(key);
  }
}
