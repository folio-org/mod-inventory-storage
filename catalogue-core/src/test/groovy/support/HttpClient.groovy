package support

import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.HttpResponseException
import groovyx.net.http.Method
import groovyx.net.http.ResponseParseException
import org.apache.commons.io.IOUtils

class HttpClient {

    static get(url) {
        def http = new HTTPBuilder(url)

        try {
            http.request(Method.GET) { req ->
                println "\nTest Http Client GET from: ${url}\n"

                headers.'X-Okapi-Tenant' = "our"

                response.success = { resp, body ->
                    body
                }

                response.failure = { resp, body ->
                    println "Failed to access ${url}"
                    println "Status Code: ${resp.statusLine}"
                    resp.headers.each { println "${it.name} : ${it.value}" }
                    println ""

                    if(body instanceof InputStream) {
                        println "Body: ${IOUtils.toString(body)}\n"
                    }
                    else {
                        println "Body: ${body}\n"
                    }
                    null
                }
            }
        }
        catch (ConnectException ex) {
            println "Failed to connect to ${url} error: ${ex}\n"
        }
        catch (ResponseParseException ex) {
            println "Failed to access ${url} error: ${ex}\n"
        }
        catch (HttpResponseException ex) {
            parseResponseException(url, ex)
        }
    }

    static getExpectingFailure(url, Closure onFailure) {
        def http = new HTTPBuilder(url)

        try {
            http.request(Method.GET) { req ->
                println "\nTest Http Client GET from: ${url}\n"

                headers.'X-Okapi-Tenant' = "our"

                response.success = { resp, body ->
                    body
                }

                response.failure = { resp, body ->
                    onFailure resp, body
                }
            }
        }
        catch (ConnectException ex) {
            println "Failed to connect to ${url} error: ${ex}\n"
        }
        catch (ResponseParseException ex) {
            println "Failed to access ${url} error: ${ex}\n"
        }
        catch (HttpResponseException ex) {
            parseResponseException(url, ex)
        }
    }

    static canGet(url) {
        get(url) != null
    }

    static def String postToCreate(URL url, bodyToSend = null) {
        if(url == null)
            throw new IllegalArgumentException("url is null")

        def http = new HTTPBuilder(url)

        try {
            http.request(Method.POST, ContentType.JSON) { req ->
                println "\nTest Http Client POST to: ${url}\n"

                body = bodyToSend
                headers.'X-Okapi-Tenant' = "our"

                response.success = { resp ->
                    println "Status Code: ${resp.status}"
                    println "Location: ${resp.headers.location}\n"

                    resp.headers.location.toString()
                }
            }
        }
        catch (ConnectException ex) {
            println "Failed to access ${url} error: ${ex})\n"
        }
        catch (ResponseParseException ex) {
            println "Failed to access ${url} error: ${ex})\n"
        }
        catch (HttpResponseException ex) {
            parseResponseException(url, ex)
        }
    }

    private static void parseResponseException(URL url, HttpResponseException ex) {
        println "Failed to access ${url} error: ${ex} (More information below)\n"
        printf "Headers: %s \n", ex.getResponse().getAllHeaders()
        printf "Content Type: %s \n", ex.getResponse().getContentType()
        printf "Status Code: %s \n", ex.getResponse().getStatus()
    }
}
