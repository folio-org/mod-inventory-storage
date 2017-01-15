package org.folio.inventory.resources

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import io.vertx.groovy.ext.web.handler.BodyHandler
import org.apache.commons.lang.StringEscapeUtils
import org.folio.inventory.domain.Instance
import org.folio.inventory.domain.Item
import org.folio.inventory.storage.Storage
import org.folio.metadata.common.WebContext
import org.folio.metadata.common.api.request.VertxBodyParser
import org.folio.metadata.common.api.response.ClientErrorResponse
import org.folio.metadata.common.api.response.JsonResponse
import org.folio.metadata.common.api.response.RedirectResponse

class Items {

  private final Storage storage

  Items(final Storage storage) {
    this.storage = storage
  }

  public void register(Router router) {
    router.post(relativeItemsPath() + "*").handler(BodyHandler.create())

    router.get(relativeItemsPath()).handler(this.&getAll)
    router.post(relativeItemsPath()).handler(this.&create)
    router.delete(relativeItemsPath()).handler(this.&deleteAll)

    router.get(relativeItemsPath() + "/:id").handler(this.&getById)
  }

  void getAll(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    storage.getItemCollection(context).findAll {
      JsonResponse.success(routingContext.response(),
        convertToUTF8(it, context))
    }
  }

  void deleteAll(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    storage.getItemCollection(context).empty {
      JsonResponse.success(routingContext.response(),
        convertToUTF8(it, context))
    }
  }

  void create(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    Map itemRequest = new VertxBodyParser().toMap(routingContext)

    def newItem = new Item(itemRequest.title,
      itemRequest.barcode, itemRequest.instanceId)

    storage.getItemCollection(context).add(newItem, {
      RedirectResponse.created(routingContext.response(),
        context.absoluteUrl("${relativeItemsPath()}/${it.id}").toString())
    })
  }

  void getById(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    storage.getItemCollection(context).findById(
      routingContext.request().getParam("id"),
      {
        if(it != null) {
          JsonResponse.success(routingContext.response(),
            convertToUTF8(it, context))
        }
        else {
          ClientErrorResponse.notFound(routingContext.response())
        }
      })
  }

  private static String relativeItemsPath() {
    "/inventory/items"
  }

  private JsonArray convertToUTF8(List<Item> items, WebContext context) {
    def result = new JsonArray()

    items.each {
      result.add(convertToUTF8(it, context))
    }

    result
  }

  private JsonObject convertToUTF8(Item item, WebContext context) {
    def representation = new JsonObject()
    representation.put("id", item.id)
    representation.put("instanceId", item.instanceId)
    representation.put("title", StringEscapeUtils.escapeJava(item.title))
    representation.put("barcode", item.barcode)

    representation.put('links',
      ['self': context.absoluteUrl(
        relativeItemsPath() + "/${item.id}").toString()])

    representation
  }
}
