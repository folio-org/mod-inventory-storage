package org.folio.inventory.resources

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import org.apache.commons.lang.StringEscapeUtils
import org.folio.inventory.domain.Item
import org.folio.inventory.storage.Storage
import org.folio.metadata.common.WebContext
import org.folio.metadata.common.api.response.JsonResponse

class Items {

  private final Storage storage

  Items(final Storage storage) {
    this.storage = storage
  }

  public void register(Router router) {
    router.get(relativeItemsPath()).handler(this.&getAll)
  }

  void getAll(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    storage.getItemCollection(context).findAll {
      JsonResponse.success(routingContext.response(), convertToUTF8(it))
    }
  }

  private static String relativeItemsPath() {
    "/inventory/items"
  }

  private JsonArray convertToUTF8(List<Item> items) {
    def result = new JsonArray()

    items.each {
      result.add(convertToUTF8(it))
    }

    result
  }

  private JsonObject convertToUTF8(Item item) {
    def object = new JsonObject()
    object.put("id", item.id)
    object.put("instanceId", item.instanceId)
    object.put("title", StringEscapeUtils.escapeJava(item.title))
    object.put("barcode", item.barcode)
    object
  }
}
