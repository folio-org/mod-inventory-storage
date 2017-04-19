package api

import api.support.ApiRoot
import api.support.InstanceApiClient
import api.support.Preparation
import com.github.jsonldjava.core.DocumentLoader
import com.github.jsonldjava.core.JsonLdOptions
import com.github.jsonldjava.core.JsonLdProcessor
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import org.apache.http.impl.client.cache.CachingHttpClientBuilder
import org.apache.http.message.BasicHeader
import org.folio.metadata.common.testing.HttpClient
import spock.lang.Specification

import static api.support.InstanceSamples.*

class InstancesApiExamples extends Specification {
  private final HttpClient client = ApiTestSuite.createHttpClient()

  def setup() {
    new Preparation(client).deleteInstances()
  }

  void "Can create an instance"() {
    given:
      def newInstanceRequest = new JsonObject()
        .put("title", "Long Way to a Small Angry Planet")
        .put("identifiers", [[namespace: "isbn", value: "9781473619777"]])

    when:
      def (postResponse, _) = client.post(ApiRoot.instances(),
        Json.encodePrettily(newInstanceRequest))

    then:
      def location = postResponse.headers.location.toString()

      assert postResponse.status == 201
      assert location != null

      def (getResponse, createdInstance) = client.get(location)

      assert getResponse.status == 200

      assert createdInstance.id != null
      assert createdInstance.title == "Long Way to a Small Angry Planet"
      assert createdInstance.identifiers[0].namespace == "isbn"
      assert createdInstance.identifiers[0].value == "9781473619777"

      expressesDublinCoreMetadata(createdInstance)
      dublinCoreContextLinkRespectsWayResourceWasReached(createdInstance)
      selfLinkRespectsWayResourceWasReached(createdInstance)
      selfLinkShouldBeReachable(createdInstance)
  }

  void "Can create an instance with an ID"() {
    given:
      def instanceId = UUID.randomUUID().toString()

      def newInstanceRequest = new JsonObject()
        .put("id", instanceId)
        .put("title", "Long Way to a Small Angry Planet")

    when:
      def (postResponse, _) = client.post(ApiRoot.instances(),
        Json.encodePrettily(newInstanceRequest))

    then:
      def location = postResponse.headers.location.toString()

      assert postResponse.status == 201
      assert location != null
      assert location.contains(instanceId)

      def (getResponse, createdInstance) = client.get(location)

      assert getResponse.status == 200

      assert createdInstance.id == instanceId
      assert createdInstance.title == "Long Way to a Small Angry Planet"

      expressesDublinCoreMetadata(createdInstance)
      dublinCoreContextLinkRespectsWayResourceWasReached(createdInstance)
      selfLinkRespectsWayResourceWasReached(createdInstance)
      selfLinkShouldBeReachable(createdInstance)
  }

  void "Instance title is mandatory"() {
    given:
      def newInstanceRequest = new JsonObject()

    when:
      def (postResponse, body) = client.post(
        new URL("${ApiRoot.instances()}"),
        Json.encodePrettily(newInstanceRequest))

    then:
      assert postResponse.status == 400
      assert postResponse.headers.location == null
      assert body == "Title must be provided for an instance"
  }

  void "Can update an existing instance"() {

    given:
      def id = UUID.randomUUID()

      def newInstance = createInstance(smallAngryPlanet(id))

      def updateInstanceRequest = smallAngryPlanet(id)
        .put("title", "The Long Way to a Small, Angry Planet")

      def instanceLocation =
        new URL("${ApiRoot.instances()}/${newInstance.id}")

    when:
      def (putResponse, __) = client.put(instanceLocation,
        Json.encodePrettily(updateInstanceRequest))

    then:
      assert putResponse.status == 204

      def (getResponse, updatedInstance) = client.get(instanceLocation)

      assert getResponse.status == 200

      assert updatedInstance.id == newInstance.id
      assert updatedInstance.title == "The Long Way to a Small, Angry Planet"
      assert updatedInstance.identifiers.size() == 1

      selfLinkRespectsWayResourceWasReached(updatedInstance)
      selfLinkShouldBeReachable(updatedInstance)
  }

  void "Cannot update an instance that does not exist"() {
    given:
      def updateInstanceRequest = smallAngryPlanet(UUID.randomUUID())

    when:
      def (putResponse, __) = client.put(
        new URL("${ApiRoot.items()}/${updateInstanceRequest.getString("id")}"),
        Json.encodePrettily(updateInstanceRequest))

    then:
      assert putResponse.status == 404
  }

  void "Can delete all instances"() {
    given:
      createInstance(smallAngryPlanet(UUID.randomUUID()))
      createInstance(nod(UUID.randomUUID()))
      createInstance(leviathanWakes(UUID.randomUUID()))

    when:
      def (deleteResponse, deleteBody) = client.delete(ApiRoot.instances())

      def (_, body) = client.get(ApiRoot.instances())
      def instances = body.instances

    then:
      assert deleteResponse.status == 204
      assert deleteBody == null

      assert instances.size() == 0
  }

  void "Can delete a single instance"() {
    given:
      createInstance(smallAngryPlanet(UUID.randomUUID()))
      createInstance(nod(UUID.randomUUID()))
      def instanceToDelete = createInstance(leviathanWakes(UUID.randomUUID()))

      def instanceToDeleteLocation =
        new URL("${ApiRoot.instances()}/${instanceToDelete.id}")

    when:
      def (deleteResponse, deleteBody) = client.delete(instanceToDeleteLocation)

    then:
      assert deleteResponse.status == 204
      assert deleteBody == null

      def (getResponse, _) = client.get(instanceToDeleteLocation)

      assert getResponse.status == 404

      def (__, getAllBody) = client.get(ApiRoot.instances())

      assert getAllBody.instances.size() == 2
  }

  void "Can get all instances"() {
    given:
      createInstance(smallAngryPlanet(UUID.randomUUID()))
      createInstance(nod(UUID.randomUUID()))
      createInstance(temeraire(UUID.randomUUID()))

    when:
      def (response, body) = client.get(ApiRoot.instances())
      def instances = body.instances

    then:
      assert response.status == 200
      assert instances.size() == 3

      hasCollectionProperties(instances)
  }

  void "Can page all instances"() {
    given:
    createInstance(smallAngryPlanet(UUID.randomUUID()))
    createInstance(nod(UUID.randomUUID()))
    createInstance(temeraire(UUID.randomUUID()))
    createInstance(leviathanWakes(UUID.randomUUID()))
    createInstance(taoOfPooh(UUID.randomUUID()))

    when:
      def (firstPageResponse, firstPage) = client.get(
        ApiRoot.instances("limit=3"))

      def (secondPageResponse, secondPage) = client.get(
        ApiRoot.instances("limit=3&offset=3"))

    then:
      assert firstPageResponse.status == 200
      assert firstPage.instances.size() == 3

      assert secondPageResponse.status == 200
      assert secondPage.instances.size() == 2

      hasCollectionProperties(firstPage.instances)
      hasCollectionProperties(secondPage.instances)
  }

  void "Page parameters must be numeric"() {
    when:
      def (response, message) = client.get(ApiRoot.instances("limit=&offset="))

    then:
      assert response.status == 400
      assert message == "limit and offset must be numeric when supplied"
  }

  void "Can search for instances by title"() {
    given:
      createInstance(smallAngryPlanet(UUID.randomUUID()))
      createInstance(nod(UUID.randomUUID()))
      createInstance(uprooted(UUID.randomUUID()))

    when:
      def (response, body) = client.get(
        ApiRoot.instances("query=title=*Small%20Angry*"))

      def instances = body.instances

    then:
      assert response.status == 200
      assert instances.size() == 1

      assert instances[0].title == "Long Way to a Small Angry Planet"

      hasCollectionProperties(instances)
  }

  void "Cannot find an unknown resource"() {
    when:
      def (response, _) = client.get("${ApiRoot.instances()}/${UUID.randomUUID()}")

    then:
      assert response.status == 404
  }

  private void hasCollectionProperties(instances) {

    instances.each {
      expressesDublinCoreMetadata(it)
    }

    instances.each {
      dublinCoreContextLinkRespectsWayResourceWasReached(it)
    }

    instances.each {
      selfLinkRespectsWayResourceWasReached(it)
    }

    instances.each {
      selfLinkShouldBeReachable(it)
    }
  }

  private void expressesDublinCoreMetadata(instance) {
    def options = new JsonLdOptions()
    def documentLoader = new DocumentLoader()
    def httpClient = CachingHttpClientBuilder
      .create()
      .setDefaultHeaders([new BasicHeader('X-Okapi-Tenant', ApiTestSuite.TENANT_ID)])
      .build()

    documentLoader.setHttpClient(httpClient)

    options.setDocumentLoader(documentLoader)

    def expandedLinkedData = JsonLdProcessor.expand(instance, options)

    assert expandedLinkedData.empty == false: "No Linked Data present"
    assert LinkedDataValue(expandedLinkedData,
      "http://purl.org/dc/terms/title") == instance.title
  }

  private static String LinkedDataValue(List<Object> expanded, String field) {
    expanded[0][field][0]?."@value"
  }

  private def createInstance(JsonObject newInstanceRequest) {
    InstanceApiClient.createInstance(client, newInstanceRequest)
  }

  private void selfLinkShouldBeReachable(instance) {
    def (response, _) = client.get(instance.links.self)

    assert response.status == 200
  }

  private void dublinCoreContextLinkRespectsWayResourceWasReached(instance) {
    assert containsApiRoot(instance."@context")
  }

  private void selfLinkRespectsWayResourceWasReached(instance) {
    assert containsApiRoot(instance.links.self)
  }

  private boolean containsApiRoot(String link) {
    link.contains(ApiTestSuite.apiRoot())
  }
}
