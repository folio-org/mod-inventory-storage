package org.folio.inventory.org.folio.inventory.api.resources

import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import org.folio.inventory.domain.InstanceCollection
import org.folio.metadata.common.api.response.JsonResponse

class Instances {

  private final InstanceCollection instanceCollection

  Instances(InstanceCollection instanceCollection) {
    this.instanceCollection = instanceCollection
  }

  public void register(Router router) {
    router.get(relativeItemsPath()).handler(this.&getAll)
  }

  void getAll(RoutingContext routingContext) {
    instanceCollection.findAll {
      JsonResponse.success(routingContext.response(), it)
    }
  }

  private static String relativeItemsPath() {
    "/inventory/instances"
  }
}
