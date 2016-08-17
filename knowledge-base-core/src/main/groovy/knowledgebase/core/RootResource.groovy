package knowledgebase.core

import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import knowledgebase.core.util.JsonResponse
import knowledgebase.core.util.ResourceMap

class RootResource {
    static def register(Router router) {
        router.route(HttpMethod.GET, ResourceMap.rootResource()).handler(RootResource.&handleRoot)
    }

    public static void handleRoot(RoutingContext routingContext) {
        JsonResponse.success(routingContext.response(),
                new JsonObject()
                        .put("Message", "Welcome to the Folio Knowledge Base"))
    }
}
