package catalogue.core

class Config {
  private static Integer port;

  static Integer getPort() {
    port
  }

  static String getApiBaseAddress() {
    "http://localhost:${port}/"
  }

  static def initialiseFrom(Map<String, Object> map) {
    port = map.port
  }
}
