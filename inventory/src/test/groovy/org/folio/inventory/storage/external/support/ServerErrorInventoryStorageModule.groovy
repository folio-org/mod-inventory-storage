package org.folio.inventory.storage.external.support

import io.vertx.core.Future
import io.vertx.groovy.core.http.HttpServer
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import io.vertx.lang.groovy.GroovyVerticle
import org.folio.metadata.common.WebRequestDiagnostics
import org.folio.metadata.common.api.response.ServerErrorResponse

class ServerErrorInventoryStorageModule extends GroovyVerticle {
  private static final int PORT_TO_USE = 9493
  private static final String address = "http://localhost:${PORT_TO_USE}"

  private HttpServer server;

  static def String getAddress() {
    address
  }

  @Override
  public void start(Future deployed) {
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

    router.route('/item-storage/items/*').handler(this.&serverError)
    router.route('/instance-storage/instances/*').handler(this.&serverError)
  }

  @Override
  public void stop(Future stopped) {
    println "Stopping failing storage module"
    server.close({ result ->
      if (result.succeeded()) {
        println "Stopped listening on ${server.actualPort()}"
        stopped.complete();
      } else {
        stopped.fail(result.cause());
      }
    });
  }

  private void serverError(RoutingContext routingContext) {
    ServerErrorResponse.internalError(routingContext.response(), "Server Error")
  }
}
