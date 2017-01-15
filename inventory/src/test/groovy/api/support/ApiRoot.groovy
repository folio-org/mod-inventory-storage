package api.support

import api.ApiTestSuite

class ApiRoot {
  private static String inventory() {
    "${ApiTestSuite.apiRoot()}/inventory"
  }

  private static URL instances() {
    new URL("${inventory()}/instances")
  }

  static URL items() {
    new URL("${inventory()}/items")
  }
}
