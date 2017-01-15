package api

import api.support.ApiRoot
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

class InstancesApiExamples extends Specification {
  private final String TENANT_ID = "test_tenant"

  private final HttpClient client = new HttpClient(TENANT_ID)

  def setup() {
    new Preparation(client).deleteInstances()
  }

  void "Can create an instance"() {
    given:
      def newInstanceRequest = new JsonObject()
        .put("title", "Long Way to a Small Angry Planet")
        .put("identifiers", [[namespace: "isbn", value: "9781473619777"]]);

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

  void "Can delete all instances"() {
    given:
      createInstance("Long Way to a Small Angry Planet")
      createInstance("Nod")
      createInstance("Leviathan Wakes")

    when:
      def (_, __) = client.delete(ApiRoot.instances())

      def (response, instances) = client.get(ApiRoot.instances())

    then:
      assert response.status == 200
      assert instances.size() == 0
  }

  void "Can get all instances"() {
    given:
      createInstance("Long Way to a Small Angry Planet")
      createInstance("Nod")
      createInstance("Leviathan Wakes")

    when:
      def (response, instances) = client.get(ApiRoot.instances())

    then:
      assert response.status == 200
      assert instances.size() == 3

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

  void "Cannot find an unknown resource"() {
    when:
      def (response, _) = client.get("${ApiRoot.instances()}/${UUID.randomUUID()}")

    then:
      assert response.status == 404
  }

  private def createInstance(String title) {
    def newInstanceRequest = new JsonObject()
      .put("title", title)

    def (postResponse, body) = client.post(
      new URL("${ApiRoot.inventory()}/instances"),
      Json.encodePrettily(newInstanceRequest))

    assert postResponse.status == 201
  }

  private void expressesDublinCoreMetadata(instance) {
    def options = new JsonLdOptions()
    def documentLoader = new DocumentLoader()
    def httpClient = CachingHttpClientBuilder
      .create()
      .setDefaultHeaders([new BasicHeader('X-Okapi-Tenant', TENANT_ID)])
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
