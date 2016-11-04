package org.folio.metadata.common

import io.vertx.groovy.core.Vertx
import java.util.concurrent.CompletableFuture

class VertxAssistant {

  public Vertx vertx

  Vertx start() {
    this.vertx = Vertx.vertx()
  }

  void stop(Vertx vertx) {
    if (vertx != null) {
      def stopped = new CompletableFuture()

      vertx.close({ res ->
        if (res.succeeded()) {
          stopped.complete(null);
        } else {
          stopped.completeExceptionally(res.cause());
        }
      })

      stopped.join()
    }
  }

  void deployGroovyVerticle(String verticleClass, CompletableFuture deployed) {
    def startTime = System.currentTimeMillis()

    vertx.deployVerticle("groovy:" + verticleClass, ["worker": true ], { res ->
      if (res.succeeded()) {
        def elapsedTime = System.currentTimeMillis() - startTime
        println("${verticleClass} deployed in ${elapsedTime} milliseconds")
        deployed.complete(null);
      } else {
        deployed.completeExceptionally(res.cause());
      }
    });
  }
}
