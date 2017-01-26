package org.folio.inventory.resources

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import io.vertx.groovy.ext.web.handler.BodyHandler
import org.folio.inventory.domain.Instance
import org.folio.inventory.storage.Storage
import org.folio.metadata.common.WebContext
import org.folio.metadata.common.api.request.PagingParameters
import org.folio.metadata.common.api.request.VertxBodyParser
import org.folio.metadata.common.api.response.ClientErrorResponse
import org.folio.metadata.common.api.response.JsonResponse
import org.folio.metadata.common.api.response.RedirectResponse
import org.folio.metadata.common.api.response.SuccessResponse

class Instances {
  private final Storage storage

  Instances(final Storage storage) {
    this.storage = storage
  }

  public void register(Router router) {
    router.post(relativeInstancesPath() + "*").handler(BodyHandler.create())

    router.get(relativeInstancesPath() + "/context")
      .handler(this.&getMetadataContext)

    router.get(relativeInstancesPath()).handler(this.&getAll)
    router.post(relativeInstancesPath()).handler(this.&create)
    router.delete(relativeInstancesPath()).handler(this.&deleteAll)

    router.get(relativeInstancesPath() + "/:id").handler(this.&getById)
  }

  void getMetadataContext(RoutingContext routingContext) {
    def representation = [:]

    representation."@context" = [
      "dcterms": "http://purl.org/dc/terms/",
      "title"  : "dcterms:title"
    ]

    JsonResponse.success(routingContext.response(),
        representation)
  }

  void getAll(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    def limit = context.getIntegerParameter("limit", 10)
    def offset = context.getIntegerParameter("offset", 0)
    def search = context.getStringParameter("query", null)

    if(search == null) {
      storage.getInstanceCollection(context).findAll(
        new PagingParameters(limit, offset), {
        JsonResponse.success(routingContext.response(),
          toRepresentation(it, context))
      })
    }
    else {
      storage.getInstanceCollection(context).findByCql(search,
        new PagingParameters(limit, offset), {
        JsonResponse.success(routingContext.response(),
          toRepresentation(it, context))
      })
    }
  }

  void create(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    Map instanceRequest = new VertxBodyParser().toMap(routingContext)

    if(isEmpty(instanceRequest.title)) {
      ClientErrorResponse.badRequest(routingContext.response(),
        "Title must be provided for an instance")
      return
    }

    def newInstance = new Instance(instanceRequest.title,
      instanceRequest.identifiers)

    storage.getInstanceCollection(context).add(newInstance, {
      RedirectResponse.created(routingContext.response(),
        context.absoluteUrl("${relativeInstancesPath()}/${it.id}").toString())
    })
  }

  void deleteAll(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    storage.getInstanceCollection(context).empty {
      SuccessResponse.noContent(routingContext.response())
    }
  }

  void getById(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    storage.getInstanceCollection(context).findById(
      routingContext.request().getParam("id"),
      {
        if(it != null) {
          JsonResponse.success(routingContext.response(),
            toRepresentation(it, context))
        }
        else {
          ClientErrorResponse.notFound(routingContext.response())
        }
      })
  }

  private static String relativeInstancesPath() {
    "/inventory/instances"
  }

  private JsonObject toRepresentation(List<Instance> instances, WebContext context) {
    def representation = new JsonObject()

    def results = new JsonArray()

    instances.each {
      results.add(toRepresentation(it, context))
    }

    representation.put("instances", results)

    representation
  }

  private JsonObject toRepresentation(Instance instance, WebContext context) {
    def representation = new JsonObject()

    representation.put("@context", context.absoluteUrl(
      relativeInstancesPath() + "/context").toString())

    representation.put("id", instance.id)
    representation.put("title", instance.title)

    def identifiers = []

    instance.identifiers.each { identifier ->
      def identifierRepresentation = [:]

      identifierRepresentation.namespace = identifier.namespace
      identifierRepresentation.value = identifier.value

      identifiers.add(identifierRepresentation)
    }

    representation.put('identifiers', identifiers)

    representation.put('links',
      ['self': context.absoluteUrl(
        relativeInstancesPath() + "/${instance.id}").toString()])

    representation
  }

  private boolean isEmpty(String string) {
    string == null || string.trim().length() == 0
  }
}
