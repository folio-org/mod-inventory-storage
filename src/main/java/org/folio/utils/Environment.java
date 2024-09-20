package org.folio.utils;

public class Environment {

  private Environment() {
  }

  public static String getEnvVar(String key, String defaultVal) {
    return System.getenv().getOrDefault(key, defaultVal);
  }
}
