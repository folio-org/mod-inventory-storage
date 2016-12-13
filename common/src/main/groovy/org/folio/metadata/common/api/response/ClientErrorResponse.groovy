package org.folio.metadata.common.api.response

import io.vertx.groovy.core.http.HttpServerResponse
import org.apache.http.entity.ContentType

class ClientErrorResponse {
  static notFound(HttpServerResponse response) {
    println("Not Found Response")
    response.setStatusCode(404)
    response.end()
  }

  static badRequest(HttpServerResponse response, String reason) {
    response.setStatusCode(400)
    response.putHeader "content-type", ContentType.TEXT_PLAIN.toString()
    response.end(reason)
  }

  static forbidden(response, String reason) {
    response.setStatusCode(403)
    response.putHeader "content-type", ContentType.TEXT_PLAIN.toString()
    response.end(reason)
  }
}
