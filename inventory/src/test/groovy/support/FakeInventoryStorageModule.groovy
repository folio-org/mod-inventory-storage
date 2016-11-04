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
import org.folio.metadata.common.api.response.RedirectResponse

class FakeInventoryStorageModule extends GroovyVerticle {
  private static final int PORT_TO_USE = 9492

  public static final String address = "http://localhost:${PORT_TO_USE}/inventory-storage"
  private final Map<String, JsonObject> storedItems = [:];

  private HttpServer server;

  @Override
  public void start(Future deployed) {
    server = vertx.createHttpServer()

    def router = Router.router(vertx)

    router.route('/inventory-storage/item/*').handler(BodyHandler.create())

    router.route().handler(WebRequestDiagnostics.&outputDiagnostics)
    router.route().handler(this.&checkTenantHeader)

    def getItemRoute = router.route(HttpMethod.GET, '/inventory-storage/item/:id')

    getItemRoute.handler({ routingContext ->
      JsonResponse.success(routingContext.response(),
        storedItems[routingContext.request().getParam("id")])
    });

    def getAllItemsRoute = router.route(HttpMethod.GET, '/inventory-storage/item')

    getAllItemsRoute.handler({ routingContext ->
      JsonResponse.success(routingContext.response(),
        storedItems.values())
    });

    def deleteAllRoute = router.route(HttpMethod.DELETE, '/inventory-storage/item')

    deleteAllRoute.handler({ routingContext ->
      storedItems.clear()

      JsonResponse.success(routingContext.response(),
        storedItems.values())
    });

    def createItemRoute = router.route(HttpMethod.POST, '/inventory-storage/item')

    createItemRoute.handler({ routingContext ->
      def body = getMapFromBody(routingContext)

      println("Received body: ${body}")

      def id = UUID.randomUUID().toString()

      def newItem = new JsonObject(body)
        .put("id", id)

      storedItems.put(id, newItem)

      JsonResponse.success(routingContext.response(),
        storedItems[id])
    })

    server.requestHandler(router.&accept)
      .listen(9492,
      { result ->
        if (result.succeeded()) {
          deployed.complete();
        } else {
          deployed.fail(result.cause());
        }
      })
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

  private static def getMapFromBody(RoutingContext routingContext) {
    if (routingContext.bodyAsString.trim()) {
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
