package org.folio.metadata.common

import org.folio.metadata.common.domain.Success

import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

import static org.folio.metadata.common.FutureAssistance.succeed

class CollectAll<T> {

  private allFutures = new ArrayList<CompletableFuture<T>>()

  Consumer<Success> receive() {
    def newFuture = new CompletableFuture()

    allFutures.add(newFuture)

    succeed(newFuture)
  }

  void collect(Closure action) {
    if(action != null) {
      CompletableFuture.allOf(*allFutures)
        .thenApply { action(allFutures.collect { it.get() }) }
    }
  }
}
