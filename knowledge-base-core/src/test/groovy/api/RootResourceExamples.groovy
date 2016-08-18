import groovyx.net.http.*
import io.vertx.groovy.core.Vertx
import knowledgebase.core.ApiVerticle
import org.junit.Before
import org.junit.Test

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
        new URL('http://localhost:9401/knowledge-base')
    }

    static get(url) {
        def http = new HTTPBuilder(url)

        try {
            http.request(Method.GET) { req ->
                response.success = { resp, body ->
                    body
                }
            }
        }
        catch (ConnectException ex) {
            println "Failed to access ${url} error: ${ex}"
        }
        catch (ResponseParseException ex) {
            println "Failed to access ${url} error: ${ex}"
        }
        catch (HttpResponseException ex) {
            println "Failed to access ${url} error: ${ex}"
        }
    }

}