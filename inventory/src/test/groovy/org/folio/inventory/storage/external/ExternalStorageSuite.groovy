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

  static String getItemStorageAddress() {
    if(!useFakeStorageModule()) {
      def externalStorageAddress = System.getProperty(
        "inventory.storage.address")

      def address = "${externalStorageAddress}/item-storage"
      println "Using External Storage Module: ${address}"

      address
    }
    else {
      println "Using Fake Storage Module"
      FakeInventoryStorageModule.address
    }
  }

  static String getInstanceStorageAddress() {
    if(!useFakeStorageModule()) {
      def externalStorageAddress = System.getProperty(
        "inventory.storage.address")

      def address = "${externalStorageAddress}/instance-storage"
      println "Using External Storage Module: ${address}"

      address
    }
    else {
      println "Using Fake Storage Module"
      FakeInventoryStorageModule.address
    }
  }

  static Collection<String> getExpectedTenants() {
    ["test_tenant_1", "test_tenant_2"]
  }

  @BeforeClass
  static void beforeAll() {
    vertxAssistant.start()

    if(useFakeStorageModule()) {
      println("Starting Fake Storage Module")

      def deployed = new CompletableFuture()

      vertxAssistant.deployGroovyVerticle(
        FakeInventoryStorageModule.class.name,
        ["expectedTenants": expectedTenants],
        deployed)

      storageModuleDeploymentId = deployed.get(20000, TimeUnit.MILLISECONDS)
    }
  }

  private static boolean useFakeStorageModule() {
    Boolean.parseBoolean(
      System.getProperty('inventory.storage.use.fake', "true"))
  }

  @AfterClass()
  static void afterAll() {

    if(useFakeStorageModule()) {
      def undeployed = new CompletableFuture()

      vertxAssistant.undeployVerticle(storageModuleDeploymentId, undeployed)

      undeployed.get(20000, TimeUnit.MILLISECONDS)
    }

    vertxAssistant.stop()
  }
}
