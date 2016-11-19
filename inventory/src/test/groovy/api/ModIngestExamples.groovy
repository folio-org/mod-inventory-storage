package api

import groovyx.net.http.HTTPBuilder
import groovyx.net.http.HttpResponseException
import groovyx.net.http.Method
import groovyx.net.http.ResponseParseException
import io.vertx.core.json.JsonObject
import io.vertx.groovy.core.Vertx
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.folio.inventory.IngestVerticle
import org.folio.metadata.common.testing.HttpClient
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

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

  void "Ingest some MODS records"() {
    given:
      def modsFile = loadFileFromResource(
        "mods/multiple-example-mods-records.xml")

    when:
      def (ingestResponse, body) = ingestFile(
        new URL("http://localhost:9603/ingest/mods"), [modsFile])

    then:
      def statusLocation = ingestResponse.headers.location.toString()
      assert ingestResponse.status == 202
      assert statusLocation != null

      def conditions = new PollingConditions(
        timeout: 10, initialDelay: 1.0, factor: 1.25)

      conditions.eventually {
        ingestJobHasCompleted(statusLocation)
        expectedItemsCreatedFromIngest(statusLocation)
      }
  }

  def ingestJobHasCompleted(String statusLocation) {
    def client = new HttpClient()

    def (resp, body) = client.get(statusLocation)

    assert resp.status == 200
    assert body.status == "completed"
  }

  def expectedItemsCreatedFromIngest(String statusLocation) {
    def client = new HttpClient()

    def (resp, body) = client.get(statusLocation)

    assert resp.status == 200
    assert body.items != null

    List<JsonObject> items = body.items

    assert items.size() == 5
    assert items.every({ it.id != null })
    assert items.every({ it.title != null })
    assert items.every({ it.barcode != null })

    assert items.any({
      matches(it,
        "California: its gold and its inhabitants, by the author of 'Seven years on the Slave coast of Africa'.",
        "69228882")
    })

    assert items.any({
      matches(it,
        "Studien zur Geschichte der Notenschrift.",
        "69247446")
    })

    assert items.any({
      matches(it,
        "Essays on C.S. Lewis and George MacDonald",
        "53556908")
    })

    assert items.any({
      matches(it,
        "Statistical sketches of Upper Canada, for the use of emigrants, by a backwoodsman [W. Dunlop].",
        "69077747")
    })

    assert items.any({
      matches(it,
        "Edward McGuire, RHA",
        "22169083")
    })
  }

  void "Refuse ingest for multiple files"() {
    given:
      def modsFile = loadFileFromResource("mods/multiple-example-mods-records.xml")

    when:
      def (resp, body) = ingestFile(new URL("http://localhost:9603/ingest/mods"),
        [modsFile, modsFile])

    then:
      assert resp.status == 400
      assert body == "Cannot parsing multiple files in a single request"
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

  static Tuple ingestFile(URL url, List<File> recordsToIngest) {
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

  private File loadFileFromResource(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();

    new File(classLoader.getResource(filename).getFile())
  }

  private boolean matches(record, String expectedTitle, String expectedBarcode) {
    record.title == expectedTitle &&
      record.barcode == expectedBarcode
  }

}
