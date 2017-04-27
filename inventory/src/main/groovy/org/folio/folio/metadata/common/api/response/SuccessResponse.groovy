package org.folio.metadata.common.api.response

import io.vertx.groovy.core.http.HttpServerResponse

class SuccessResponse {
  static noContent(HttpServerResponse response) {
    response.setStatusCode(204);
    response.end()
  }

  static ok(HttpServerResponse response) {
    response.setStatusCode(200);
    response.end()
  }
}
