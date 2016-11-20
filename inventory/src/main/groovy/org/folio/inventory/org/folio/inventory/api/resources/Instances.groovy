package org.folio.inventory.org.folio.inventory.api.resources

import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import org.folio.inventory.domain.Instance
import org.folio.inventory.domain.ItemCollection
import org.folio.metadata.common.api.response.JsonResponse

class Instances {

  private final ItemCollection itemCollection

  Instances(ItemCollection itemCollection) {
    this.itemCollection = itemCollection
  }

  public void register(Router router) {
    router.get(relativeItemsPath()).handler(this.&getAll)
  }

  void getAll(RoutingContext routingContext) {
    itemCollection.findAll {
      JsonResponse.success(routingContext.response(),
        it.stream()
          .map({ new Instance(it.id, it.title) })
          .collect())
    }
  }

  private static String relativeItemsPath() {
    "/inventory/instances"
  }
}
