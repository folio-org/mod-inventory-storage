package api.support

import org.folio.metadata.common.testing.HttpClient

class Preparation {
  private final HttpClient client

  def Preparation(HttpClient client) {
    this.client = client
  }

  void deleteInstances() {
    def (response, _) = client.delete(ApiRoot.instances())

    assert response.status == 204
  }

  void deleteItems() {
    def (response, _) = client.delete(ApiRoot.items())

    assert response.status == 204
  }
}
