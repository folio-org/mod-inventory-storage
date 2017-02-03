package api.support

import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import org.folio.metadata.common.testing.HttpClient

class InstanceApiClient {
  static def createInstance(HttpClient client, JsonObject newInstanceRequest) {
    def (createInstanceResponse, _) = client.post(ApiRoot.instances(),
      Json.encodePrettily(newInstanceRequest))

    def instanceLocation = createInstanceResponse.headers.location.toString()

    def (response, createdInstance) = client.get(instanceLocation)

    assert response.status == 200

    createdInstance
  }
}
