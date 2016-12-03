package org.folio.inventory.storage.external

import org.folio.metadata.common.VertxAssistant
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.runner.RunWith;
import org.junit.runners.Suite
import org.folio.inventory.storage.external.support.FakeInventoryStorageModule

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit;

@RunWith(Suite.class)

@Suite.SuiteClasses([
  ExternalItemCollectionExamples.class,
  ExternalInstanceCollectionExamples.class
])

public class ExternalStorageSuite {
  private static final VertxAssistant vertxAssistant = new VertxAssistant();
  private static String storageModuleDeploymentId

  public static useVertx(Closure action) {
    vertxAssistant.useVertx(action)
  }

  static String getStorageAddress() {
    FakeInventoryStorageModule.address
  }

  static Collection<String> getExpectedTenants() {
    ["test-tenant-1"]
  }

  @BeforeClass
  static void beforeAll() {
    vertxAssistant.start()

    def deployed = new CompletableFuture()

    vertxAssistant.deployGroovyVerticle(
      FakeInventoryStorageModule.class.name,
      ["expectedTenants" : expectedTenants],
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
