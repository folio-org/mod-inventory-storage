package knowledgebase.core.api.resource

import io.vertx.core.http.HttpMethod
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import io.vertx.groovy.ext.web.handler.BodyHandler

import knowledgebase.core.api.ResourceMap
import knowledgebase.core.api.response.ClientErrorResponse
import knowledgebase.core.api.response.JsonResponse
import knowledgebase.core.api.response.RedirectResponse
import knowledgebase.core.domain.Instance
import knowledgebase.core.domain.InstanceCollection

import knowledgebase.core.api.representation.InstanceRepresentation

class InstanceResource {
  public static void register(Router router, InstanceCollection instanceCollection) {

    router.route(ResourceMap.instance().toString() + "*").handler(BodyHandler.create())

    def createNewRoute = router.route(HttpMethod.POST, ResourceMap.instance())

    createNewRoute.handler({ routingContext ->

      def body = getMapFromBody(routingContext)

      def instanceToCreate = new Instance(body.title, body.identifiers)

      instanceCollection.add(instanceToCreate, { instance ->
        RedirectResponse.created(routingContext.response(),
          ResourceMap.instanceAbsolute("/${instance.id}", routingContext.request()))
      })
    })

    def getRoute = router.route(HttpMethod.GET, ResourceMap.instance('/:id'))

    getRoute.handler({ routingContext ->
      def response = routingContext.response()
      def instance = instanceCollection.findById(routingContext.request().getParam("id"))

      if (instance == null) {
        ClientErrorResponse.notFound(response)
      } else {
        JsonResponse.success(response, InstanceRepresentation.toMap(instance,
          routingContext.request()))
      }
    })

    def searchroute = router.route(HttpMethod.GET, ResourceMap.instance())

    searchroute.handler({ routingContext ->

      def responders = [:]

      def findResponder = { context, term, finder ->
        finder(searchValue(context), { result ->
          JsonResponse.success(context.response(),
            result.collect { instance -> InstanceRepresentation.toMap(instance, context.request()) })
        })
      }

      responders.partialTitle = findResponder.rcurry(instanceCollection.&findByPartialTitle)
      responders.isbn = findResponder.rcurry(instanceCollection.&findByIdentifier.curry('isbn'))
      responders.all = { context, term ->
        instanceCollection.findAll({ result ->
          JsonResponse.success(context.response(),
            result.collect { instance -> InstanceRepresentation.toMap(instance, context.request()) })
        })
      }

      def searchTerm = hasSearchTerms(routingContext) ? searchTerm(routingContext) : 'all'

      def responder = responders.get(searchTerm, this.&fail)

      responder(routingContext, searchTerm)
    });
  }

  private static void fail(RoutingContext routingContext, String searchTerm) {
    def response = routingContext.response()

    response.statusCode = 500
    response.putHeader "content-type", "text/plain"

    response.end("Unknown search term: $searchTerm")
  }

  private static boolean hasSearchTerms(RoutingContext routingContext) {
    routingContext.request().params().size() > 0
  }

  private static String searchValue(RoutingContext routingContext) {
    routingContext.request().getParam(searchTerm(routingContext))
  }

  private static String searchTerm(RoutingContext routingContext) {
    routingContext.request().params().names().iterator().next()
  }

  private static def getMapFromBody(RoutingContext routingContext) {
    if (routingContext.bodyAsString.trim()) {
      routingContext.getBodyAsJson()
    } else {
      new HashMap<String, Object>()
    }
  }
}