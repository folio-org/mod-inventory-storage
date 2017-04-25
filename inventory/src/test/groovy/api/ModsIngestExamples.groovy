package api

import api.support.Preparation
import org.folio.metadata.common.testing.HttpClient
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class ModsIngestExamples extends Specification {
  private final HttpClient client = ApiTestSuite.createHttpClient()

  def setup() {
    def preparation = new Preparation(client)
    preparation.deleteInstances()
    preparation.deleteItems()
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
    def (resp, itemsBody) = client.get(
      new URL("${inventoryApiRoot()}/items"))

    assert resp.status == 200

    def items = itemsBody.items

    assert items != null
    assert items.size() == 8
    assert items.every({ it.id != null })
    assert items.every({ it.title != null })
    assert items.every({ it.barcode != null })
    assert items.every({ it.instanceId != null })
    assert items.every({ it?.status?.name == "Available" })
    assert items.every({ it?.materialType?.id == ApiTestSuite.bookMaterialType })
    assert items.every({ it?.materialType?.name == "Book" })
    assert items.every({ it?.location?.name == "Main Library" })

    assert items.any({
      itemSimilarTo(it, "California: its gold and its inhabitants", "69228882")
    })

    assert items.any({
      itemSimilarTo(it, "Studien zur Geschichte der Notenschrift.", "69247446")
    })

    assert items.any({
      itemSimilarTo(it, "Essays on C.S. Lewis and George MacDonald", "53556908")
    })

    assert items.any({
      itemSimilarTo(it, "Statistical sketches of Upper Canada", "69077747")
    })

    assert items.any({
      itemSimilarTo(it, "Edward McGuire, RHA", "22169083")
    })

    assert items.any({
      itemSimilarTo(it, "Influenza della Poesia sui Costumi", "43620390")
    })

    assert items.any({
      itemSimilarTo(it, "Pavle Nik", "37696876")
    })

    assert items.any({
      itemSimilarTo(it, "Grammaire", "69250051")
    })

    items.every({ hasCorrectInstanceRelationship(it) })
  }

  private hasCorrectInstanceRelationship(item) {
    def (resp, instance) = client.get(
      new URL("${inventoryApiRoot()}/instances/${item.instanceId}"))

    assert resp.status == 200
    assert instance != null
    assert instance.title == item.title
  }

  private expectedInstancesCreatedFromIngest() {
    def (resp, body) = client.get(new URL("${inventoryApiRoot()}/instances"))

    def instances = body.instances

    assert resp.status == 200
    assert instances != null

    assert instances.size() == 8
    assert instances.every({ it.id != null })
    assert instances.every({ it.title != null })
    assert instances.every({ it.identifiers != null })
    assert instances.every({ it.identifiers.size() >= 1 })
    assert instances.every({ it.identifiers.every({ it.namespace != null }) })
    assert instances.every({ it.identifiers.every({ it.value != null }) })

    assert instances.any({
      InstanceSimilarTo(it, "California: its gold and its inhabitants")
    })

    assert instances.any({
      InstanceSimilarTo(it, "Studien zur Geschichte der Notenschrift.")
    })

    assert instances.any({
      InstanceSimilarTo(it, "Essays on C.S. Lewis and George MacDonald")
    })

    assert instances.any({
      InstanceSimilarTo(it, "Statistical sketches of Upper Canada")
    })

    assert instances.any({
      InstanceSimilarTo(it, "Edward McGuire, RHA")
    })

    assert instances.any({
      InstanceSimilarTo(it, "Influenza della Poesia sui Costumi") })

    assert instances.any({
      InstanceSimilarTo(it, "Pavle Nik") })

    assert instances.any({
      InstanceSimilarTo(it, "Grammaire") })
  }

  private beginIngest(List<File> files) {
    client.uploadFile(getIngestUrl(),
      files, "record")
  }

  private URL getIngestUrl() {
    new URL("${inventoryApiRoot()}/ingest/mods")
  }

  private String inventoryApiRoot() {
    "${ApiTestSuite.apiRoot()}/inventory"
  }

  private File loadFileFromResource(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();

    new File(classLoader.getResource(filename).getFile())
  }

  private boolean itemSimilarTo(
    record,
    String expectedSimilarTitle,
    String expectedBarcode) {

    record.title.contains(expectedSimilarTitle) &&
      record.barcode == expectedBarcode
  }

  private boolean InstanceSimilarTo(record, String expectedSimilarTitle) {
    record.title.contains(expectedSimilarTitle)
  }
}
