package org.folio.catalogue.core

import org.folio.catalogue.core.api.resource.ItemResource
import org.folio.catalogue.core.storage.Storage
import io.vertx.lang.groovy.GroovyVerticle
import io.vertx.core.Future
import io.vertx.groovy.core.http.HttpServer
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.core.Vertx
import org.folio.catalogue.core.support.WebRequestDiagnostics
import org.folio.catalogue.core.api.resource.RootResource

import java.util.concurrent.CompletableFuture

public class ApiVerticle extends GroovyVerticle {

  private HttpServer server;

  public static void deploy(Vertx vertx, Map config, CompletableFuture deployed) {
    def options = ["config": config, "worker": true]
    
    vertx.deployVerticle("groovy:org.folio.catalogue.core.ApiVerticle", options, { res ->
      if (res.succeeded()) {
        deployed.complete(null);
      } else {
        deployed.completeExceptionally(res.cause());
      }
    });
  }

  public static CompletableFuture<Void> deploy(Vertx vertx, Map config) {
    def deployed = new CompletableFuture()

    deploy(vertx, config, deployed)

    deployed
  }

  @Override
  public void start(Future started) {

    Config.initialiseFrom(vertx.getOrCreateContext().config())
//
    def router = Router.router(vertx)

    router.route().handler(WebRequestDiagnostics.&outputDiagnostics)

    RootResource.register(router)
    ItemResource.register(router, Storage.collectionProvider.itemCollection)

    def handler = { result ->
      if (result.succeeded()) {
        println "Listening on ${server.actualPort()}"
        started.complete();
      } else {
        started.fail(result.cause());
      }
    }

    server = vertx.createHttpServer()

    server.requestHandler(router.&accept).listen(Config.port ?: 9402, handler)
  }

  @Override
  public void stop(Future stopped) {
    println "Stopping catalogue API"
    server.close({ result ->
      if (result.succeeded()) {
        println "Stopped listening on ${server.actualPort()}"
        stopped.complete();
      } else {
        stopped.fail(result.cause());
      }
    });
  }
}
