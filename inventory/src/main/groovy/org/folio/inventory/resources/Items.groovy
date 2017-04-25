package org.folio.inventory.resources

import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import io.vertx.groovy.ext.web.handler.BodyHandler
import org.folio.inventory.CollectionResourceClient
import org.folio.inventory.domain.Item
import org.folio.inventory.storage.Storage
import org.folio.inventory.support.http.client.OkapiHttpClient
import org.folio.inventory.support.http.client.Response
import org.folio.metadata.common.WebContext
import org.folio.metadata.common.api.request.PagingParameters
import org.folio.metadata.common.api.request.VertxBodyParser
import org.folio.metadata.common.api.response.*
import org.folio.metadata.common.domain.Failure
import org.folio.metadata.common.domain.Success

import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors

class Items {

  private final Storage storage

  Items(final Storage storage) {
    this.storage = storage
  }

  void register(Router router) {
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

    def search = context.getStringParameter("query", null)

    def pagingParameters = PagingParameters.from(context)

    if(pagingParameters == null) {
      ClientErrorResponse.badRequest(routingContext.response(),
        "limit and offset must be numeric when supplied")

      return
    }

    if(search == null) {
      storage.getItemCollection(context).findAll(
        pagingParameters,
        { Success success ->
          respondWithManyItems(routingContext, context, success.result)
        }, FailureResponseConsumer.serverError(routingContext.response()))
    }
    else {
      storage.getItemCollection(context).findByCql(search,
        pagingParameters, { Success success ->
        respondWithManyItems(routingContext, context, success.result)
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

    def materialTypesClient = createMaterialTypesClient(routingContext, context)

    storage.getItemCollection(context).findById(
      routingContext.request().getParam("id"),
      { Success itemResponse ->
        def item = itemResponse.result

        if(item != null) {
          if(item?.materialType?.id != null) {
            materialTypesClient.get(item.materialType.id,
              { materialTypeResponse ->
              if(materialTypeResponse.statusCode == 200) {
                JsonResponse.success(routingContext.response(),
                  new ItemRepresentation(relativeItemsPath())
                    .toJson(item, materialTypeResponse.json, context))
              } else if (materialTypeResponse.statusCode == 404) {
                JsonResponse.success(routingContext.response(),
                  new ItemRepresentation(relativeItemsPath())
                    .toJson(item, context))
              } else {
                ServerErrorResponse.internalError(routingContext.response(),
                  String.format("Failed to get material type with ID: %s:, %s",
                    item.materialType.id, materialTypeResponse.getBody()));
              }
            })
          }
          else {
            JsonResponse.success(routingContext.response(),
              new ItemRepresentation(relativeItemsPath())
                .toJson(item, context))
          }
        }
        else {
          ClientErrorResponse.notFound(routingContext.response())
        }
      }, FailureResponseConsumer.serverError(routingContext.response()))
  }

  private CollectionResourceClient createMaterialTypesClient(
    RoutingContext routingContext,
    WebContext context) {

    def client = new OkapiHttpClient(routingContext.vertx().createHttpClient(),
      new URL(context.okapiLocation), context.tenantId,
      context.token,
      {
        ServerErrorResponse.internalError(routingContext.response(),
          "Failed to retrieve material types: ${it}")
      })

    new CollectionResourceClient(client,
      new URL(context.okapiLocation + "/material-types"))
  }

  private static String relativeItemsPath() {
    "/inventory/items"
  }

  private Item requestToItem(Map<String, Object> itemRequest) {
    new Item(itemRequest.id, itemRequest.title,
      itemRequest.barcode, itemRequest.instanceId, itemRequest?.status?.name,
      itemRequest?.materialType, itemRequest?.location?.name)
  }

  private respondWithManyItems(
    RoutingContext routingContext,
    WebContext context,
    List<Item> items) {

    def materialTypesClient = createMaterialTypesClient(routingContext, context)

    def allFutures = new ArrayList<CompletableFuture<Response>>()

    def materialTypeIds = items.stream()
      .map({ it?.materialType?.id })
      .filter({ it != null })
      .distinct()
      .collect(Collectors.toList())

    materialTypeIds.each { id ->
      def newFuture = new CompletableFuture<>()

      allFutures.add(newFuture)

      materialTypesClient.get(id, { response -> newFuture.complete(response) })
    }

    CompletableFuture<Void> allDoneFuture = CompletableFuture.allOf(*allFutures)

    allDoneFuture.thenAccept({ v ->
      def materialTypeResponses = allFutures.stream()
        .map({ future -> future.join() })
        .collect(Collectors.toList())

      def foundMaterialTypes = materialTypeResponses.stream()
        .filter({ it.getStatusCode() == 200 })
        .map({ it.getJson() })
        .collect(Collectors.toMap({ it.getString("id") }, { it }))

      JsonResponse.success(routingContext.response(),
        new ItemRepresentation(relativeItemsPath()).toJson(items,
          foundMaterialTypes, context))
    });
  }
}


