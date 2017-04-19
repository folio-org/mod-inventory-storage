package org.folio.metadata.common.testing

import groovyx.net.http.*
import org.apache.commons.io.IOUtils
import org.apache.http.entity.mime.MultipartEntityBuilder

class HttpClient {
  private final String TENANT_HEADER = 'X-Okapi-Tenant'
  private final String OKAPI_URL_HEADER = 'X-Okapi-Url'
  private final String TOKEN_HEADER = 'X-Okapi-Token'

  private final String tenantId
  private final String token
  private final String okapiUrl

  def HttpClient(String okapiUrl, String tenantId, String token) {
    this.okapiUrl = okapiUrl
    this.tenantId = tenantId
    this.token = token
  }

  Tuple get(String url) {
    get(new URL(url))
  }

  Tuple get(URL url) {
    def requestBuilder = new HTTPBuilder(url)

    try {
      requestBuilder.request(Method.GET) { req ->

        headers.put(OKAPI_URL_HEADER, okapiUrl)
        headers.put(TENANT_HEADER, tenantId)
        headers.put(TOKEN_HEADER, token)

        response.success = { resp, body ->
          new Tuple(resp, body)
        }

        response.failure = handleFailure(url)
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

        headers.put(OKAPI_URL_HEADER, okapiUrl)
        headers.put(TENANT_HEADER, tenantId)
        headers.put(TOKEN_HEADER, token)
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

        response.failure = handleFailure(url)
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


  Tuple delete(URL url) {
    def requestBuilder = new HTTPBuilder(url)

    requestBuilder.request(Method.DELETE) { req ->

      headers.put(OKAPI_URL_HEADER, okapiUrl)
      headers.put(TENANT_HEADER, tenantId)
      headers.put(TOKEN_HEADER, token)

      response.success = { resp, body ->
        println "Status Code: ${resp.status}"
        println "Location: ${resp.headers.location}\n"

        new Tuple2(resp, body)
      }

      response.failure = handleFailure(url)
    }
  }

  Tuple post(URL url, String bodyToSend) {
    if (url == null)
      throw new IllegalArgumentException("url is null")

    def http = new HTTPBuilder(url)

    try {
      http.request(Method.POST) { req ->

        headers.put(OKAPI_URL_HEADER, okapiUrl)
        headers.put(TENANT_HEADER, tenantId)
        headers.put(TOKEN_HEADER, token)
        requestContentType = 'application/json'
        body = bodyToSend

        response.success = { resp, responseBody ->
          println "Status Code: ${resp.status}"
          println "Location: ${resp.headers.location}\n"

          new Tuple2(resp, responseBody)
        }

        response.failure = handleFailure(url)
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

  def put(URL url, bodyToSend) {
    if (url == null)
      throw new IllegalArgumentException("url is null")

    def http = new HTTPBuilder(url)

    try {
      http.request(Method.PUT) { req ->
        headers.put(OKAPI_URL_HEADER, okapiUrl)
        headers.put(TENANT_HEADER, tenantId)
        headers.put(TOKEN_HEADER, token)
        requestContentType = 'application/json'
        body = bodyToSend

        response.success = { resp, responseBody ->
          println "Status Code: ${resp.status}"
          println "Location: ${resp.headers.location}\n"

          new Tuple2(resp, responseBody)
        }

        response.failure = handleFailure(url)
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

  private Closure<Tuple2> handleFailure(url) {
    { resp, body ->
      println "Failed to access ${url}"
      println "Status Code: ${resp.status}"

      resp.headers.each { println "${it.name} : ${it.value}" }
      println ""

      def bodyString = getBody(body)

      println "Body: ${bodyString}\n"

      new Tuple2(resp, bodyString)
    }
  }

  private String getBody(body) {
    if(body instanceof InputStreamReader) {
      IOUtils.toString(body)
    }
    else {
      body
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
