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
import org.folio.metadata.common.api.response.*
import org.folio.metadata.common.domain.Success

class Instances {
  private final Storage storage

  Instances(final Storage storage) {
    this.storage = storage
  }

  public void register(Router router) {
    router.post(relativeInstancesPath() + "*").handler(BodyHandler.create())
    router.put(relativeInstancesPath() + "*").handler(BodyHandler.create())

    router.get(relativeInstancesPath() + "/context")
      .handler(this.&getMetadataContext)

    router.get(relativeInstancesPath()).handler(this.&getAll)
    router.post(relativeInstancesPath()).handler(this.&create)
    router.delete(relativeInstancesPath()).handler(this.&deleteAll)

    router.get(relativeInstancesPath() + "/:id").handler(this.&getById)
    router.put(relativeInstancesPath() + "/:id").handler(this.&update)
    router.delete(relativeInstancesPath() + "/:id").handler(this.&deleteById)
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

    def search = context.getStringParameter("query", null)

    def pagingParameters = PagingParameters.from(context)

    if(pagingParameters == null) {
      ClientErrorResponse.badRequest(routingContext.response(),
        "limit and offset must be numeric when supplied")

      return
    }

    if(search == null) {
      storage.getInstanceCollection(context).findAll(
        pagingParameters,
        { Success success -> JsonResponse.success(routingContext.response(),
          toRepresentation(success.result, context)) },
        FailureResponseConsumer.serverError(routingContext.response()))
    }
    else {
      storage.getInstanceCollection(context).findByCql(search,
        pagingParameters, {
        JsonResponse.success(routingContext.response(),
          toRepresentation(it.result, context))
      }, FailureResponseConsumer.serverError(routingContext.response()))
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

    def newInstance = requestToInstance(instanceRequest)

    storage.getInstanceCollection(context).add(newInstance,
      { Success success ->
        RedirectResponse.created(routingContext.response(),
        context.absoluteUrl(
          "${relativeInstancesPath()}/${success.result.id}").toString())
      }, FailureResponseConsumer.serverError(routingContext.response()))
  }

  void update(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    Map instanceRequest = new VertxBodyParser().toMap(routingContext)

    def updatedInstance = requestToInstance(instanceRequest)

    def instanceCollection = storage.getInstanceCollection(context)

    instanceCollection.findById(routingContext.request().getParam("id"),
      { Success it ->
        if(it.result != null) {
          instanceCollection.update(updatedInstance, {
            SuccessResponse.noContent(routingContext.response()) },
            FailureResponseConsumer.serverError(routingContext.response()))
        }
        else {
          ClientErrorResponse.notFound(routingContext.response())
        }
      }, FailureResponseConsumer.serverError(routingContext.response()))
  }

  void deleteAll(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    storage.getInstanceCollection(context).empty (
      { SuccessResponse.noContent(routingContext.response()) },
      FailureResponseConsumer.serverError(routingContext.response()))
  }

  void deleteById(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    storage.getInstanceCollection(context).delete(
      routingContext.request().getParam("id"),
      { SuccessResponse.noContent(routingContext.response()) },
      FailureResponseConsumer.serverError(routingContext.response()))
  }

  void getById(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    storage.getInstanceCollection(context).findById(
      routingContext.request().getParam("id"),
      { Success it ->
        if(it.result != null) {
          JsonResponse.success(routingContext.response(),
            toRepresentation(it.result, context))
        }
        else {
          ClientErrorResponse.notFound(routingContext.response())
        }
      }, FailureResponseConsumer.serverError(routingContext.response()))
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

  private Instance requestToInstance(Map<String, Object> instanceRequest) {
    new Instance(instanceRequest.id, instanceRequest.title,
      instanceRequest.identifiers)
  }

  private boolean isEmpty(String string) {
    string == null || string.trim().length() == 0
  }
}
