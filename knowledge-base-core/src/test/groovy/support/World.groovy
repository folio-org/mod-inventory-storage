package support

import knowledgebase.core.Launcher
import knowledgebase.core.storage.Storage

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

import static support.HttpClient.get

class World {
  static reset() {
    Storage.clear()
  }

  static def startApi() {
    Launcher.start()
  }

  static def stopApi() {
    Launcher.stop()
  }

  static URL instanceApiRoot() {
    new URL(get(World.apiRoot()).links.instances)
  }

  static URL apiRoot() {
    def directAddress = new URL('http://localhost:9401/knowledge-base')
    def useOkapi = (System.getProperty("okapi.use") ?: "").toBoolean()

    useOkapi ? new URL(System.getProperty("okapi.address") + '/knowledge-base') : directAddress
  }

  static <T> T getOnCompletion(CompletableFuture<T> future) {
    future.get(2000, TimeUnit.MILLISECONDS)
  }

  static Closure complete(CompletableFuture future) {
    return { future.complete(it) }
  }
}
