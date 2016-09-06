package support

import catalogue.core.ApiVerticle
import catalogue.core.storage.Storage
import io.vertx.groovy.core.Vertx

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

import static support.HttpClient.get

class World {
  private static vertx

  static reset() {
    Storage.clear()
  }

  static def startVertx() {
    vertx = Vertx.vertx()
    vertx
  }

  static def startApi() {
    ApiVerticle.deploy(vertx).join()
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

  static URL itemApiRoot() {
    new URL(get(World.apiRoot()).links.items)
  }

  static URL apiRoot() {
    def directAddress = new URL('http://localhost:9402/catalogue')
    def useOkapi = (System.getProperty("okapi.use") ?: "").toBoolean()

    useOkapi ? new URL(System.getProperty("okapi.address") + '/catalogue') : directAddress
  }

  static <T> T getOnCompletion(CompletableFuture<T> future) {
    future.get(2000, TimeUnit.MILLISECONDS)
  }

  static Closure complete(CompletableFuture future) {
    return { future.complete(it) }
  }
}
