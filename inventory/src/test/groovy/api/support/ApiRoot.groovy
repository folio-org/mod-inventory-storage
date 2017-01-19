package api.support

import api.ApiTestSuite

class ApiRoot {
  private static String inventory() {
    "${ApiTestSuite.apiRoot()}/inventory"
  }

  private static URL instances() {
    new URL("${inventory()}/instances")
  }

  private static URL instances(String query) {
    new URL("${inventory()}/instances?${query}")
  }

  static URL items() {
    new URL("${inventory()}/items")
  }
}
