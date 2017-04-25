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
  private final HttpClient client = ApiTestSuite.createHttpClient()

  def setup() {
    def preparation = new Preparation(client)

    preparation.deleteItems()
    preparation.deleteInstances()
  }

  void "Can create an item"() {
    given:
      def createdInstance = createInstance(
        smallAngryPlanet(UUID.randomUUID()))

      def newItemRequest = new JsonObject()
        .put("title", createdInstance.title)
        .put("instanceId", createdInstance.id)
        .put("barcode", "645398607547")
        .put("status", new JsonObject().put("name", "Available"))
        .put("materialType", new JsonObject()
          .put("id", "${ApiTestSuite.bookMaterialType}")
          .put("name", "Book"))
        .put("location", new JsonObject().put("name", "Annex Library"))

    when:
      def (postResponse, _) = client.post(
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
      assert createdItem?.status?.name == "Available"
      assert createdItem?.materialType?.id == ApiTestSuite.bookMaterialType
      assert createdItem?.materialType?.name == "Book"
      assert createdItem?.location?.name == "Annex Library"

      selfLinkRespectsWayResourceWasReached(createdItem)
      selfLinkShouldBeReachable(createdItem)
  }

  void "Can create an item with an ID"() {
    given:
      def createdInstance = createInstance(
        smallAngryPlanet(UUID.randomUUID()))

      def itemId = UUID.randomUUID().toString()

      def newItemRequest = new JsonObject()
        .put("id", itemId)
        .put("title", createdInstance.title)
        .put("instanceId", createdInstance.id)
        .put("materialType", new JsonObject()
          .put("id", "${ApiTestSuite.bookMaterialType}")
          .put("name", "Book"))
        .put("barcode", "645398607547")

    when:
      def (postResponse, __) = client.post(
        new URL("${ApiRoot.items()}"),
        Json.encodePrettily(newItemRequest))

    then:
      def location = postResponse.headers.location.toString()

      assert postResponse.status == 201
      assert location != null
      assert location.contains(itemId)

      def (getResponse, createdItem) = client.get(location)

      assert getResponse.status == 200

      assert createdItem.id == itemId

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
        .put("status", new JsonObject().put("name", "Available"))
        .put("materialType", new JsonObject()
          .put("id", "${ApiTestSuite.bookMaterialType}")
          .put("name", "Book"))
        .put("location", new JsonObject().put("name", "Annex Library"))

    when:
      def (postResponse, _) = client.post(
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
      assert createdItem?.status?.name == "Available"
      assert createdItem?.materialType?.id == ApiTestSuite.bookMaterialType
      assert createdItem?.materialType?.name == "Book"
      assert createdItem?.location?.name == "Annex Library"

      selfLinkRespectsWayResourceWasReached(createdItem)
      selfLinkShouldBeReachable(createdItem)
  }

  void "Can create an item without a material type"() {
    given:
      def createdInstance = createInstance(
        smallAngryPlanet(UUID.randomUUID()))

      def newItemRequest = new JsonObject()
        .put("title", createdInstance.title)
        .put("instanceId", createdInstance.id)
        .put("barcode", "645398607547")
        .put("status", new JsonObject().put("name", "Available"))
        .put("location", new JsonObject().put("name", "Annex Library"))

    when:
      def (postResponse, _) = client.post(
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
      assert createdItem?.status?.name == "Available"
      assert createdItem?.location?.name == "Annex Library"

      selfLinkRespectsWayResourceWasReached(createdItem)
      selfLinkShouldBeReachable(createdItem)
  }

  void "Can update an existing item"() {
    given:
      def createdInstance = createInstance(
        smallAngryPlanet(UUID.randomUUID()))

      def newItem = createItem(
        createdInstance.title, createdInstance.id, "645398607547")

      def updateItemRequest = newItem.copy()
        .put("status", new JsonObject().put("name", "Checked Out"))

      def itemLocation = new URL("${ApiRoot.items()}/${newItem.getString("id")}")

    when:
      def (putResponse, __) = client.put(itemLocation,
        Json.encodePrettily(updateItemRequest))

    then:
      assert putResponse.status == 204

      def (getResponse, updatedItem) = client.get(itemLocation)

      assert getResponse.status == 200

      assert updatedItem.id == newItem.getString("id")
      assert updatedItem.instanceId == createdInstance.id
      assert updatedItem.barcode == "645398607547"
      assert updatedItem?.status?.name == "Checked Out"
      assert updatedItem?.materialType?.id == ApiTestSuite.bookMaterialType
      assert updatedItem?.materialType?.name == "Book"
      assert updatedItem?.location?.name == "Main Library"

      selfLinkRespectsWayResourceWasReached(updatedItem)
      selfLinkShouldBeReachable(updatedItem)
  }

  void "Cannot update an item that does not exist"() {
    given:
      def updateItemRequest = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("title", "Nod")
        .put("instanceId", UUID.randomUUID().toString())
        .put("barcode", "546747342365")
        .put("status", new JsonObject().put("name", "Available"))
        .put("materialType", new JsonObject()
          .put("id", "${ApiTestSuite.bookMaterialType}")
          .put("name", "Book"))
        .put("location", new JsonObject().put("name", "Main Library"))

    when:
      def (putResponse, __) = client.put(
        new URL("${ApiRoot.items()}/${updateItemRequest.getString("id")}"),
        Json.encodePrettily(updateItemRequest))

    then:
      assert putResponse.status == 404
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

  void "Can delete a single item"() {
    given:
      def createdInstance = createInstance(
        smallAngryPlanet(UUID.randomUUID()))

      createItem(createdInstance.title, createdInstance.id,
        "645398607547")

      createItem(createdInstance.title, createdInstance.id,
        "175848607547")

      def itemToDelete = createItem(createdInstance.title, createdInstance.id,
        "645334645247")

      def itemToDeleteLocation =
        new URL("${ApiRoot.items()}/${itemToDelete.getString("id")}")

    when:
      def (deleteResponse, deleteBody) = client.delete(itemToDeleteLocation)

    then:
      assert deleteResponse.status == 204
      assert deleteBody == null

      def (getResponse, _) = client.get(itemToDeleteLocation)

      assert getResponse.status == 404

      def (__, getAllBody) = client.get(ApiRoot.items())

      assert getAllBody.items.size() == 2
  }

  void "Can page all items"() {
    given:
      def smallAngryInstance = createInstance(
        smallAngryPlanet(UUID.randomUUID()))

      createItem(smallAngryInstance.title, smallAngryInstance.id,
        "645398607547")

      createItem(smallAngryInstance.title, smallAngryInstance.id,
        "175848607547")

      def girlOnTheTrainInstance = createInstance(
        girlOnTheTrain(UUID.randomUUID()))

      createItem(girlOnTheTrainInstance.title, girlOnTheTrainInstance.id,
        "645334645247", dvdMaterialType())

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

      firstPage.items.each {
        hasMaterialType(it)
      }

      firstPage.items.each {
        hasStatus(it)
      }

      firstPage.items.each {
        hasLocation(it)
      }

      secondPage.items.each {
        selfLinkRespectsWayResourceWasReached(it)
      }

      secondPage.items.each {
        selfLinkShouldBeReachable(it)
      }

      secondPage.items.each {
        hasMaterialType(it)
      }

      secondPage.items.each {
        hasStatus(it)
      }

      secondPage.items.each {
        hasLocation(it)
      }
  }

  void "Page parameters must be numeric"() {
    when:
      def (response, message) = client.get(ApiRoot.items("limit=&offset="))

    then:
      assert response.status == 400
      assert message == "limit and offset must be numeric when supplied"
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
      assert firstItem.status.name == "Available"

      items.each {
        selfLinkRespectsWayResourceWasReached(it)
      }

      items.each {
        selfLinkShouldBeReachable(it)
      }

      items.each {
        hasMaterialType(it)
      }

      items.each {
        hasStatus(it)
      }

      items.each {
        hasLocation(it)
      }
  }

  void "Cannot create a second item with the same barcode"() {
    given:
      def smallAngryInstance = createInstance(
        smallAngryPlanet(UUID.randomUUID()))

      createItem(smallAngryInstance.title, smallAngryInstance.id,
        "645398607547")

      def nodInstance = createInstance(nod(UUID.randomUUID()))

    when:
      def newItemRequest = new JsonObject()
        .put("title", nodInstance.title)
        .put("instanceId", nodInstance.id)
        .put("barcode", "645398607547")
        .put("status", new JsonObject().put("name", "Available"))
        .put("materialType", new JsonObject()
          .put("id", "${ApiTestSuite.bookMaterialType}")
          .put("name", "Book"))
        .put("location", new JsonObject().put("name", "Main Library"))

      def (createItemResponse, createItemBody) = client.post(ApiRoot.items(),
        Json.encodePrettily(newItemRequest))

    then:
      assert createItemResponse.status == 400
      assert createItemBody == "Barcodes must be unique, 645398607547 is already assigned to another item"
  }

  void "Cannot update an item to have the same barcode as an existing item"() {
    given:
      def smallAngryInstance = createInstance(
        smallAngryPlanet(UUID.randomUUID()))

      createItem(smallAngryInstance.title, smallAngryInstance.id,
        "645398607547")

      def nodInstance = createInstance(nod(UUID.randomUUID()))

      def nodItem = createItem(nodInstance.title, nodInstance.id,
        "654647774352")

    when:
      def changedNodItem = nodItem.copy()
        .put("barcode", "645398607547")

      def nodItemLocation = new URL(
        "${ApiRoot.items()}/${changedNodItem.getString("id")}")

    def (putItemResponse, putItemBody) = client.put(nodItemLocation,
      Json.encodePrettily(changedNodItem));

    then:
      assert putItemResponse.status == 400
      assert putItemBody == "Barcodes must be unique, 645398607547 is already assigned to another item"
  }

  private void selfLinkRespectsWayResourceWasReached(item) {
    assert containsApiRoot(item.links.self)
  }

  private boolean containsApiRoot(String link) {
    link.contains(ApiTestSuite.apiRoot())
  }

  private void selfLinkShouldBeReachable(item) {
    def (response, _) = client.get(item.links.self)

    assert response.status == 200
  }

  private void hasStatus(item) {
    assert item?.status?.name != null
  }

  private void hasMaterialType(item) {
    assert item?.materialType?.id == ApiTestSuite.bookMaterialType ||
      item?.materialType?.id == ApiTestSuite.dvdMaterialType

    assert item?.materialType?.name == "Book" ||
      item?.materialType?.name == "DVD"
  }

  private void hasLocation(item) {
    assert item?.location?.name != null
  }

  private def createInstance(JsonObject newInstanceRequest) {
    InstanceApiClient.createInstance(client, newInstanceRequest)
  }

  private JsonObject createItem(String title, String instanceId, String barcode) {
    createItem(title, instanceId, barcode, bookMaterialType())
  }

  private JsonObject createItem(
    String title,
    String instanceId,
    String barcode,
    JsonObject materialType) {

    def newItemRequest = new JsonObject()
      .put("title", title)
      .put("instanceId", instanceId)
      .put("barcode", barcode)
      .put("status", new JsonObject().put("name", "Available"))
      .put("materialType", materialType)
      .put("location", new JsonObject().put("name", "Main Library"))

    def (createItemResponse, _) = client.post(ApiRoot.items(),
      Json.encodePrettily(newItemRequest))

    def instanceLocation = createItemResponse.headers.location.toString()

    def (response, createdItem) = client.get(instanceLocation)

    assert response.status == 200

    createdItem
  }

  private JsonObject bookMaterialType() {
    new JsonObject()
      .put("id", "${ApiTestSuite.bookMaterialType}")
      .put("name", "Book")
  }

  private JsonObject dvdMaterialType() {
    new JsonObject()
      .put("id", "${ApiTestSuite.dvdMaterialType}")
      .put("name", "DVD")
  }
}
