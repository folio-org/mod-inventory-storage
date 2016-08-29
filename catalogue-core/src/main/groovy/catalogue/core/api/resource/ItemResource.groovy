package catalogue.core.api.resource

import catalogue.core.api.ResourceMap
import catalogue.core.api.representation.ItemRepresentation
import catalogue.core.api.response.ClientErrorResponse
import catalogue.core.api.response.JsonResponse
import catalogue.core.domain.ItemCollection
import io.vertx.core.http.HttpMethod
import io.vertx.groovy.ext.web.Router

class ItemResource {

    public static void register(Router router, ItemCollection instanceCollection) {

        router.route(HttpMethod.GET, ResourceMap.item()).handler(findAll(instanceCollection));

        router.route(HttpMethod.GET, ResourceMap.item('/:id')).handler(find(instanceCollection));
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
}
