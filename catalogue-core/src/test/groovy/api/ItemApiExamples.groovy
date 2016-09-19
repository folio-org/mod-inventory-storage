package api

import org.folio.catalogue.core.domain.Item
import org.folio.catalogue.core.storage.Storage
import io.vertx.core.json.JsonObject
import spock.lang.Specification
import support.FakeKnowledgeBase
import support.HttpClient
import support.World

import java.util.concurrent.CompletableFuture

import static support.HttpClient.get

class ItemApiExamples extends Specification {

  def setupSpec() {
    def vertx = World.startVertx()

    def fakeDeployed = new CompletableFuture<Void>()

    FakeKnowledgeBase.deploy(vertx, fakeDeployed)

    World.startApi()

    fakeDeployed.join()
  }

  def cleanupSpec() {
    World.stopVertx()
  }

  void "Create a new item"() {
    given:
      World.reset()

    when:
      JsonObject existingInstance = new JsonObject()
        .put("title", "The End of the World Running Club")
        .put("identifiers", [[namespace: "asin", value: "B00LD69QGO"],
                           [namespace: "isbn", value: "1785032666"]]);

      def locationOfInstance = registerInstanceWithFakeKnowledgeBase(existingInstance)

      JsonObject newItemRequest = new JsonObject()
        .put("barcode", "564323765087")
        .put("instance", locationOfInstance.toString())

      def locationOfNewItem = createNewItemViaApi(newItemRequest)

      def newItem = HttpClient.get(locationOfNewItem)

    then:
      assert newItem.title == "The End of the World Running Club"
      assert newItem.links.instance == locationOfInstance.toString()
      assert newItem.barcode == "564323765087"

      selfLinkShouldRespectWayResourceWasReached(newItem)
      selfLinkShouldBeReachable(newItem)

  }

  void "Creating a new item with unreachable instance fails"() {
    given:
      World.reset()

    when:
      JsonObject newItemRequest = new JsonObject()
        .put("barcode", "564323765087")
        .put("instance", "http://somenonexistingdomain.com/instance/54532344")

      def response = HttpClient.post(World.itemApiRoot(),
        newItemRequest.encodePrettily())

    then:
      assert response.status == 400
  }

  void "Find all instances and navigate to them"() {
    given:
      World.reset()
      someItems()

    when:
      def itemsFromApi = findAllItems()

    then:
      assert itemsFromApi.size() == 2

      def firstItem = itemsFromApi[0]
      def secondItem = itemsFromApi[1]

      assert firstItem.title == "A Long Way to a Small Angry Planet"
      assert secondItem.title == "Nod"

      assert firstItem.barcode != null
      assert secondItem.barcode != null

      selfLinkShouldRespectWayResourceWasReached(firstItem)
      selfLinkShouldRespectWayResourceWasReached(secondItem)

      selfLinkShouldBeReachable(firstItem)
      selfLinkShouldBeReachable(secondItem)
  }

  void "Cannot find an unknown item"() {
    given:
      World.reset()

    when:
      def status;
      HttpClient.getExpectingFailure(World.itemApiRoot().toString() + "/${UUID.randomUUID()}",
        { resp, body -> status = resp.statusLine })

    then:
      assert status.toString().contains("404")
  }

  void "Find by part of a title"() {
    given:
      World.reset()
      someItems()

    when:
      def matchingItems = findItemsByPartialTitle("Long Way")

    then:
      assert matchingItems.size() == 1

      def onlyItem = matchingItems[0]

      assert onlyItem.title == "A Long Way to a Small Angry Planet"
      assert onlyItem.barcode != null

      selfLinkShouldRespectWayResourceWasReached(onlyItem)
      selfLinkShouldBeReachable(onlyItem)
  }

  private static findAllItems() {
    HttpClient.get(World.itemApiRoot())
  }

  private def findItemsByPartialTitle(String partialTitle) {
    HttpClient.getByQuery(World.itemApiRoot(), [partialTitle: partialTitle])
  }

  private static someItems() {
    def itemCollection = Storage.itemCollection

    def firstItem = new Item("A Long Way to a Small Angry Planet", "http://books.com/small-angry", "687954039561")

    def secondItem = new Item("Nod", "http://books.com/nod", "675478965475")

    itemCollection.add([firstItem, secondItem])
  }

  private void selfLinkShouldBeReachable(instance) {
    assert HttpClient.canGet(instance.links.self)
  }

  private void selfLinkShouldRespectWayResourceWasReached(instance) {
    assert instance.links.self.contains(World.catalogueApiRoot().toString())
  }

  URL createNewItemViaApi(JsonObject itemRequest) {
    def create = HttpClient.postToCreate(World.itemApiRoot(),
            itemRequest.encodePrettily())

    new URL(create)
  }

  URL registerInstanceWithFakeKnowledgeBase(JsonObject instanceRequest) {
    new URL(HttpClient.postToCreate(GetFakeKnowledgeBaseInstanceUrl(),
      instanceRequest.encodePrettily()))
  }

  private URL GetFakeKnowledgeBaseInstanceUrl() {
    new URL(get(FakeKnowledgeBase.address).links.instances)
  }
}
