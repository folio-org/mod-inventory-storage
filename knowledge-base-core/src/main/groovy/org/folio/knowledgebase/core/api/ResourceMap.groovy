package org.folio.knowledgebase.core.api

import io.vertx.groovy.core.http.HttpServerRequest
import org.folio.knowledgebase.core.Config

class ResourceMap {

  static String root() {
    "/knowledge-base"
  }

  static String instance() {
    "/knowledge-base/instance"
  }

  static String instance(String path) {
    "/knowledge-base/instance" + path
  }

  static String instanceAbsolute(String path, HttpServerRequest request) {
    appendToAbsolute(request, "knowledge-base/instance" + path)
  }

  static String instanceMetadataAbsolute(HttpServerRequest request) {
    appendToAbsolute(request, "knowledge-base/instance/context")
  }

  static String instanceMetadata() {
    "/knowledge-base/instance/context"
  }

  private static String appendToAbsolute(HttpServerRequest request, String path) {
    absoluteRoot(request) + path
  }

  private static String absoluteRoot(HttpServerRequest request) {
    def okapiLocation = request.getHeader("X-Okapi-Url");

    appendTrailingSlash(okapiLocation ?: Config.apiBaseAddress)
  }

  private static String appendTrailingSlash(String host) {
    if(host[-1] != '/') {
      host + '/'
    }
    else {
      host
    }
  }
}
