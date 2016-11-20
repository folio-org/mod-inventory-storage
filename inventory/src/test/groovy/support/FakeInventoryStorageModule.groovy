package support

import io.vertx.core.Future
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.groovy.core.http.HttpServer
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import io.vertx.groovy.ext.web.handler.BodyHandler
import io.vertx.lang.groovy.GroovyVerticle
import org.folio.metadata.common.WebRequestDiagnostics
import org.folio.metadata.common.api.response.ClientErrorResponse
import org.folio.metadata.common.api.response.JsonResponse

class FakeInventoryStorageModule extends GroovyVerticle {
  private static final int PORT_TO_USE = 9492

  public static final String address = "http://localhost:${PORT_TO_USE}/inventory-storage"
  private final Map<String, JsonObject> storedItems = [:];
  private final Map<String, JsonObject> storedInstances = [:];

  private HttpServer server;

  @Override
  public void start(Future deployed) {
    server = vertx.createHttpServer()

    def router = Router.router(vertx)

    server.requestHandler(router.&accept)
      .listen(9492,
      { result ->
        if (result.succeeded()) {
          deployed.complete();
        } else {
          deployed.fail(result.cause());
        }
      })

    router.route('/inventory-storage/items/*').handler(BodyHandler.create())
    router.route('/inventory-storage/instances/*').handler(BodyHandler.create())

    router.route().handler(WebRequestDiagnostics.&outputDiagnostics)
    router.route().handler(this.&checkTenantHeader)

    router.route(HttpMethod.GET, '/inventory-storage/items/:id')
      .handler(this.&getItem);

    router.route(HttpMethod.GET, '/inventory-storage/items')
      .handler(this.&getItems);

    router.route(HttpMethod.DELETE, '/inventory-storage/items')
      .handler(this.&deleteItems)

    router.route(HttpMethod.POST, '/inventory-storage/items')
      .handler(this.&createItem)

    router.route(HttpMethod.GET, '/inventory-storage/instances/:id')
      .handler(this.&getInstance);

    router.route(HttpMethod.GET, '/inventory-storage/instances')
      .handler(this.&getInstances);

    router.route(HttpMethod.DELETE, '/inventory-storage/instances')
      .handler(this.&deleteInstances)

    router.route(HttpMethod.POST, '/inventory-storage/instances')
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

    def id = UUID.randomUUID().toString()

    def newItem = new JsonObject(body)
      .put("id", id)

    storedItems.put(id, newItem)

    JsonResponse.success(routingContext.response(),
      storedItems[id])
  }

  private def deleteItems(RoutingContext routingContext) {
      storedItems.clear()

      JsonResponse.success(routingContext.response(),
        storedItems.values())
  }

  private def getItems(RoutingContext routingContext) {
    JsonResponse.success(routingContext.response(),
      storedItems.values())
  }

  private def getItem(RoutingContext routingContext) {
    JsonResponse.success(routingContext.response(),
      storedItems[routingContext.request().getParam("id")])
  }

  private def createInstance(RoutingContext routingContext) {
    def body = getMapFromBody(routingContext)

    def id = UUID.randomUUID().toString()

    def newItem = new JsonObject(body)
      .put("id", id)

    storedInstances.put(id, newItem)

    JsonResponse.success(routingContext.response(),
      storedInstances[id])
  }

  private def deleteInstances(RoutingContext routingContext) {
    storedInstances.clear()

    JsonResponse.success(routingContext.response(),
      storedItems.values())
  }

  private def getInstances(RoutingContext routingContext) {
    JsonResponse.success(routingContext.response(),
      storedInstances.values())
  }

  private def getInstance(RoutingContext routingContext) {
    JsonResponse.success(routingContext.response(),
      storedInstances[routingContext.request().getParam("id")])
  }

  private static def getMapFromBody(RoutingContext routingContext) {
    if (routingContext.bodyAsString != null && routingContext.bodyAsString.trim()) {
      routingContext.getBodyAsJson()
    } else {
      new HashMap<String, Object>()
    }
  }

  private static void checkTenantHeader(RoutingContext routingContext) {
    def tenant = routingContext.request().getHeader("X-Okapi-Tenant");

    if(tenant != null) {
      routingContext.next()
    }
    else {
      ClientErrorResponse.forbidden(routingContext.response(), "Missing Tenant")
    }
  }
}
