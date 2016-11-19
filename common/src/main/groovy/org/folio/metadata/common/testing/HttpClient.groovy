package org.folio.metadata.common.testing

import groovyx.net.http.*
import org.apache.commons.io.IOUtils

class HttpClient {

  Tuple get(url) {
    def requestBuilder = new HTTPBuilder(url)

    try {
      requestBuilder.request(Method.GET) { req ->
        println "\nTest Http Client GET from: ${url}\n"

        headers.'X-Okapi-Tenant' = "our"

        response.success = { resp, body ->
          new Tuple(resp, body)
        }

        response.failure = { resp, body ->
          println "Failed to access ${url}"
          println "Status Code: ${resp.statusLine}"
          resp.headers.each { println "${it.name} : ${it.value}" }
          println ""

          if (body instanceof InputStream) {
            println "Body: ${IOUtils.toString(body)}\n"
          } else {
            println "Body: ${body}\n"
          }
          null
        }
      }
    }
    catch (ConnectException ex) {
      println "Failed to connect to ${url} internalError: ${ex}\n"
    }
    catch (ResponseParseException ex) {
      println "Failed to access ${url} internalError: ${ex}\n"
    }
    catch (HttpResponseException ex) {
      parseResponseException(url, ex)
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
