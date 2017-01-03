package org.folio.inventory.resources

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import io.vertx.groovy.ext.web.handler.BodyHandler
import org.apache.commons.lang.StringEscapeUtils
import org.folio.inventory.domain.Instance
import org.folio.inventory.storage.Storage
import org.folio.metadata.common.WebContext
import org.folio.metadata.common.api.request.VertxBodyParser
import org.folio.metadata.common.api.response.JsonResponse
import org.folio.metadata.common.api.response.RedirectResponse

class Instances {
  private final Storage storage

  Instances(final Storage storage) {
    this.storage = storage
  }

  public void register(Router router) {
    router.post(relativeInstancesPath() + "*").handler(BodyHandler.create())

    router.get(relativeInstancesPath()).handler(this.&getAll)
    router.post(relativeInstancesPath()).handler(this.&create)
    router.delete(relativeInstancesPath()).handler(this.&deleteAll)
    router.get(relativeInstancesPath() + "/:id").handler(this.&getById)
  }

  void getAll(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    storage.getInstanceCollection(context).findAll {
      JsonResponse.success(routingContext.response(),
        convertToUTF8(it))
    }
  }

  void create(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    Map instanceRequest = new VertxBodyParser().toMap(routingContext)

    def newInstance = new Instance(instanceRequest.title)

    storage.getInstanceCollection(context).add(newInstance, {
      RedirectResponse.created(routingContext.response(),
        context.absoluteUrl("${relativeInstancesPath()}/${it.id}").toString())
    })
  }

  void deleteAll(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    storage.getInstanceCollection(context).empty {
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

  private static String relativeInstancesPath() {
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
