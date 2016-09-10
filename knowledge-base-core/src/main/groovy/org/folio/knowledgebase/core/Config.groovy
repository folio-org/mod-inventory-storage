package org.folio.knowledgebase.core

import org.apache.commons.lang.StringUtils

class Config {
  private static Integer port
  private static String apiBaseAddress

  static Integer getPort() {
    port
  }

  static String getApiBaseAddress() {
    apiBaseAddress
  }

  static def initialiseFrom(Map map) {
    port = map.port
    apiBaseAddress = map.apiBaseAddress ?: "http://localhost:${port}/"
  }

  def static String toString() {
    def builder = new StringBuilder()

    builder.append("Config\n")
      .append(StringUtils.repeat("-", 10))
      .append("\n")
      .append("Port: ${port}\n")
      .append("API Base Address: ${apiBaseAddress}\n")

    builder.toString()
  }
}
