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
      def newInstanceRequest = new JsonObject()
        .put("title", "Long Way to a Small Angry Planet")
        .put("identifiers", [[namespace: "isbn", value: "9781473619777"]]);

      def (createInstanceResponse, _) = client.post(ApiRoot.instances(),
        Json.encodePrettily(newInstanceRequest))

      def instanceLocation = createInstanceResponse.headers.location.toString()

      def (getInstanceResponse, createdInstance) = client.get(instanceLocation)

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
}
