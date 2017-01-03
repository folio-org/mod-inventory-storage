package api

import org.folio.metadata.common.testing.HttpClient
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class ModsIngestExamples extends Specification {

  private final HttpClient client = new HttpClient("test_tenant")

  def setup() {
    deleteInstances()
    deleteItems()
  }


  void "Ingest some MODS records"() {
    given:
      def modsFile = loadFileFromResource(
        "mods/multiple-example-mods-records.xml")

    when:
      def (ingestResponse, _) = beginIngest([modsFile])

    then:
      def statusLocation = ingestResponse.headers.location.toString()
      assert ingestResponse.status == 202
      assert statusLocation != null

      def conditions = new PollingConditions(
        timeout: 10, initialDelay: 1.0, factor: 1.25)

      conditions.eventually {
        ingestJobHasCompleted(statusLocation)
        expectedInstancesCreatedFromIngest()
        expectedItemsCreatedFromIngest()
      }
  }

  void "Refuse ingest for multiple files"() {
    given:
      def modsFile =
        loadFileFromResource("mods/multiple-example-mods-records.xml")

    when:
      def (resp, body) = beginIngest([modsFile, modsFile])

    then:
      assert resp.status == 400
      assert body == "Cannot parse multiple files in a single request"
  }

  private ingestJobHasCompleted(String statusLocation) {
    def (resp, body) = client.get(statusLocation)

    assert resp.status == 200
    assert body.status == "Completed"
  }

  private expectedItemsCreatedFromIngest() {
    def (resp, items) = client.get(
      new URL("${inventoryApiRoot()}/items"))

    assert resp.status == 200

    assert items != null
    assert items.size() == 8
    assert items.every({ it.id != null })
    assert items.every({ it.title != null })
    assert items.every({ it.barcode != null })
    assert items.every({ it.instanceId != null })

    assert items.any({
      itemMatches(it,
        "California: its gold and its inhabitants",
        "69228882")
    })

    assert items.any({
      itemMatches(it,
        "Studien zur Geschichte der Notenschrift.",
        "69247446")
    })

    assert items.any({
      itemMatches(it,
        "Essays on C.S. Lewis and George MacDonald",
        "53556908")
    })

    assert items.any({
      itemMatches(it,
        "Statistical sketches of Upper Canada",
        "69077747")
    })

    assert items.any({
      itemMatches(it,
        "Edward McGuire, RHA",
        "22169083")
    })

    items.stream().forEach({ itemHasCorrectInstanceRelationship(it) })
  }

  private itemHasCorrectInstanceRelationship(item) {
    def (resp, instance) = client.get(
      new URL("${inventoryApiRoot()}/instances/${item.instanceId}"))

    assert resp.status == 200
    assert instance != null
    assert instance.title == item.title
  }

  private expectedInstancesCreatedFromIngest() {
    def client = new HttpClient("test_tenant")

    def (resp, instances) = client.get(new URL("${inventoryApiRoot()}/instances"))

    assert resp.status == 200
    assert instances != null

    assert instances.size() == 8
    assert instances.every({ it.id != null })
    assert instances.every({ it.title != null })

    assert instances.any({
      instanceMatches(it,
        "California: its gold and its inhabitants")
    })

    assert instances.any({
      instanceMatches(it,
        "Studien zur Geschichte der Notenschrift.")
    })

    assert instances.any({
      instanceMatches(it,
        "Essays on C.S. Lewis and George MacDonald")
    })

    assert instances.any({
      instanceMatches(it,
        "Statistical sketches of Upper Canada")
    })

    assert instances.any({
      instanceMatches(it,
        "Edward McGuire, RHA")
    })
  }

  private beginIngest(List<File> files) {
    client.uploadFile(getIngestUrl(),
      files, "record")
  }

  private URL getIngestUrl() {
    new URL("${inventoryApiRoot()}/ingest/mods")
  }

  private String inventoryApiRoot() {
    def directAddress = new URL("http://localhost:${ApiTestSuite.INVENTORY_VERTICLE_TEST_PORT}/inventory")

    def useOkapi = (System.getProperty("okapi.use") ?: "").toBoolean()

    useOkapi ?
      System.getProperty("okapi.address") + '/inventory'
      : directAddress
  }

  private File loadFileFromResource(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();

    new File(classLoader.getResource(filename).getFile())
  }

  private boolean itemMatches(
    record,
    String expectedSimilarTitle,
    String expectedBarcode) {

    record.title.contains(expectedSimilarTitle) &&
      record.barcode == expectedBarcode
  }

  private boolean instanceMatches(record, String expectedSimilarTitle) {
    record.title.contains(expectedSimilarTitle)
  }

  private void deleteItems() {
    def (response, _) = client.delete(
      new URL("${inventoryApiRoot()}/items"))

    assert response.status == 200
  }

  private void deleteInstances() {
    def (response, _) = client.delete(
      new URL("${inventoryApiRoot()}/instances"))

    assert response.status == 200
  }
}
