package org.folio.metadata.common.testing

import groovyx.net.http.HTTPBuilder
import groovyx.net.http.HttpResponseException
import groovyx.net.http.Method
import groovyx.net.http.ResponseParseException
import io.vertx.core.Handler
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.groovy.core.Vertx
import org.apache.http.entity.mime.MultipartEntityBuilder

import java.util.concurrent.CompletableFuture

class VertxHttpClient {

  private final Vertx vertx
  private final String tenant

  VertxHttpClient(Vertx vertx) {
    this.vertx = vertx
    this.tenant = "test_tenant"
  }

  VertxHttpClient(Vertx vertx, String tenant) {
    this.vertx = vertx
    this.tenant = tenant
  }

  Tuple get(url) {
    def client = this.vertx.createHttpClient()

    def result = new CompletableFuture()

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"

        result.complete(new JsonObject(responseBody))
      })
    }

    Handler<Throwable> onException = { println "Exception: ${it}" }

    String tenant = tenant

    client.requestAbs(HttpMethod.GET, url, onResponse)
      .exceptionHandler(onException)
      .putHeader("X-Okapi-Tenant", tenant)
      .end()

    new Tuple2(null, result.get())
  }

  Tuple uploadFile(URL url, List<File> recordsToIngest, String partName) {
    if (url == null)
      throw new IllegalArgumentException("url is null")

    def http = new HTTPBuilder(url)

    try {
      http.request(Method.POST) { req ->
        println "\nTest Http Client POST to: ${url}\n"

        headers.'X-Okapi-Tenant' = tenant
        requestContentType = 'multipart/form-data'

        def multipartBuilder = new MultipartEntityBuilder()

        recordsToIngest.each {
          multipartBuilder.addBinaryBody(partName, it)
        }

        req.entity = multipartBuilder.build()

        response.success = { resp, body ->
          println "Status Code: ${resp.status}"
          println "Location: ${resp.headers.location}\n"

          new Tuple2(resp, body)
        }

        response.failure = { resp, body ->
          println "Status Code: ${resp.status}"
          println "Location: ${resp.headers.location}\n"

          new Tuple2(resp, body.getText())
        }
      }
    }
    catch (ConnectException ex) {
      println "Failed to access ${url} internalError: ${ex})\n"
    }
    catch (ResponseParseException ex) {
      println "Failed to access ${url} internalError: ${ex})\n"
    }
    catch (HttpResponseException ex) {
      println "Failed to access ${url} internalError: ${ex})\n"
    }
  }

  private void parseResponseException(URL url, HttpResponseException ex) {
    println "Failed to access ${url} internalError: ${ex} (More information below)\n"
    println "Headers:"
    println "${ex.response.allHeaders}"
    println "Content Type: ${ex.response.contentType}"
    println "Status Code: ${ex.response.status}"
  }
}
