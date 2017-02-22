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

    def port = Integer.getInteger("port", 9403)

    def storageType = System.getProperty(
      "org.folio.metadata.inventory.storage.type", null)

    def storageLocation = System.getProperty(
      "org.folio.metadata.inventory.storage.location", null)

    putNonNullConfig("storage.type", storageType, config)
    putNonNullConfig("storage.location", storageLocation, config)
    putNonNullConfig("port", port, config)

    start(config)
  }

  static start(Map config) {
    vertxAssistant.start()

    println "Server Starting"

    def deployed = new CompletableFuture()

    vertxAssistant.deployGroovyVerticle(InventoryVerticle.class.name,
      config, deployed)

    deployed.thenAccept({ println "Server Started" })

    inventoryModuleDeploymentId = deployed.get(20, TimeUnit.SECONDS)
  }

  static stop() {
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

  private static void putNonNullConfig(String key, Object value, Map config) {
    if(value != null) {
      config.put(key, value)
    }
  }
}
