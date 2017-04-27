package org.folio.metadata.common

import io.vertx.groovy.core.Vertx
import java.util.concurrent.CompletableFuture

class VertxAssistant {

  private Vertx vertx

  def useVertx(Closure closure) {
    closure(vertx)
  }

  void start() {
    if(vertx == null) {
      this.vertx = Vertx.vertx()
    }
  }

  void stop() {
    def stopped = new CompletableFuture()

    stop(stopped)

    stopped.join()
  }

  void stop(CompletableFuture stopped) {

    if (vertx != null) {
      vertx.close({ res ->
        if (res.succeeded()) {
          stopped.complete(null);
        } else {
          stopped.completeExceptionally(res.cause());
        }
      })

      stopped.thenAccept({ vertx == null })
    }
  }

  void deployGroovyVerticle(String verticleClass,
                            Map<String, Object> config,
                            CompletableFuture<String> deployed) {

    def startTime = System.currentTimeMillis()

    def options = [:]

    options.config = config
    options.worker = true

    vertx.deployVerticle("groovy:" + verticleClass,
      options,
      { res ->
        if (res.succeeded()) {
          def elapsedTime = System.currentTimeMillis() - startTime
          println("${verticleClass} deployed in ${elapsedTime} milliseconds")
          deployed.complete(res.result());
        } else {
          deployed.completeExceptionally(res.cause());
        }
      });
  }

  void deployVerticle(String verticleClass,
                            Map<String, Object> config,
                            CompletableFuture<String> deployed) {

    def startTime = System.currentTimeMillis()

    def options = [:]

    options.config = config
    options.worker = true

    vertx.deployVerticle(verticleClass,
      options,
      { res ->
        if (res.succeeded()) {
          def elapsedTime = System.currentTimeMillis() - startTime
          println("${verticleClass} deployed in ${elapsedTime} milliseconds")
          deployed.complete(res.result());
        } else {
          deployed.completeExceptionally(res.cause());
        }
      });
  }


  void undeployVerticle(String deploymentId, CompletableFuture undeployed) {

    vertx.undeploy(deploymentId, { res ->
      if (res.succeeded()) {
        undeployed.complete();
      } else {
        undeployed.completeExceptionally(res.cause());
      }
    });
  }
}
