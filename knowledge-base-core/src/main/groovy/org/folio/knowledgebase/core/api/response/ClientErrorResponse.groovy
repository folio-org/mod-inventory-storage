package org.folio.knowledgebase.core.api.response

import io.vertx.groovy.core.http.HttpServerResponse

class ClientErrorResponse {
  static badRequest(HttpServerResponse response, String reason) {
    response.setStatusCode(400)
    response.end(reason)
  }

  static notFound(HttpServerResponse response) {
    response.setStatusCode(404)
    response.end()
  }
}
