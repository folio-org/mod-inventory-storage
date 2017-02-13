package org.folio.inventory.storage.external.failure

import org.folio.inventory.storage.external.support.FailureInventoryStorageModule
import org.folio.metadata.common.VertxAssistant
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.runner.RunWith
import org.junit.runners.Suite

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@RunWith(Suite.class)

@Suite.SuiteClasses([
  ExternalItemCollectionServerErrorExamples.class,
  ExternalItemCollectionBadRequestExamples.class,
  ExternalInstanceCollectionServerErrorExamples.class,
  ExternalInstanceCollectionBadRequestExamples.class,
])

public class ExternalStorageFailureSuite {
  private static final VertxAssistant vertxAssistant = new VertxAssistant();
  private static String storageModuleDeploymentId

  public static useVertx(Closure action) {
    vertxAssistant.useVertx(action)
  }

  static String getServerErrorStorageAddress() {
      FailureInventoryStorageModule.serverErrorAddress
  }

  static String getBadRequestStorageAddress() {
    FailureInventoryStorageModule.badRequestAddress
  }

  @BeforeClass
  static void beforeAll() {
    vertxAssistant.start()

    println("Starting Failing Storage Module")

    def deployed = new CompletableFuture()

    vertxAssistant.deployGroovyVerticle(
      FailureInventoryStorageModule.class.name,
      [:],
      deployed)

    storageModuleDeploymentId = deployed.get(20000, TimeUnit.MILLISECONDS)
  }

  @AfterClass()
  static void afterAll() {
    def undeployed = new CompletableFuture()

    vertxAssistant.undeployVerticle(storageModuleDeploymentId, undeployed)

    undeployed.get(20000, TimeUnit.MILLISECONDS)

    vertxAssistant.stop()
  }
}
