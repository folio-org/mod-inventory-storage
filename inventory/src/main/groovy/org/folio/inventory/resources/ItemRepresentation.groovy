package org.folio.inventory.resources

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.folio.inventory.domain.Item
import org.folio.metadata.common.WebContext

class ItemRepresentation {
  private final String relativeItemsPath

  def ItemRepresentation(String relativeItemsPath) {
    this.relativeItemsPath = relativeItemsPath
  }

  JsonObject toJson(Item item, WebContext context) {
    def representation = new JsonObject()
    representation.put("id", item.id)
    representation.put("instanceId", item.instanceId)
    representation.put("title", item.title)
    representation.put("barcode", item.barcode)

    if(item.status != null) {
      representation.put("status", new JsonObject().put("name", item.status))
    }

    representation.put('links',
      ['self': context.absoluteUrl(
        "${relativeItemsPath}/${item.id}").toString()])

    representation
  }

  JsonObject toJson(List<Item> items,
                    WebContext context) {

    def representation = new JsonObject()

    def results = new JsonArray()

    items.each { item ->
      results.add(toJson(item, context))
    }

    representation.put("items", results)

    representation
  }
}
