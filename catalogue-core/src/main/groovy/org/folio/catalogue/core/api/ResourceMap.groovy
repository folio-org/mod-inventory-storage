package org.folio.catalogue.core.api

import org.folio.catalogue.core.Config
import io.vertx.groovy.core.http.HttpServerRequest

class ResourceMap {

  static String root() {
    "/catalogue"
  }

  static String item() {
    "/catalogue/item"
  }

  static String item(String path) {
    "/catalogue/item" + path
  }

  static def itemAbsolute(String path, HttpServerRequest request) {
    appendToAbsolute(request, "catalogue/item" + path)
  }

  private static String appendToAbsolute(HttpServerRequest request, String path) {
    def okapiLocation = request.getHeader("X-Okapi-Url");

    (okapiLocation ?: Config.apiBaseAddress) + path
  }
}
