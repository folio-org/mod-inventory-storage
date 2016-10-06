package org.folio.metadata.common.api.response

import io.vertx.core.json.Json

class JsonResponse {
  static success(response, body) {
    def json = Json.encodePrettily(body)

    response.putHeader "content-type", "application/json"
    response.putHeader "content-length", Integer.toString(json.length())

    println("Response: ${json}")

    response.end json
  }
}
