package org.folio.inventory.resources

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import org.apache.commons.lang.StringEscapeUtils
import org.folio.inventory.domain.Instance
import org.folio.inventory.storage.Storage
import org.folio.metadata.common.WebContext
import org.folio.metadata.common.api.response.JsonResponse

class Instances {
  private final Storage storage

  Instances(final Storage storage) {
    this.storage = storage
  }

  public void register(Router router) {
    router.get(relativeItemsPath()).handler(this.&getAll)
    router.get(relativeItemsPath() + "/:id").handler(this.&getById)
  }

  void getAll(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    storage.getInstanceCollection(context).findAll {
      JsonResponse.success(routingContext.response(),
        convertToUTF8(it))
    }
  }

  void getById(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    storage.getInstanceCollection(context).findById(
      routingContext.request().getParam("id"),
      { JsonResponse.success(routingContext.response(), convertToUTF8(it)) })
  }

  private static String relativeItemsPath() {
    "/inventory/instances"
  }

  private JsonArray convertToUTF8(List<Instance> instances) {
    def result = new JsonArray()

    instances.each {
      result.add(convertToUTF8(it))
    }

    result
  }

  private JsonObject convertToUTF8(Instance instance) {
    def object = new JsonObject()
    object.put("id", instance.id)
    object.put("title", StringEscapeUtils.escapeJava(instance.title))
    object
  }
}
