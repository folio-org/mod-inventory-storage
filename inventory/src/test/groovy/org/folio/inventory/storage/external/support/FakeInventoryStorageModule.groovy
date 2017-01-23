package org.folio.inventory.storage.external.support

import io.vertx.core.Future
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.groovy.core.http.HttpServer
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import io.vertx.groovy.ext.web.handler.BodyHandler
import io.vertx.lang.groovy.GroovyVerticle
import org.folio.metadata.common.WebContext
import org.folio.metadata.common.WebRequestDiagnostics
import org.folio.metadata.common.api.response.ClientErrorResponse
import org.folio.metadata.common.api.response.JsonResponse

import java.util.regex.Pattern

class FakeInventoryStorageModule extends GroovyVerticle {
  private static final int PORT_TO_USE = 9492
  private static final String address = "http://localhost:${PORT_TO_USE}"

  private final Map<String, Map<String, JsonObject>> storedItemsByTenant
  private final Map<String, Map<String, JsonObject>> storedInstancesByTenant

  private HttpServer server;

  static def String getAddress() {
    address
  }

  FakeInventoryStorageModule() {
    storedItemsByTenant = [:]
    storedInstancesByTenant = [:]
  }

  @Override
  public void start(Future deployed) {
    def expectedTenants = vertx.getOrCreateContext().config()
      .get("expectedTenants", [])

    server = vertx.createHttpServer()

    def router = Router.router(vertx)

    server.requestHandler(router.&accept)
      .listen(PORT_TO_USE,
      { result ->
        if (result.succeeded()) {
          deployed.complete();
        } else {
          deployed.fail(result.cause());
        }
      })

    router.route('/item-storage/items/*').handler(BodyHandler.create())
    router.route('/instance-storage/instances/*').handler(BodyHandler.create())

    router.route().handler(WebRequestDiagnostics.&outputDiagnostics)
    router.route().handler(this.&checkTenantHeader.rcurry(expectedTenants))
    router.route().handler(this.&checkAcceptHeader)
    router.route(HttpMethod.POST, '/instance-storage/*').handler(this.&checkContentTypeHeader)
    router.route(HttpMethod.POST, '/item-storage/*').handler(this.&checkContentTypeHeader)

    router.route(HttpMethod.GET, '/item-storage/items/:id')
      .handler(this.&getItem);

    router.route(HttpMethod.GET, '/item-storage/items')
      .handler(this.&getItems);

    router.route(HttpMethod.DELETE, '/item-storage/items')
      .handler(this.&deleteItems)

    router.route(HttpMethod.POST, '/item-storage/items')
      .handler(this.&createItem)

    router.route(HttpMethod.GET, '/instance-storage/instances/:id')
      .handler(this.&getInstance);

    router.route(HttpMethod.GET, '/instance-storage/instances')
      .handler(this.&getInstances);

    router.route(HttpMethod.DELETE, '/instance-storage/instances')
      .handler(this.&deleteInstances)

    router.route(HttpMethod.POST, '/instance-storage/instances')
      .handler(this.&createInstance)
  }

  @Override
  public void stop(Future stopped) {
    println "Stopping fake knowledge base"
    server.close({ result ->
      if (result.succeeded()) {
        println "Stopped listening on ${server.actualPort()}"
        stopped.complete();
      } else {
        stopped.fail(result.cause());
      }
    });
  }

  private def createItem(RoutingContext routingContext) {
    def body = getMapFromBody(routingContext)

    def newItem = new JsonObject(body)

    def id = newItem.getString("id")

    def itemsForTenant = getItemsForTenant(getTenantId(routingContext))

    itemsForTenant.put(id, newItem)

    JsonResponse.created(routingContext.response(),
      itemsForTenant[id])
  }

  private def deleteItems(RoutingContext routingContext) {
    def itemsForTenant = getItemsForTenant(getTenantId(routingContext))

    itemsForTenant.clear()

    JsonResponse.success(routingContext.response(),
      itemsForTenant.values())
  }

  private def getItems(RoutingContext routingContext) {
    def itemsForTenant = getItemsForTenant(getTenantId(routingContext))

    def context = new WebContext(routingContext)

    def limit = context.getIntegerParameter("limit", 10)
    def offset = context.getIntegerParameter("offset", 0)
    def query = context.getStringParameter("query", null)

    def filteredItems = itemsForTenant.values().stream()
      .skip(offset)
      .limit(limit)
      .collect()

    def result = new JsonObject()
    result.put("items", new JsonArray(filteredItems))
    result.put("total_records", itemsForTenant.size())

    JsonResponse.success(routingContext.response(), result)
  }

  private def getItem(RoutingContext routingContext) {
    def itemsForTenant = getItemsForTenant(getTenantId(routingContext))

    def item = itemsForTenant.get(routingContext.request().getParam("id"), null)

    if(item != null) {
      JsonResponse.success(routingContext.response(), item)
    }
    else {
      ClientErrorResponse.notFound(routingContext.response())
    }
  }

  private def createInstance(RoutingContext routingContext) {
    def body = getMapFromBody(routingContext)

    def newItem = new JsonObject(body)

    def id = newItem.getString("id")

    def storedInstances = getInstancesForTenant(getTenantId(routingContext))

    storedInstances.put(id, newItem)

    JsonResponse.created(routingContext.response(),
      storedInstances[id])
  }

  private def deleteInstances(RoutingContext routingContext) {
    def storedInstances = getInstancesForTenant(getTenantId(routingContext))

    storedInstances.clear()

    JsonResponse.success(routingContext.response(),
      storedInstances.values())
  }

  private def getInstances(RoutingContext routingContext) {

    def instancesForTenant = getInstancesForTenant(
      getTenantId(routingContext))

    def context = new WebContext(routingContext)

    def limit = context.getIntegerParameter("limit", 10)
    def offset = context.getIntegerParameter("offset", 0)

    def query = context.getStringParameter("query", null)


    def searchTerm = query == null ? null :
      query.replace("title=", "").replaceAll("\"", "").replaceAll("\\*", "")

    def filteredInstances = instancesForTenant.values().stream()
      .filter(filterByTitle(searchTerm))
      .collect()

    def pagedInstances = filteredInstances.stream()
      .skip(offset)
      .limit(limit)
      .collect()

    def result = new JsonObject()
    result.put("instances", new JsonArray(pagedInstances))
    result.put("total_records", filteredInstances.size())

    JsonResponse.success(routingContext.response(), result)
  }

  private Closure filterByTitle(searchTerm) {
    return {
      if (searchTerm == null) {
        true
      } else {
        it.getString("title").contains(searchTerm)
      }
    }
  }

  private def getInstance(RoutingContext routingContext) {
    def foundInstance = getInstancesForTenant(getTenantId(routingContext)).get(
      routingContext.request().getParam("id"), null)

    if(foundInstance != null) {
      JsonResponse.success(routingContext.response(), foundInstance)
    }
    else {
      ClientErrorResponse.notFound(routingContext.response())
    }
  }

  private static def getMapFromBody(RoutingContext routingContext) {
    if (hasBody(routingContext)) {

      routingContext.getBodyAsJson()
    } else {
      new HashMap<String, Object>()
    }
  }

  private static boolean hasBody(RoutingContext routingContext) {
    routingContext.bodyAsString != null &&
      routingContext.bodyAsString.trim()
  }

  private static void checkTenantHeader(RoutingContext routingContext,
                                        Collection<String> expectedTenants) {

    def tenantId = new WebContext(routingContext).tenantId

    switch (tenantId) {
      case expectedTenants:
        routingContext.next()
        break

      case null:
        ClientErrorResponse.forbidden(routingContext.response(),
          "Missing Tenant")
        break

      default:
        ClientErrorResponse.forbidden(routingContext.response(),
          "Incorrect Tenant, expected: ${expectedTenants}, received: ${tenantId}")
        break
    }
  }

  private String getTenantId(RoutingContext routingContext) {
    new WebContext(routingContext).tenantId
  }

  private Map<String, JsonObject> getItemsForTenant(String tenantId) {
    getMapForTenant(tenantId, storedItemsByTenant)
  }

  private Map<String, JsonObject> getInstancesForTenant(String tenantId) {
    getMapForTenant(tenantId, storedInstancesByTenant)
  }

  private Map<String, JsonObject> getMapForTenant(
    String tenantId,
    Map<String, Map<String, JsonObject>> maps) {

    def mapForTenant = maps.get(tenantId, null)

    if (mapForTenant == null) {
      mapForTenant = [:]
      maps.put(tenantId, mapForTenant)
    }

    mapForTenant
  }

  private static void checkAcceptHeader(RoutingContext routingContext) {

    def accepts = new WebContext(routingContext).getHeader("Accept")

    switch (accepts) {
      case null:
      case "":
        ClientErrorResponse.badRequest(routingContext.response(),
          "Missing Accept Header")
        break

      case "application/json":
      default:
        routingContext.next()
        break
    }
  }

  private static void checkContentTypeHeader(RoutingContext routingContext) {

    def accepts = new WebContext(routingContext).getHeader("Content-Type")

    switch (accepts) {
      case "application/json":
        routingContext.next()
        break

      case null:
      case "":
      default:
        ClientErrorResponse.badRequest(routingContext.response(),
          "Missing Content Type Header")
        break
    }
  }
}
