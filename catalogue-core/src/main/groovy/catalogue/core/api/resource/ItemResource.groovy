package catalogue.core.api.resource

import catalogue.core.api.ResourceMap
import catalogue.core.api.representation.ItemRepresentation
import catalogue.core.api.response.ClientErrorResponse
import catalogue.core.api.response.JsonResponse
import catalogue.core.api.response.RedirectResponse
import catalogue.core.domain.Item
import catalogue.core.domain.ItemCollection

import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import io.vertx.groovy.ext.web.handler.BodyHandler

class ItemResource {

    public static void register(Router router, ItemCollection instanceCollection) {

        router.route(ResourceMap.item().toString() + "*").handler(BodyHandler.create())

        router.route(HttpMethod.GET, ResourceMap.item()).handler(findAll(instanceCollection));

        router.route(HttpMethod.POST, ResourceMap.item()).handler(create(instanceCollection));

        router.route(HttpMethod.GET, ResourceMap.item('/:id')).handler(find(instanceCollection));
    }

    static Closure create(ItemCollection itemCollection) {
        { routingContext ->
            def client = routingContext.vertx().createHttpClient()

            def body = getMapFromBody(routingContext)

            client.requestAbs(HttpMethod.GET, body.instance, { response ->
                response.bodyHandler({ buffer ->
                    System.out.println("Response (" + buffer.length() + "): ");
                    System.out.println(buffer.getString(0, buffer.length()));

                    def instance = new JsonObject(buffer.getString(0, buffer.length()))

                    def itemToCreate = new Item(instance.getString("title"), body.instance)

                    itemCollection.add(itemToCreate, { item ->
                        RedirectResponse.created(routingContext.response(),
                                ResourceMap.itemAbsolute("/${item.id}", routingContext.request()))
                    })
                })
            })
            .end()


        }
    }

    private static Closure findAll(ItemCollection itemCollection) {
        { routingContext ->
            itemCollection.findAll({ result ->
                JsonResponse.success(routingContext.response(),
                        result.collect { item -> ItemRepresentation.toMap(item, routingContext.request()) })
            })
        }
    }

    private static Closure find(ItemCollection itemCollection) {
        { routingContext ->
            itemCollection.findById(routingContext.request().getParam("id"), { result ->
                if(result == null) {
                    ClientErrorResponse.notFound(routingContext.response())
                }
                else {
                    JsonResponse.success(routingContext.response(),
                            ItemRepresentation.toMap(result, routingContext.request()))
                }
            })
        }
    }

    private static def getMapFromBody(RoutingContext routingContext) {
        if (routingContext.bodyAsString.trim()) {
            routingContext.getBodyAsJson()
        }
        else {
            new HashMap<String, Object>()
        }
    }
}
