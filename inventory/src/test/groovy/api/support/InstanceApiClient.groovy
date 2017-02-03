package api.support

import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import org.folio.metadata.common.testing.HttpClient

class InstanceApiClient {
  static def createInstance(HttpClient client, JsonObject newInstanceRequest) {
    def (postResponse, body) = client.post(
      new URL("${ApiRoot.inventory()}/instances"),
      Json.encodePrettily(newInstanceRequest))

    assert postResponse.status == 201
  }
}
