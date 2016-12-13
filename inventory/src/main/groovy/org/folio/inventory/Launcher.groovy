package org.folio.inventory

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

    def config = [:]

    def storageType = System.getProperty(
      "org.folio.metadata.inventory.storage.type", "memory")

    def storageLocation = System.getProperty(
      "org.folio.metadata.inventory.storage.location", null)

    println("Storage type: ${storageType}")
    println("Storage location: ${storageLocation}")

    config.put("storage.type", storageType)
    config.put("storage.location", storageLocation)

    start(config)
  }

  public static start(LinkedHashMap config) {
    vertx = Vertx.vertx()

    println "Server Starting"

    def deploy = InventoryVerticle.deploy(vertx, config)

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
