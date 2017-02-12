package org.folio.inventory.resources

import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import io.vertx.groovy.ext.web.handler.BodyHandler
import org.folio.inventory.domain.Item
import org.folio.inventory.storage.Storage
import org.folio.metadata.common.WebContext
import org.folio.metadata.common.api.request.PagingParameters
import org.folio.metadata.common.api.request.VertxBodyParser
import org.folio.metadata.common.api.response.*
import org.folio.metadata.common.domain.Failure
import org.folio.metadata.common.domain.Success

class Items {

  private final Storage storage

  Items(final Storage storage) {
    this.storage = storage
  }

  public void register(Router router) {
    router.post(relativeItemsPath() + "*").handler(BodyHandler.create())
    router.put(relativeItemsPath() + "*").handler(BodyHandler.create())

    router.get(relativeItemsPath()).handler(this.&getAll)
    router.post(relativeItemsPath()).handler(this.&create)
    router.delete(relativeItemsPath()).handler(this.&deleteAll)

    router.get(relativeItemsPath() + "/:id").handler(this.&getById)
    router.put(relativeItemsPath() + "/:id").handler(this.&update)
    router.delete(relativeItemsPath() + "/:id").handler(this.&deleteById)
  }

  void getAll(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    def limit = context.getIntegerParameter("limit", 10)
    def offset = context.getIntegerParameter("offset", 0)
    def search = context.getStringParameter("query", null)

    if(search == null) {
      storage.getItemCollection(context).findAll(
        new PagingParameters(limit, offset),
        { Success success ->
        JsonResponse.success(routingContext.response(),
          new ItemRepresentation(relativeItemsPath()).toJson(success.result,
            context)) },
        FailureResponseConsumer.serverError(routingContext.response()))
    }
    else {
      storage.getItemCollection(context).findByCql(search,
        new PagingParameters(limit, offset), {
        JsonResponse.success(routingContext.response(),
          new ItemRepresentation(relativeItemsPath()).toJson(it.result, context))
      }, FailureResponseConsumer.serverError(routingContext.response()))
    }
  }

  void deleteAll(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    storage.getItemCollection(context).empty(
      { SuccessResponse.noContent(routingContext.response()) },
      FailureResponseConsumer.serverError(routingContext.response()))
  }

  void create(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    Map itemRequest = new VertxBodyParser().toMap(routingContext)

    def newItem = requestToItem(itemRequest)

    def itemCollection = storage.getItemCollection(context)

    itemCollection.findByCql("barcode=${newItem.barcode}",
      PagingParameters.defaults(), {

      if(it.result.size() == 0) {
        itemCollection.add(newItem, { Success success ->
          RedirectResponse.created(routingContext.response(),
            context.absoluteUrl(
              "${relativeItemsPath()}/${success.result.id}").toString())
        }, FailureResponseConsumer.serverError(routingContext.response()))
      }
      else {
        ClientErrorResponse.badRequest(routingContext.response(),
          "Barcodes must be unique, ${newItem.barcode} is already assigned to another item")
      }
    }, FailureResponseConsumer.serverError(routingContext.response()))
  }

  void update(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    Map itemRequest = new VertxBodyParser().toMap(routingContext)

    def updatedItem = requestToItem(itemRequest)

    def itemCollection = storage.getItemCollection(context)

    itemCollection.findById(routingContext.request().getParam("id"), {
      Success it ->
      if(it.result != null) {
        if(it.result.barcode == updatedItem.barcode) {
          itemCollection.update(updatedItem,
            { SuccessResponse.noContent(routingContext.response()) },
            { Failure failure -> ServerErrorResponse.internalError(
              routingContext.response(), failure.reason) })
        } else {
          itemCollection.findByCql("barcode=${updatedItem.barcode}",
            PagingParameters.defaults(), {

            if(it.result.size() == 1 && it.result.first().id == updatedItem.id) {
              itemCollection.update(updatedItem, {
                SuccessResponse.noContent(routingContext.response()) },
                { Failure failure -> ServerErrorResponse.internalError(
                  routingContext.response(), failure.reason) })
            }
            else {
              ClientErrorResponse.badRequest(routingContext.response(),
                "Barcodes must be unique, ${updatedItem.barcode} is already assigned to another item")
            }
          }, FailureResponseConsumer.serverError(routingContext.response()))
        }
      }
      else {
        ClientErrorResponse.notFound(routingContext.response())
      }
    }, FailureResponseConsumer.serverError(routingContext.response()))
  }

  void deleteById(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    storage.getItemCollection(context).delete(
      routingContext.request().getParam("id"),
      { SuccessResponse.noContent(routingContext.response()) },
      FailureResponseConsumer.serverError(routingContext.response()))
  }

  void getById(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    storage.getItemCollection(context).findById(
      routingContext.request().getParam("id"),
      { Success it ->
        if(it.result != null) {
          JsonResponse.success(routingContext.response(),
            new ItemRepresentation(relativeItemsPath())
              .toJson(it.result, context))
        }
        else {
          ClientErrorResponse.notFound(routingContext.response())
        }
      }, FailureResponseConsumer.serverError(routingContext.response()))
  }

  private static String relativeItemsPath() {
    "/inventory/items"
  }

  private Item requestToItem(Map<String, Object> itemRequest) {
    new Item(itemRequest.id, itemRequest.title,
      itemRequest.barcode, itemRequest.instanceId, itemRequest?.status?.name,
      itemRequest?.materialType?.name, itemRequest?.location?.name)
  }
}


