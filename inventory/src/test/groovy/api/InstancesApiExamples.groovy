package api

import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import org.folio.metadata.common.testing.HttpClient
import spock.lang.Specification

class InstancesApiExamples extends Specification {
  private final HttpClient client = new HttpClient("test_tenant")

  def setup() {
    deleteInstances()
  }

  void "Can create instances"() {
    given:
      def newInstanceRequest = new JsonObject()
        .put("title", "Long Way to a Small Angry Planet")

    when:
      def (postResponse, _) = client.post(
        new URL("${inventoryApiRoot()}/instances"),
        Json.encodePrettily(newInstanceRequest))

    then:
      def location = postResponse.headers.location.toString()

      assert postResponse.status == 201
      assert location != null

      def (getResponse, createdInstance) = client.get(location)

      assert getResponse.status == 200

      assert new JsonObject(createdInstance)
        .getString("title") == "Long Way to a Small Angry Planet"
  }

  void "Instance title is mandatory"() {
    given:
    def newInstanceRequest = new JsonObject()

    when:
      def (postResponse, body) = client.post(
        new URL("${inventoryApiRoot()}/instances"),
        Json.encodePrettily(newInstanceRequest))

    then:
      assert postResponse.status == 400
      assert postResponse.headers.location == null
      assert body == "Title must be provided for an instance"
  }

  private String inventoryApiRoot() {
    def directAddress = new URL(
      "http://localhost:${ApiTestSuite.INVENTORY_VERTICLE_TEST_PORT}/inventory")

    def useOkapi = (System.getProperty("okapi.use") ?: "").toBoolean()

    useOkapi ?
      System.getProperty("okapi.address") + '/inventory'
      : directAddress
  }

  private void deleteInstances() {
    def (response, _) = client.delete(
      new URL("${inventoryApiRoot()}/instances"))

    assert response.status == 200
  }
}
