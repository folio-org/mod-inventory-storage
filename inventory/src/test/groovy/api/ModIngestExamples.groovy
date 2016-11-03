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

    def (resp, body) = ingestRecord(new URL("http://localhost:9603/ingest/mods"), [singleModsRecord])

    then:
      assert resp.status == 200
      assert body != null
      assert body.id != null
      assert body.title == "'Edward Samuel: ei Oes a'i Waith', an essay submitted for competition at the National Eisteddfod held at Corwen, 1919; together ..., 1919."
      assert body.barcode == "78584457"
  }

  void "Refuse ingest for multiple files"() {
    given:
      def singleModsRecord = loadFileFromResource("mods-single-record.xml")

    when:
      def (resp, body) = ingestRecord(new URL("http://localhost:9603/ingest/mods"),
        [singleModsRecord, singleModsRecord])

    then:
      assert resp.status == 400
      assert body == "Cannot ingest multiple files in a single request"
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

  static def ingestRecord(URL url, List<File> recordsToIngest) {
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
          multipartBuilder.addBinaryBody("record", it)
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

          new Tuple(resp, body.getText())
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

  private File loadFileFromResource(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();

    new File(classLoader.getResource(filename).getFile())
  }

}
