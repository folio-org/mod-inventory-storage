package org.folio;

public class Environment {
  private Environment() { }

  public static String getEnvironmentName() {
    return System.getenv().getOrDefault("ENV", "folio");
  }
}
