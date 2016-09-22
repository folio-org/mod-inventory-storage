package org.folio.catalogue.core.api.resource

import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import io.vertx.groovy.ext.web.handler.BodyHandler
import org.apache.commons.lang.StringUtils
import org.folio.catalogue.core.api.ResourceMap
import org.folio.catalogue.core.api.representation.ItemRepresentation
import org.folio.metadata.common.api.response.ClientErrorResponse
import org.folio.metadata.common.api.response.JsonResponse
import org.folio.metadata.common.api.response.RedirectResponse
import org.folio.catalogue.core.domain.Item
import org.folio.catalogue.core.domain.ItemCollection

class ItemResource {
  static private final String TENANT_HEADER_NAME = "X-Okapi-Tenant"

  public static void register(Router router, ItemCollection instanceCollection) {

    router.get(ResourceMap.item()).handler(find(instanceCollection));
    router.get(ResourceMap.item('/:id')).handler(findById(instanceCollection));

    router.post(ResourceMap.item().toString() + "*").handler(BodyHandler.create())
    router.post(ResourceMap.item()).handler(create(instanceCollection));
  }

  static Closure create(ItemCollection itemCollection) {
    { routingContext ->

      String tenant = routingContext.request().getHeader(TENANT_HEADER_NAME) ?: "";

      def client = routingContext.vertx().createHttpClient()

      def body = getMapFromBody(routingContext)

      client.requestAbs(HttpMethod.GET, body.instance, { response ->
        response.bodyHandler({ buffer ->
          def status = "${response.statusCode()}"
          def instanceBody = "${buffer.getString(0, buffer.length())}"

          printDiagnostics(body.instance, status, instanceBody)

          if (Integer.parseInt(status) == HttpResponseStatus.OK.code()) {
            def instance = new JsonObject(instanceBody)

            def itemToCreate = new Item(instance.getString("title"), body.instance, body.barcode)

            itemCollection.add(itemToCreate, { item ->
              RedirectResponse.created(routingContext.response(),
                      ResourceMap.itemAbsolute("/${item.id}", routingContext.request()))
            })
          } else {
            ClientErrorResponse.badRequest(routingContext.response(),
                    "Request to reach instance at ${body.instance} failed: ${status} : ${instanceBody}")
          }
        })
      })
      .exceptionHandler({ throwable ->
        ClientErrorResponse.badRequest(routingContext.response(),
                "Failed to reach instance location - ${body.instance}")
      })
      .putHeader(TENANT_HEADER_NAME, tenant)
      .end()
    }
  }

  private static void printDiagnostics(location, status, body) {
    println "Response Received from instance resource - ${location}"
    println StringUtils.repeat("-", 25)
    println "Status Code: ${status}"
    println "Body: ${body}"
    println StringUtils.repeat("-", 25)
  }

  private static Closure find(ItemCollection itemCollection) {
    { routingContext ->

      def firstSearchTerm = firstQueryParameter(routingContext)

      switch (firstSearchTerm) {
        case "partialTitle":
          itemCollection.findByTitle(queryParameterValue(routingContext, firstSearchTerm), { result ->
            JsonResponse.success(routingContext.response(),
              result.collect { item -> ItemRepresentation.toMap(item, routingContext.request()) })
          })
          break;

        default:
          itemCollection.findAll({ result ->
            JsonResponse.success(routingContext.response(),
              result.collect { item -> ItemRepresentation.toMap(item, routingContext.request()) })
          })
      }
    }
  }

  private static String firstQueryParameter(routingContext) {
    if (routingContext.request().params().size() > 0) {
      routingContext.request().params().names().iterator().next()
    } else {
      null
    }
  }

  private static String queryParameterValue(RoutingContext routingContext, String parameter) {
    routingContext.request().getParam(parameter)
  }

  private static Closure findById(ItemCollection itemCollection) {
    { routingContext ->
      itemCollection.findById(routingContext.request().getParam("id"), { result ->
        if (result == null) {
          ClientErrorResponse.notFound(routingContext.response())
        } else {
          JsonResponse.success(routingContext.response(),
            ItemRepresentation.toMap(result, routingContext.request()))
        }
      })
    }
  }

  private static def getMapFromBody(RoutingContext routingContext) {
    if (routingContext.bodyAsString.trim()) {
      routingContext.getBodyAsJson()
    } else {
      new HashMap<String, Object>()
    }
  }
}
