package org.folio.metadata.common

import org.folio.metadata.common.domain.Success

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

import static org.folio.metadata.common.FutureAssistance.complete
import static org.folio.metadata.common.FutureAssistance.succeed

class WaitForAllFutures<T> {

  private allFutures = new ArrayList<CompletableFuture<T>>()

  Closure notifyComplete() {

    def newFuture = new CompletableFuture()

    allFutures.add(newFuture)

    complete(newFuture)
  }

  Consumer<Success> notifySuccess() {

    def newFuture = new CompletableFuture()

    allFutures.add(newFuture)

    succeed(newFuture)
  }

  void waitForCompletion() {
    CompletableFuture.allOf(*allFutures).get(5000, TimeUnit.MILLISECONDS)
  }
}
