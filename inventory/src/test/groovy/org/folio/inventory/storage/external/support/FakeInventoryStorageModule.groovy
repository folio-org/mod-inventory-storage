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
import org.folio.metadata.common.api.response.SuccessResponse

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

    router.route().handler(WebRequestDiagnostics.&outputDiagnostics)
    router.route().handler(this.&checkTenantHeader.rcurry(expectedTenants))
    router.route().handler(this.&checkTokenHeader)

    router.route('/item-storage/items/*').handler(BodyHandler.create())
    router.route(HttpMethod.POST, '/item-storage/items/*').handler(this.&acceptHeaderIsJson)
    router.route(HttpMethod.GET, '/item-storage/items/*').handler(this.&acceptHeaderIsJson)
    router.route(HttpMethod.PUT, '/item-storage/items/*').handler(this.&acceptHeaderIsText)

    router.route('/instance-storage/instances/*').handler(BodyHandler.create())
    router.route(HttpMethod.POST, '/instance-storage/instances/*').handler(this.&acceptHeaderIsJson)
    router.route(HttpMethod.GET, '/instance-storage/instances/*').handler(this.&acceptHeaderIsJson)
    router.route(HttpMethod.PUT, '/instance-storage/instances/*').handler(this.&acceptHeaderIsText)

    router.route(HttpMethod.POST, '/instance-storage/*').handler(this.&checkContentTypeHeader)
    router.route(HttpMethod.PUT, '/instance-storage/*').handler(this.&checkContentTypeHeader)
    router.route(HttpMethod.POST, '/item-storage/*').handler(this.&checkContentTypeHeader)
    router.route(HttpMethod.PUT, '/item-storage/*').handler(this.&checkContentTypeHeader)

    router.route(HttpMethod.GET, '/item-storage/items/:id')
      .handler(this.&getItem);

    router.route(HttpMethod.DELETE, '/item-storage/items/:id')
      .handler(this.&deleteItem);

    router.route(HttpMethod.PUT, '/item-storage/items/:id')
      .handler(this.&updateItem)

    router.route(HttpMethod.GET, '/item-storage/items')
      .handler(this.&getItems);

    router.route(HttpMethod.DELETE, '/item-storage/items')
      .handler(this.&deleteItems)

    router.route(HttpMethod.POST, '/item-storage/items')
      .handler(this.&createItem)

    router.route(HttpMethod.GET, '/instance-storage/instances/:id')
      .handler(this.&getInstance);

    router.route(HttpMethod.DELETE, '/instance-storage/instances/:id')
      .handler(this.&deleteInstance);

    router.route(HttpMethod.PUT, '/instance-storage/instances/:id')
      .handler(this.&updateInstance);

    router.route(HttpMethod.GET, '/instance-storage/instances')
      .handler(this.&getInstances);

    router.route(HttpMethod.DELETE, '/instance-storage/instances')
      .handler(this.&deleteInstances)

    router.route(HttpMethod.POST, '/instance-storage/instances')
      .handler(this.&createInstance)
  }

  @Override
  public void stop(Future stopped) {
    println "Stopping fake storage module"
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

  private def updateItem(RoutingContext routingContext) {
    def body = getMapFromBody(routingContext)

    def updatedItem = new JsonObject(body)

    def id = routingContext.request().getParam("id")

    def itemsForTenant = getItemsForTenant(getTenantId(routingContext))

    itemsForTenant.replace(id, updatedItem)

    SuccessResponse.noContent(routingContext.response())
  }

  private def deleteItems(RoutingContext routingContext) {
    def itemsForTenant = getItemsForTenant(getTenantId(routingContext))

    itemsForTenant.clear()

    SuccessResponse.noContent(routingContext.response())
  }

  private def getItems(RoutingContext routingContext) {
    def itemsForTenant = getItemsForTenant(getTenantId(routingContext))

    def context = new WebContext(routingContext)

    def limit = context.getIntegerParameter("limit", 10)
    def offset = context.getIntegerParameter("offset", 0)
    def query = context.getStringParameter("query", null)

    def searchField = query == null ? null : query.split("=").first()

    def searchTerm = query == null ? null :
      query.replace("${searchField}=", "").replaceAll("\"", "").replaceAll("\\*", "")

    def filteredItems = itemsForTenant.values().stream()
      .filter(filterByField(searchField, searchTerm))
      .collect()

    def pagedItems = filteredItems.stream()
      .skip(offset)
      .limit(limit)
      .collect()

    def result = new JsonObject()
    result.put("items", new JsonArray(pagedItems))
    result.put("totalRecords", filteredItems.size())

    JsonResponse.success(routingContext.response(), result)
  }

  private def getItem(RoutingContext routingContext) {
    def itemsForTenant = getItemsForTenant(getTenantId(routingContext))

    def id = routingContext.request().getParam("id")

    if(itemsForTenant.containsKey(id)) {
      JsonResponse.success(routingContext.response(),
        itemsForTenant.get(id))
    }
    else {
      ClientErrorResponse.notFound(routingContext.response())
    }
  }

  private def deleteItem(RoutingContext routingContext) {
    def itemsForTenant = getItemsForTenant(getTenantId(routingContext))

    def id = routingContext.request().getParam("id")

    if(itemsForTenant.containsKey(id)) {
      itemsForTenant.remove(id)
      SuccessResponse.noContent(routingContext.response())
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

    SuccessResponse.noContent(routingContext.response())
  }

  private def deleteInstance(RoutingContext routingContext) {
    def instancesForTenant = getInstancesForTenant(getTenantId(routingContext))

    def id = routingContext.request().getParam("id")

    if(instancesForTenant.containsKey(id)) {
      instancesForTenant.remove(id)
      SuccessResponse.noContent(routingContext.response())
    }
    else {
      ClientErrorResponse.notFound(routingContext.response())
    }
  }

  private def updateInstance(RoutingContext routingContext) {
    def body = getMapFromBody(routingContext)

    def updatedInstance = new JsonObject(body)

    def id = routingContext.request().getParam("id")

    def instanceForTenant = getInstancesForTenant(getTenantId(routingContext))

    instanceForTenant.replace(id, updatedInstance)

    SuccessResponse.noContent(routingContext.response())
  }

  private def getInstances(RoutingContext routingContext) {

    def instancesForTenant = getInstancesForTenant(
      getTenantId(routingContext))

    def context = new WebContext(routingContext)

    def limit = context.getIntegerParameter("limit", 10)
    def offset = context.getIntegerParameter("offset", 0)
    def query = context.getStringParameter("query", null)

    def searchField = query == null ? null : query.split("=").first()

    def searchTerm = query == null ? null :
      query.replace("${searchField}=", "").replaceAll("\"", "").replaceAll("\\*", "")

    def filteredInstances = instancesForTenant.values().stream()
      .filter(filterByField(searchField, searchTerm))
      .collect()

    def pagedInstances = filteredInstances.stream()
      .skip(offset)
      .limit(limit)
      .collect()

    println("Total instances: ${instancesForTenant.size()}")
    println("Filtered instances: ${filteredInstances.size()}")
    println("Paged instances: ${pagedInstances.size()}")

    def result = new JsonObject()
    result.put("instances", new JsonArray(pagedInstances))
    result.put("totalRecords", filteredInstances.size())

    JsonResponse.success(routingContext.response(), result)
  }

  private Closure filterByField(field, term) {
    return {
      if (term == null || field == null) {
        true
      } else {
        it.getString("${field}").contains(term)
      }
    }
  }

  private def getInstance(RoutingContext routingContext) {

    def instancesForTenant = getInstancesForTenant(getTenantId(routingContext))

    def id = routingContext.request().getParam("id")

    if(instancesForTenant.containsKey(id)) {
      JsonResponse.success(routingContext.response(),
        instancesForTenant.get(id))
    }
    else {
      ClientErrorResponse.notFound(routingContext.response())
    }
  }

  private static def getMapFromBody(RoutingContext routingContext) {
    if (hasBody(routingContext)) {

      println("Body received by fake: ${routingContext.getBodyAsString()}")

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

  private static void acceptHeaderIsJson(RoutingContext routingContext) {
    checkAcceptHeader(routingContext, "application/json")
  }

  private static void acceptHeaderIsText(RoutingContext routingContext) {
    checkAcceptHeader(routingContext, "text/plain")
  }

  private static void checkAcceptHeader(RoutingContext routingContext,
                                        String acceptableType) {

    def accepts = new WebContext(routingContext).getHeader("Accept")

    switch (accepts) {
      case {it.contains(acceptableType)}:
        routingContext.next()
        break

      case null:
      case "":
        ClientErrorResponse.badRequest(routingContext.response(),
          "Missing Accept Header")
        break
      default:
        ClientErrorResponse.badRequest(routingContext.response(),
          "Accept Header should be ${acceptableType}")
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

  private static void checkTokenHeader(RoutingContext routingContext) {

    def tokenId = new WebContext(routingContext).token

    if(tokenId != null && tokenId.trim().length() > 0) {
      routingContext.next()
    }
    else {
      ClientErrorResponse.forbidden(routingContext.response(),
        "Missing Token")
    }
  }
}
