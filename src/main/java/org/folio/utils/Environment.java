package org.folio.utils;

public record  Environment() {

  public static String getEnvVar(String key, String defaultVal) {
    return System.getenv().getOrDefault(key, defaultVal);
  }
}
