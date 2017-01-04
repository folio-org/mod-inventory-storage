package api

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
    deleteInstances()
  }

  void "Can create an instance"() {
    given:
      def newInstanceRequest = new JsonObject()
        .put("title", "Long Way to a Small Angry Planet")

    when:
      def (postResponse, _) = client.post(
        new URL("${instancesRoot()}"),
        Json.encodePrettily(newInstanceRequest))

    then:
      def location = postResponse.headers.location.toString()

      assert postResponse.status == 201
      assert location != null

      def (getResponse, createdInstance) = client.get(location)

      assert getResponse.status == 200

      assert createdInstance.title == "Long Way to a Small Angry Planet"
      instanceExpressesDublinCoreMetadata(createdInstance)
  }

  void "Instance title is mandatory"() {
    given:
      def newInstanceRequest = new JsonObject()

    when:
      def (postResponse, body) = client.post(
        new URL("${instancesRoot()}"),
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
      deleteInstances()
      def (response, instances) = client.get(instancesRoot())

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
      def (response, instances) = client.get(instancesRoot())

    then:
      assert response.status == 200
      assert instances.size() == 3

      instances.each { instanceExpressesDublinCoreMetadata(it) }
  }

  void "Instance expresses title in Dublin Core metadata"() {
    given:
      createInstance("Long Way to a Small Angry Planet")

    when:
      def (response, instances) = client.get(instancesRoot())

    then:
      instanceExpressesDublinCoreMetadata(instances[0])
  }

  private URL instancesRoot() {
    new URL("${inventoryApiRoot()}/instances")
  }

  def createInstance(String title) {
    def newInstanceRequest = new JsonObject()
      .put("title", title)

    def (postResponse, body) = client.post(
      new URL("${inventoryApiRoot()}/instances"),
      Json.encodePrettily(newInstanceRequest))

    assert postResponse.status == 201
  }

  private String inventoryApiRoot() {
    "${ApiTestSuite.apiRoot()}/inventory"
  }

  private void deleteInstances() {
    def (response, _) = client.delete(
      new URL("${instancesRoot()}"))

    assert response.status == 200
  }

  private void instanceExpressesDublinCoreMetadata(instance) {
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
}
