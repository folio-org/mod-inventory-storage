package org.folio.inventory.storage

import org.folio.metadata.common.VertxAssistant
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.runner.RunWith;
import org.junit.runners.Suite
import support.FakeInventoryStorageModule

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit;

@RunWith(Suite.class)

@Suite.SuiteClasses([
  ExternalItemCollectionExamples.class,
  ExternalInstanceCollectionExamples.class
])

public class ExternalStorageSuite {
  private static final VertxAssistant vertxAssistant = new VertxAssistant();
  private static String deploymentId

  public static useVertx(Closure action) {
    vertxAssistant.useVertx(action)
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
