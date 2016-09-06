package support

import catalogue.core.api.response.JsonResponse
import catalogue.core.api.response.RedirectResponse
import catalogue.core.support.WebRequestDiagnostics
import io.vertx.core.Future
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.groovy.core.Vertx
import io.vertx.groovy.core.http.HttpServer
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import io.vertx.groovy.ext.web.handler.BodyHandler
import io.vertx.lang.groovy.GroovyVerticle

import java.util.concurrent.CompletableFuture

class FakeKnowledgeBase extends GroovyVerticle {
  public static final String address = 'http://localhost:9491/knowledge-base'

  private HttpServer server;
  private Map<String, JsonObject> instances = [:];

  public static void deploy(Vertx vertx, CompletableFuture deployed) {
    vertx.deployVerticle("groovy:support.FakeKnowledgeBase", { res ->
      if (res.succeeded()) {
        deployed.complete(null);
      } else {
        deployed.completeExceptionally(res.cause());
      }
    });
  }

  @Override
  public void start(Future deployed) {
    server = vertx.createHttpServer()

    def router = Router.router(vertx)

    router.route().handler(WebRequestDiagnostics.&outputDiagnostics)

    router.route('/knowledge-base/instance/*').handler(BodyHandler.create())

    def homeRoute = router.route(HttpMethod.GET, '/knowledge-base')

    homeRoute.handler({ routingContext ->
      def links = [:]

      links << ['instances': address + "/instance"]

      JsonResponse.success(routingContext.response(),
        new JsonObject()
          .put("message", "Welcome to the Folio Knowledge Base")
          .put("links", links))
    });

    def getInstanceRoute = router.route(HttpMethod.GET, '/knowledge-base/instance/:id')

    getInstanceRoute.handler({ routingContext ->
      JsonResponse.success(routingContext.response(),
        instances[routingContext.request().getParam("id")])
    });

    def createInstanceRoute = router.route(HttpMethod.POST, '/knowledge-base/instance')

    createInstanceRoute.handler({ routingContext ->
      def body = getMapFromBody(routingContext)

      def id = UUID.randomUUID().toString()

      instances.put(id, new JsonObject(body))

      RedirectResponse.created(routingContext.response(),
        address + "/instance/${id}")
    })

    server.requestHandler(router.&accept)
      .listen(9491,
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
}
