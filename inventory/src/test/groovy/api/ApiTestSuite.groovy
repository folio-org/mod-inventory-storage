package api

import org.folio.inventory.InventoryVerticle
import org.folio.metadata.common.VertxAssistant
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.runner.RunWith
import org.junit.runners.Suite

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@RunWith(Suite.class)

@Suite.SuiteClasses([
  ModsIngestExamples.class,
  InstancesApiExamples.class
])

public class ApiTestSuite {
  public static final INVENTORY_VERTICLE_TEST_PORT = 9603

  private static VertxAssistant vertxAssistant = new VertxAssistant();
  private static String inventoryModuleDeploymentId

  @BeforeClass
  public static void before() {
    startVertx()
    startInventoryVerticle()
  }

  @AfterClass
  public static void after() {
    stopInventoryVerticle()
    stopVertx()
  }


  private static stopVertx() {
    vertxAssistant.stop()
  }

  private static startVertx() {
    vertxAssistant.start()
  }

  private static startInventoryVerticle() {
    def deployed = new CompletableFuture()

    def config = ["port": INVENTORY_VERTICLE_TEST_PORT,
                  "storage.type" : "memory"]

    vertxAssistant.deployGroovyVerticle(
      InventoryVerticle.class.name, config,  deployed)

    inventoryModuleDeploymentId = deployed.get(20000, TimeUnit.MILLISECONDS)
  }

  private static stopInventoryVerticle() {
    def undeployed = new CompletableFuture()

    vertxAssistant.undeployVerticle(inventoryModuleDeploymentId, undeployed)

    undeployed.get(20000, TimeUnit.MILLISECONDS)
  }
}
