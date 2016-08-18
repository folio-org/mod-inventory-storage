import groovyx.net.http.*
import io.vertx.groovy.core.Vertx
import knowledgebase.core.ApiVerticle
import org.apache.commons.io.IOUtils
import org.junit.Before
import org.junit.Test
import java.util.Properties

import java.util.concurrent.CompletableFuture

public class RootResourceExamples {

    @Before
    public void before() {
        def deployed = new CompletableFuture()
        ApiVerticle.deploy(Vertx.vertx(), deployed)
        deployed.join()
    }

    @Test
    void providesJson() {
        assert get(apiRoot()).message == 'Welcome to the Folio Knowledge Base'
    }

    def apiRoot() {
        def directAddress = new URL('http://localhost:9401/knowledge-base')
        def useOkapi = (System.getProperty("okapi.use") ?: "").toBoolean()

        useOkapi ? new URL(System.getProperty("okapi.address")) : directAddress
    }

    static get(url) {
        def http = new HTTPBuilder(url)

        try {
            http.request(Method.GET) { req ->
                headers.'X-Okapi-Tenant' = "our"

                response.success = { resp, body ->
                    body
                }

                response.failure = { resp, body ->
                    println "Failed to access ${url}"
                    println "Status Code: ${resp.statusLine}"
                    resp.headers.each { println "${it.name} : ${it.value}" }

                    if(body != null) {
                        println "Body: ${IOUtils.toString(body)}"
                    }
                    null
                }
            }
        }
        catch (ConnectException ex) {
            println "Failed to connect to ${url} error: ${ex}"
        }
        catch (ResponseParseException ex) {
            println "Failed to access ${url} error: ${ex}"
        }
        catch (HttpResponseException ex) {
            println "Failed to access ${url} error: ${ex}"
            printf "Headers: %s \n", ex.getResponse().getAllHeaders()
            printf "Content Type: %s \n", ex.getResponse().getContentType()
            printf "Status Code: %s \n", ex.getResponse().getStatus()
        }
    }

}