package api

import io.vertx.groovy.core.Vertx
import org.folio.inventory.InventoryVerticle
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.runner.RunWith
import org.junit.runners.Suite

import java.util.concurrent.CompletableFuture

@RunWith(Suite.class)

@Suite.SuiteClasses([
  ModsIngestExamples.class
])

public class ApiTestSuite {

  private static Vertx vertx;
  public static final INVENTORY_VERTICLE_TEST_PORT = 9603

  @BeforeClass
  public static void before() {
    startVertx()
    startInventoryVerticle()
  }

  @AfterClass
  public static void after() {
    stopVertx()
  }

  private static startVertx() {
    vertx = Vertx.vertx()
  }

  private static startInventoryVerticle() {
    def config = ["port": INVENTORY_VERTICLE_TEST_PORT,
                  "storage.type" : "memory"]

    InventoryVerticle.deploy(vertx, config).join()
  }

  private static stopVertx() {
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
    }
  }
}
