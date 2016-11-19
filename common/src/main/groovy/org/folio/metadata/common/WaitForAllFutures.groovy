package org.folio.metadata.common

import java.util.concurrent.CompletableFuture

import static org.folio.metadata.common.FutureAssistance.*

class WaitForAllFutures<T> {

  private allFutures = new ArrayList<CompletableFuture<T>>()

  Closure notifyComplete() {

    def newFuture = new CompletableFuture()

    allFutures.add(newFuture)

    complete(newFuture)
  }

  void waitForCompletion() {
    CompletableFuture.allOf(*allFutures).join()
  }
}
