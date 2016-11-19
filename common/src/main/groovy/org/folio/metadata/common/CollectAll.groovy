package org.folio.metadata.common

import java.util.concurrent.CompletableFuture

import static org.folio.metadata.common.FutureAssistance.complete

class CollectAll<T> {

  private allFutures = new ArrayList<CompletableFuture<T>>()

  Closure receive() {

    def newFuture = new CompletableFuture()

    allFutures.add(newFuture)

    complete(newFuture)
  }

  void collect(Closure action) {
    if(action != null) {
      CompletableFuture.allOf(*allFutures)
        .thenApply { action(allFutures.collect { it.get() }) }
    }
  }
}
