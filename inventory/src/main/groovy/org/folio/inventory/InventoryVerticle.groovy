package org.folio.inventory

import io.vertx.core.Future
import io.vertx.groovy.core.Vertx
import io.vertx.groovy.core.http.HttpServer
import io.vertx.groovy.ext.web.Router
import io.vertx.lang.groovy.GroovyVerticle
import org.folio.inventory.domain.ingest.IngestMessageProcessor
import org.folio.inventory.resources.Instances
import org.folio.inventory.resources.Items
import org.folio.inventory.resources.ingest.ModsIngestion
import org.folio.inventory.storage.Storage
import org.folio.metadata.common.WebRequestDiagnostics

import java.util.concurrent.CompletableFuture

public class InventoryVerticle extends GroovyVerticle {

  private HttpServer server;

  public static void deploy(Vertx vertx, Map config, CompletableFuture deployed) {
    def options = ["config": config, "worker": true]

    vertx.deployVerticle("groovy:org.folio.inventory.InventoryVerticle", options, { res ->
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

    def router = Router.router(vertx)

    server = vertx.createHttpServer()

    def config = vertx.getOrCreateContext().config()

    println("Received Config")
    config.each { println("${it.key}:${it.value}") }

    def storage = Storage.basedUpon(vertx, config)

    new IngestMessageProcessor(storage).register(vertx.eventBus())

    router.route().handler(WebRequestDiagnostics.&outputDiagnostics)

    new ModsIngestion(storage).register(router)
    new Items(storage).register(router)
    new Instances(storage).register(router)

    def onHttpServerStart = { result ->
      if (result.succeeded()) {
        println "Listening on ${server.actualPort()}"
        started.complete();
      } else {
        started.fail(result.cause());
      }
    }

    server.requestHandler(router.&accept)
      .listen(config.port, onHttpServerStart)
  }

  @Override
  public void stop(Future stopped) {
    println "Stopping inventory module"
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
