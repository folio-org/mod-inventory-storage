package org.folio.metadata.common.testing

import groovyx.net.http.*
import org.apache.commons.io.IOUtils
import org.apache.http.entity.mime.MultipartEntityBuilder

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

  Tuple uploadFile(URL url, List<File> recordsToIngest, String partName) {
    if (url == null)
      throw new IllegalArgumentException("url is null")

    def http = new HTTPBuilder(url)

    try {
      http.request(Method.POST) { req ->
        println "\nTest Http Client POST to: ${url}\n"

        headers.'X-Okapi-Tenant' = "our"
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
