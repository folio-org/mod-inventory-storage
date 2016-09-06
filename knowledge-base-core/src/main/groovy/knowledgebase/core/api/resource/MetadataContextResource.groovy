package knowledgebase.core.api.resource

import io.vertx.core.http.HttpMethod
import io.vertx.groovy.ext.web.Router
import knowledgebase.core.api.ResourceMap
import knowledgebase.core.api.response.JsonResponse

class MetadataContextResource {
  public static void register(Router router) {

    def instanceMetadataRoute = router.route(HttpMethod.GET, ResourceMap.instanceMetadata())

    def representation = [:]

    representation."@context" = [
      "dcterms": "http://purl.org/dc/terms/",
      "title"  : "dcterms:title"
    ]

    instanceMetadataRoute.handler({ routingContext ->
      JsonResponse.success(routingContext.response(),
        representation)
    })

  }
}
