package org.folio.catalogue.core

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
    def config = getApiConfig()

    vertx = Vertx.vertx()

    println "Server Starting"

    def deploy = ApiVerticle.deploy(vertx, config)

    deploy.thenAccept({ println "Server Started" })
  }

  private static Map getApiConfig() {
    def port = Integer.getInteger("catalogue.api.port")
    def apiBaseAddress = System.getProperty("catalogue.api.baseaddress")

    if (port == null) {
      println("Warning: No port provided, using default")
    }

    if (apiBaseAddress != null) {
      println("API base address provided: ${apiBaseAddress}")
    }

    ["port": port, "apiBaseAddress": apiBaseAddress]
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
