package org.folio.inventory

import org.folio.metadata.common.VertxAssistant

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

public class Launcher {
  private static VertxAssistant vertxAssistant = new VertxAssistant()
  private static String inventoryModuleDeploymentId

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
    vertxAssistant.start()

    println "Server Starting"

    def deployed = new CompletableFuture()

    vertxAssistant.deployGroovyVerticle(InventoryVerticle.class.name,
      config, deployed)

    deployed.thenAccept({ println "Server Started" })

    inventoryModuleDeploymentId = deployed.get(20, TimeUnit.SECONDS)
  }

  public static stop() {
    def undeployed = new CompletableFuture()
    def stopped = new CompletableFuture()
    def all = CompletableFuture.allOf(undeployed, stopped)

    println("Server Stopping")

    vertxAssistant.undeployVerticle(inventoryModuleDeploymentId, undeployed)

    undeployed.thenAccept({
      vertxAssistant.stop(stopped)
    })

    all.join()
    println("Server Stopped")
  }
}
