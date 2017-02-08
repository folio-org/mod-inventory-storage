package org.folio.metadata.common.api.response

import io.vertx.core.json.Json
import io.vertx.groovy.core.buffer.Buffer
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
    def buffer = Buffer.buffer(json, "UTF-8")

    response.statusCode = status
    response.putHeader "content-type", "application/json; charset=utf-8"
    response.putHeader "content-length", Integer.toString(buffer.length())

    println("JSON Success: ${json}")

    response.write(buffer)
    response.end()
  }
}
