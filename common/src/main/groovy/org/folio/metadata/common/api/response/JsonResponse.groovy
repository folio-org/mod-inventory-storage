package org.folio.metadata.common.api.response

import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.groovy.core.http.HttpServerResponse

class JsonResponse {
  static success(response, body) {
    def json = Json.encodePrettily(body)

    response.statusCode = 200
    response.putHeader "content-type", "application/json"
    response.putHeader "content-length", Integer.toString(json.length())

    println("Response: ${json}")

    response.end json
  }

  static created(HttpServerResponse response, body) {
    def json = Json.encodePrettily(body)

    response.statusCode = 201
    response.putHeader "content-type", "application/json"
    response.putHeader "content-length", Integer.toString(json.length())

    println("Response: ${json}")

    response.end json
  }
}
