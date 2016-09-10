package org.folio.catalogue.core

import org.junit.Test

class ConfigExamples {
  @Test
  void canOverrideApiBaseAddress() {
    Config.initialiseFrom(["port": 8080, "apiBaseAddress": "http://someaddress.examples:1234/"])

    assert Config.port == 8080
    assert Config.apiBaseAddress == "http://someaddress.examples:1234/"
  }

  @Test
  void apiBaseAddressDefaultedWhenOnlyPortProvided() {
    Config.initialiseFrom(["port": 8080])

    assert Config.port == 8080
    assert Config.apiBaseAddress == "http://localhost:8080/"
  }
}
