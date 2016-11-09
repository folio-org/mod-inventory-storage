package org.folio.metadata.common

import java.util.concurrent.CompletableFuture

import static org.folio.metadata.common.FutureAssistance.*

class WaitForAllFutures {

  private allFutures = new ArrayList<CompletableFuture>()

  Closure notifyComplete() {

    def newFuture = new CompletableFuture()

    allFutures.add(newFuture)

    complete(newFuture)
  }

  void waitForCompletion() {
    CompletableFuture.allOf(*allFutures).join()
  }
}
