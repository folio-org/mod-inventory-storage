package support

import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.HttpResponseException
import groovyx.net.http.Method
import groovyx.net.http.ResponseParseException
import org.apache.commons.io.IOUtils

class HttpClient {
  static private final String tenant = "test-tenant"

  static get(url) {
    def http = new HTTPBuilder(url)

    try {
      http.request(Method.GET) { req ->
        headers.'X-Okapi-Tenant' = tenant

        response.success = { resp, body ->
          body
        }

        response.failure = { resp, body ->
          println "Failed to access ${url}"
          println "Status Code: ${resp.statusLine}"
          resp.headers.each { println "${it.name} : ${it.value}" }

          if (body instanceof InputStream) {
            println "Body: ${IOUtils.toString(body)}"
          } else {
            println "Body: ${body}"
          }
          null
        }
      }
    }
    catch (ConnectException ex) {
      println "Failed to connect to ${url} internalError: ${ex}"
    }
    catch (ResponseParseException ex) {
      println "Failed to access ${url} internalError: ${ex}"
    }
    catch (HttpResponseException ex) {
      parseResponseException(url, ex)
    }
  }

  static getExpectingFailure(url, Closure onFailure) {
    def http = new HTTPBuilder(url)

    try {
      http.request(Method.GET) { req ->
        headers.'X-Okapi-Tenant' = tenant

        response.success = { resp, body ->
          body
        }

        response.failure = { resp, body ->
          onFailure resp, body
        }
      }
    }
    catch (ConnectException ex) {
      println "Failed to connect to ${url} internalError: ${ex}"
    }
    catch (ResponseParseException ex) {
      println "Failed to access ${url} internalError: ${ex}"
    }
    catch (HttpResponseException ex) {
      parseResponseException(url, ex)
    }
  }

  static canGet(url) {
    get(url) != null
  }


  static post(url, bodyToSend = null) {
    if (url == null)
      throw new IllegalArgumentException("url is null")

    def http = new HTTPBuilder(url)

    try {
      http.request(Method.POST, ContentType.JSON) { req ->
        body = bodyToSend

        response.success = { resp, responseBody ->
          responseBody
        }
      }
    }
    catch (ConnectException ex) {
      println "Failed to access ${url} internalError: ${ex})"
    }
    catch (ResponseParseException ex) {
      println "Failed to access ${url} internalError: ${ex})"
    }
    catch (HttpResponseException ex) {
      parseResponseException(url, ex)
    }
  }

  static def String postToCreate(URL url, bodyToSend = null) {
    if (url == null)
      throw new IllegalArgumentException("url is null")

    def http = new HTTPBuilder(url)

    try {
      http.request(Method.POST, ContentType.JSON) { req ->
        body = bodyToSend
        headers.'X-Okapi-Tenant' = tenant

        response.success = { resp ->
          println "Status Code: ${resp.status}"
          resp.headers.location.toString()
        }
      }
    }
    catch (ConnectException ex) {
      println "Failed to access ${url} internalError: ${ex})"
    }
    catch (ResponseParseException ex) {
      println "Failed to access ${url} internalError: ${ex})"
    }
    catch (HttpResponseException ex) {
      parseResponseException(url, ex)
    }
  }

  static def getByQuery(URL url, Map<String, Object> query) {
    def http = new HTTPBuilder(url)

    try {
      http.request(Method.GET) { req ->
        headers.'X-Okapi-Tenant' = tenant
        uri.query = query

        response.success = { resp, body ->
          body
        }

        response.failure = { resp, body ->
          println "Failed to access ${url}"
          println "Status Code: ${resp.statusLine}"
          resp.headers.each { println "${it.name} : ${it.value}" }

          if (body != null) {
            println "Body: ${IOUtils.toString(body)}"
          }
          null
        }
      }
    }
    catch (ConnectException ex) {
      println "Failed to connect to ${url} internalError: ${ex}"
    }
    catch (ResponseParseException ex) {
      println "Failed to access ${url} internalError: ${ex}"
    }
    catch (HttpResponseException ex) {
      parseResponseException(url, ex)
    }
  }

  private static void parseResponseException(URL url, HttpResponseException ex) {
    println "Failed to access ${url} internalError: ${ex}"
    printf "Headers: %s \n", ex.getResponse().getAllHeaders()
    printf "Content Type: %s \n", ex.getResponse().getContentType()
    printf "Status Code: %s \n", ex.getResponse().getStatus()
  }
}
