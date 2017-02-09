package org.folio.metadata.common

import org.folio.metadata.common.domain.Failure
import org.folio.metadata.common.domain.Success

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class FutureAssistance {
  static <T> T getOnCompletion(CompletableFuture<T> future) {
    future.get(2000, TimeUnit.MILLISECONDS)
  }

  static void waitForCompletion(CompletableFuture future) {
    future.get(2000, TimeUnit.MILLISECONDS)
  }

  static Closure complete(CompletableFuture future) {
    return { future.complete(it) }
  }

  static <T> Consumer<Success<T>> succeed(CompletableFuture<T> future) {
    return { future.complete(it.result) }
  }

  static Consumer<Failure> fail(CompletableFuture future) {
    return { Failure failure -> future.completeExceptionally(
      new Exception(failure.reason))
    }
  }
}
