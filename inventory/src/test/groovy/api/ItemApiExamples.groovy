package api

import api.support.ApiRoot
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import spock.lang.Specification
import api.support.Preparation
import org.folio.metadata.common.testing.HttpClient

class ItemApiExamples extends Specification {
  private final String TENANT_ID = "test_tenant"

  private final HttpClient client = new HttpClient(TENANT_ID)

  def setup() {
    new Preparation(client).deleteItems()
    new Preparation(client).deleteInstances()
  }

  void "Can create an item"() {
    given:
      def createdInstance = createInstance("Long Way to a Small Angry Planet")

      def newItemRequest = new JsonObject()
        .put("title", "Long Way to a Small Angry Planet")
        .put("instanceId", createdInstance.id)
        .put("barcode", "645398607547")

    when:
      def (postResponse, __) = client.post(
        new URL("${ApiRoot.items()}"),
        Json.encodePrettily(newItemRequest))

    then:
      def location = postResponse.headers.location.toString()

      assert postResponse.status == 201
      assert location != null

      def (getResponse, createdItem) = client.get(location)

      assert getResponse.status == 200

      assert createdItem.id != null
      assert createdItem.title == "Long Way to a Small Angry Planet"
      assert createdItem.instanceId == createdInstance.id
      assert createdItem.barcode == "645398607547"

      selfLinkRespectsWayResourceWasReached(createdItem)
      selfLinkShouldBeReachable(createdItem)
  }

  void "Can delete all items"() {
    given:
      def createdInstance = createInstance("Long Way to a Small Angry Planet")

      createItem("Long Way to a Small Angry Planet", createdInstance.id,
        "645398607547")

      createItem("Long Way to a Small Angry Planet", createdInstance.id,
        "175848607547")

      createItem("Long Way to a Small Angry Planet", createdInstance.id,
        "645334645247")

    when:
      def (deleteResponse, deleteBody) = client.delete(ApiRoot.items())

      def (_, items) = client.get(ApiRoot.items())

    then:
      assert deleteResponse.status == 204
      assert deleteBody == null

      assert items.size() == 0
  }

  void "Can page all items"() {
    given:
      def smallAngryInstance = createInstance("Long Way to a Small Angry Planet")

      createItem("Long Way to a Small Angry Planet", smallAngryInstance.id,
        "645398607547")

      createItem("Long Way to a Small Angry Planet", smallAngryInstance.id,
        "175848607547")

      createItem("Long Way to a Small Angry Planet", smallAngryInstance.id,
        "645334645247")

      def nodInstance = createInstance("Nod")

      createItem("Nod", nodInstance.id,
        "564566456546")

      createItem("Nod", nodInstance.id,
        "943209584495")

    when:
      def (firstPageResponse, firstPage) = client.get(
        ApiRoot.items("limit=3"))

      def (secondPageResponse, secondPage) = client.get(
        ApiRoot.items("limit=3&offset=3"))

    then:
      assert firstPageResponse.status == 200
      assert firstPage.size() == 3

      assert secondPageResponse.status == 200
      assert secondPage.size() == 2

      firstPage.each {
        selfLinkRespectsWayResourceWasReached(it)
      }

      secondPage.each {
        selfLinkRespectsWayResourceWasReached(it)
      }

      firstPage.each {
        selfLinkShouldBeReachable(it)
      }

      secondPage.each {
        selfLinkShouldBeReachable(it)
      }
  }

  private void selfLinkRespectsWayResourceWasReached(item) {
    assert containsApiRoot(item.links.self)
  }

  private boolean containsApiRoot(String link) {
    link.contains(ApiTestSuite.apiRoot())
  }

  private void selfLinkShouldBeReachable(instance) {
    def (response, _) = client.get(instance.links.self)

    assert response.status == 200
  }

  private def createInstance(String title) {
    def newInstanceRequest = new JsonObject()
      .put("title", title)

    def (createInstanceResponse, _) = client.post(ApiRoot.instances(),
      Json.encodePrettily(newInstanceRequest))

    def instanceLocation = createInstanceResponse.headers.location.toString()

    def (response, createdInstance) = client.get(instanceLocation)

    assert response.status == 200

    createdInstance
  }

  private def createItem(String title, String instanceId, String barcode) {
    def newItemRequest = new JsonObject()
      .put("title", title)
      .put("instanceId", instanceId)
      .put("barcode", barcode)

    def (createItemResponse, _) = client.post(ApiRoot.items(),
      Json.encodePrettily(newItemRequest))

    def instanceLocation = createItemResponse.headers.location.toString()

    def (response, createdItem) = client.get(instanceLocation)

    assert response.status == 200

    createdItem
  }
}
