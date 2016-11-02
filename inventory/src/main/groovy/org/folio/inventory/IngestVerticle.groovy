package org.folio.inventory

import io.vertx.core.Future
import io.vertx.groovy.core.Vertx
import io.vertx.groovy.core.buffer.Buffer
import io.vertx.groovy.core.http.HttpServer
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.handler.BodyHandler
import io.vertx.lang.groovy.GroovyVerticle
import org.folio.inventory.domain.Item
import org.folio.inventory.org.folio.inventory.ingest.ModsParser
import org.folio.metadata.common.WebRequestDiagnostics
import org.folio.metadata.common.api.response.JsonResponse

import java.util.concurrent.CompletableFuture

public class IngestVerticle extends GroovyVerticle {

  private HttpServer server;

  public static void deploy(Vertx vertx, Map config, CompletableFuture deployed) {
    def options = ["config": config, "worker": true]

    vertx.deployVerticle("groovy:org.folio.inventory.IngestVerticle", options, { res ->
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

    router.route().handler(WebRequestDiagnostics.&outputDiagnostics)

    router.post("/ingest" + "*").handler(BodyHandler.create())

    router.post("/ingest/mods").handler({ routingContext ->
      routingContext.fileUploads().each { f ->

        //Definitely shouldn't be blocking for large files
        Buffer uploadedFile = vertx.fileSystem().readFileBlocking(f.uploadedFileName());

        Item item = new ModsParser().parseRecord(uploadedFile.toString())

        JsonResponse.success(routingContext.response(), item)
      }
    })

    def handler = { result ->
      if (result.succeeded()) {
        println "Listening on ${server.actualPort()}"
        started.complete();
      } else {
        started.fail(result.cause());
      }
    }

    server = vertx.createHttpServer()

    def config = vertx.getOrCreateContext().config()

    server.requestHandler(router.&accept).listen(config.port ?: 9403, handler)
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
