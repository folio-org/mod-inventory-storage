package org.folio.inventory.resources

import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import org.folio.inventory.domain.ItemCollection
import org.folio.metadata.common.api.response.JsonResponse

class Items {

  private final ItemCollection itemCollection

  Items(ItemCollection itemCollection) {
    this.itemCollection = itemCollection
  }

  public void register(Router router) {
    router.get(relativeItemsPath()).handler(this.&getAll)
  }

  void getAll(RoutingContext routingContext) {
    itemCollection.findAll {
      JsonResponse.success(routingContext.response(), it)
    }
  }

  private static String relativeItemsPath() {
    "/inventory/items"
  }
}
