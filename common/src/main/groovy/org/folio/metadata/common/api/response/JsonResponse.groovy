package org.folio.metadata.common.api.response

import io.vertx.core.json.Json
import io.vertx.groovy.core.http.HttpServerResponse

class JsonResponse {
  static success(HttpServerResponse response, body) {
    jsonResponse(response, body, 200)
  }

  static created(HttpServerResponse response, body) {
    jsonResponse(response, body, 201)
  }

  private static void jsonResponse(
    HttpServerResponse response, body, Integer status) {

    def json = Json.encodePrettily(body)

    response.statusCode = status
    response.putHeader "content-type", "application/json;"

    response.putHeader "content-length", Integer.toString(json.length())

    println("JSON Response: ${json}")

    response.end(json)
  }
}
