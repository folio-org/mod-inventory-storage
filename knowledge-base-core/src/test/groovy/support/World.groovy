package support

import io.vertx.groovy.core.Vertx
import org.folio.knowledgebase.core.ApiVerticle
import org.folio.knowledgebase.core.storage.Storage

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

import static support.HttpClient.get

class World {
  private static vertx
  public static final testPortToUse = 9601

  static reset() {
    Storage.clear()
  }

  static def startVertx() {
    vertx = Vertx.vertx()
    vertx
  }

  static def startApi() {
    ApiVerticle.deploy(vertx, ["port": testPortToUse]).join()
  }

  static def stopVertx() {
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

  static URL instanceApiRoot() {
    new URL(get(World.apiRoot()).links.instances)
  }

  static URL apiRoot() {
    def directAddress = new URL("http://localhost:${testPortToUse}/knowledge-base")

    def useOkapi = (System.getProperty("okapi.use") ?: "").toBoolean()

    useOkapi ? new URL(System.getProperty("okapi.address") + '/knowledge-base') : directAddress
  }
}
