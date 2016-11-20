package org.folio.inventory.resources

import io.vertx.core.http.HttpMethod
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import org.folio.inventory.domain.InstanceCollection
import org.folio.metadata.common.api.response.ClientErrorResponse
import org.folio.metadata.common.api.response.JsonResponse

class Instances {

  private final InstanceCollection instanceCollection

  Instances(InstanceCollection instanceCollection) {
    this.instanceCollection = instanceCollection
  }

  public void register(Router router) {
    router.get(relativeItemsPath()).handler(this.&getAll)
    router.get(relativeItemsPath() + "/:id").handler(this.&getById)
  }

  void getAll(RoutingContext routingContext) {
    instanceCollection.findAll {
      JsonResponse.success(routingContext.response(), it)
    }
  }

  void getById(RoutingContext routingContext) {
    instanceCollection.findById(
      routingContext.request().getParam("id"),
      { JsonResponse.success(routingContext.response(), it) })
  }

  private static String relativeItemsPath() {
    "/inventory/instances"
  }
}
