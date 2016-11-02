package api

import groovyx.net.http.HTTPBuilder
import groovyx.net.http.HttpResponseException
import groovyx.net.http.Method
import groovyx.net.http.ResponseParseException
import io.vertx.groovy.core.Vertx
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.folio.inventory.IngestVerticle
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class ModIngestExamples extends Specification {

  public static final testPortToUse = 9603

  private Vertx vertx;

  def setupSpec() {
    startVertx()
    startIngestVerticle()
  }

  def cleanupSpec() {
    stopVertx()
  }

  void "Ingest a single MODS record"() {
    given:
      def singleModsRecord = loadFileFromResource("mods-single-record.xml")

    when:

    def body = ingestRecord(new URL("http://localhost:9603/ingest/mods"), singleModsRecord)

    then:
      assert body != null
      assert body.id != null
      assert body.title == "'Edward Samuel: ei Oes a'i Waith', an essay submitted for competition at the National Eisteddfod held at Corwen, 1919; together ..., 1919."
      assert body.barcode == "78584457"
  }

  def startVertx() {
    vertx = Vertx.vertx()
  }

  def startIngestVerticle() {
    IngestVerticle.deploy(vertx, ["port": testPortToUse]).join()
  }

  def stopVertx() {
    if (vertx != null) {
      def stopped = new CompletableFuture()

      vertx.close({ res ->
        if (res.succeeded()) {
          stopped.complete(null);
        } else {
          stopped.completeExceptionally(res.cause());
        }
      })

      stopped.join()
    }
  }

  static def ingestRecord(URL url, File recordToIngest) {
    if (url == null)
      throw new IllegalArgumentException("url is null")

    def http = new HTTPBuilder(url)

    try {
      http.request(Method.POST) { req ->
        println "\nTest Http Client POST to: ${url}\n"

        headers.'X-Okapi-Tenant' = "our"
        requestContentType = 'multipart/form-data'

        req.entity = new MultipartEntityBuilder().addBinaryBody("record", recordToIngest).build()

        response.success = { resp, body ->
          println "Status Code: ${resp.status}"
          println "Location: ${resp.headers.location}\n"

          body
        }

        response.failure = { resp ->
          println "Status Code: ${resp.status}"
          println "Location: ${resp.headers.location}\n"

          null
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
      println "Failed to access ${url} error: ${ex})\n"
    }
  }

  private File loadFileFromResource(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();

    new File(classLoader.getResource(filename).getFile())
  }

}
