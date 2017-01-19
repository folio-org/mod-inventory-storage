package api.support

import api.ApiTestSuite

class ApiRoot {
  static String inventory() {
    "${ApiTestSuite.apiRoot()}/inventory"
  }

  static URL instances() {
    new URL("${inventory()}/instances")
  }

  static URL instances(String query) {
    new URL("${inventory()}/instances?${query}")
  }

  static URL items() {
    new URL("${inventory()}/items")
  }

  static URL items(String query) {
    new URL("${inventory()}/items?${query}")
  }
}
