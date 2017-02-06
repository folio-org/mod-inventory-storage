package api

import api.support.ApiRoot
import api.support.InstanceApiClient
import api.support.Preparation
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import org.folio.metadata.common.testing.HttpClient
import spock.lang.Specification

import static api.support.InstanceSamples.*

class ItemApiExamples extends Specification {
  private final String TENANT_ID = "test_tenant"

  private final HttpClient client = new HttpClient(TENANT_ID)

  def setup() {
    new Preparation(client).deleteItems()
    new Preparation(client).deleteInstances()
  }

  void "Can create an item"() {
    given:
      def createdInstance = createInstance(
        smallAngryPlanet(UUID.randomUUID()))

      def newItemRequest = new JsonObject()
        .put("title", createdInstance.title)
        .put("instanceId", createdInstance.id)
        .put("barcode", "645398607547")
        .put("status", new JsonObject().put("name", "available"))

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
      assert createdItem.barcode == "645398607547"
      assert createdItem.status.name == "available"

      selfLinkRespectsWayResourceWasReached(createdItem)
      selfLinkShouldBeReachable(createdItem)
  }

  void "Can create an item based upon an instance"() {
    given:
      def createdInstance = createInstance(
        smallAngryPlanet(UUID.randomUUID()))

      def newItemRequest = new JsonObject()
        .put("title", createdInstance.title)
        .put("instanceId", createdInstance.id)
        .put("barcode", "645398607547")
        .put("status", new JsonObject().put("name", "available"))

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
      assert createdItem.instanceId == createdInstance.id
      assert createdItem.barcode == "645398607547"
      assert createdItem.status.name == "available"

      selfLinkRespectsWayResourceWasReached(createdItem)
      selfLinkShouldBeReachable(createdItem)
  }

  void "Can delete all items"() {
    given:
      def createdInstance = createInstance(
        smallAngryPlanet(UUID.randomUUID()))

      createItem(createdInstance.title, createdInstance.id,
        "645398607547")

      createItem(createdInstance.title, createdInstance.id,
        "175848607547")

      createItem(createdInstance.title, createdInstance.id,
        "645334645247")

    when:
      def (deleteResponse, deleteBody) = client.delete(ApiRoot.items())

      def (_, body) = client.get(ApiRoot.items())

    then:
      assert deleteResponse.status == 204
      assert deleteBody == null

      assert body.items.size() == 0
  }

  void "Can page all items"() {
    given:
      def smallAngryInstance = createInstance(
        smallAngryPlanet(UUID.randomUUID()))

      createItem(smallAngryInstance.title, smallAngryInstance.id,
        "645398607547")

      createItem(smallAngryInstance.title, smallAngryInstance.id,
        "175848607547")

      createItem(smallAngryInstance.title, smallAngryInstance.id,
        "645334645247")

      def nodInstance = createInstance(nod(UUID.randomUUID()))

      createItem(nodInstance.title, nodInstance.id, "564566456546")

      createItem(nodInstance.title, nodInstance.id, "943209584495")

    when:
      def (firstPageResponse, firstPage) = client.get(
        ApiRoot.items("limit=3"))

      def (secondPageResponse, secondPage) = client.get(
        ApiRoot.items("limit=3&offset=3"))

    then:
      assert firstPageResponse.status == 200
      assert firstPage.items.size() == 3

      assert secondPageResponse.status == 200
      assert secondPage.items.size() == 2

      firstPage.items.each {
        selfLinkRespectsWayResourceWasReached(it)
      }

      firstPage.items.each {
        selfLinkShouldBeReachable(it)
      }

      secondPage.items.each {
        selfLinkRespectsWayResourceWasReached(it)
      }

      secondPage.items.each {
        selfLinkShouldBeReachable(it)
      }
  }

  void "Can search for items by title"() {
    given:
    def smallAngryInstance = createInstance(
      smallAngryPlanet(UUID.randomUUID()))

      createItem(smallAngryInstance.title, smallAngryInstance.id,
        "645398607547")

      def nodInstance = createInstance(nod(UUID.randomUUID()))

      createItem(nodInstance.title, nodInstance.id, "564566456546")

    when:
      def (response, body) = client.get(
        ApiRoot.items("query=title=*Small%20Angry*"))

    then:
      assert response.status == 200

      def items = body.items

      assert items.size() == 1

      def firstItem = items[0]

      assert firstItem.title == "Long Way to a Small Angry Planet"
      assert firstItem.status.name == "available"

      items.each {
        selfLinkRespectsWayResourceWasReached(it)
      }

      items.each {
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

  private def createInstance(JsonObject newInstanceRequest) {
    InstanceApiClient.createInstance(client, newInstanceRequest)
  }

  private def createItem(String title, String instanceId, String barcode) {
    def newItemRequest = new JsonObject()
      .put("title", title)
      .put("instanceId", instanceId)
      .put("barcode", barcode)
      .put("status", new JsonObject().put("name", "available"))

    def (createItemResponse, _) = client.post(ApiRoot.items(),
      Json.encodePrettily(newItemRequest))

    def instanceLocation = createItemResponse.headers.location.toString()

    def (response, createdItem) = client.get(instanceLocation)

    assert response.status == 200

    createdItem
  }
}
