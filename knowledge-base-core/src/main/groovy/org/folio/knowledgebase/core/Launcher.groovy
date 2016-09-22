package org.folio.knowledgebase.core

import io.vertx.groovy.core.Vertx

import java.util.concurrent.CompletableFuture

public class Launcher {
  private static Vertx vertx

  public static void main(String[] args) {
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        Launcher.stop()
      }
    });

    start()
  }

  public static start() {
    def port = Integer.getInteger("knowledgebase.api.port")

    if(port == null) {
      println("Warning: No port provided, using default")
    }

    vertx = Vertx.vertx()

    println "Server Starting"

    def deploy = ApiVerticle.deploy(vertx, ["port" : port])

    deploy.thenAccept({ println "Server Started" })
  }

  public static stop() {
    println "Server Stopping"

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

      println "Server Stopped"
    }
  }
}
