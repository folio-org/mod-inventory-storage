package org.folio.inventory.storage

import org.folio.inventory.domain.ItemCollection
import org.folio.inventory.storage.external.ExternalStorageModuleItemCollection
import org.folio.inventory.storage.memory.InMemoryItemCollection
import org.folio.metadata.common.VertxAssistant
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.runners.Parameterized
import support.FakeInventoryStorageModule

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class ExternalItemCollectionExamples extends ItemCollectionExamples {

  private static final VertxAssistant vertxAssistant = new VertxAssistant();
  private static String deploymentId

  ExternalItemCollectionExamples() {
    super(vertxAssistant.useVertx { new ExternalStorageModuleItemCollection(it) })
  }

  @BeforeClass
  public static void beforeAll() {
    vertxAssistant.start()

    def deployed = new CompletableFuture()

    vertxAssistant.deployGroovyVerticle(FakeInventoryStorageModule.class.name, deployed)

    deploymentId = deployed.get(20000, TimeUnit.MILLISECONDS)
  }

  @AfterClass()
  public static void afterAll() {
    def undeployed = new CompletableFuture()

    vertxAssistant.undeployVerticle(deploymentId, undeployed)

    undeployed.get(20000, TimeUnit.MILLISECONDS)

    vertxAssistant.stop()
  }
}
