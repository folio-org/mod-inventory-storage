package org.folio.metadata.common

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class FutureAssistance {
  static <T> T getOnCompletion(CompletableFuture<T> future) {
    future.get(2000, TimeUnit.MILLISECONDS)
  }

  static Closure complete(CompletableFuture future) {
    return { future.complete(it) }
  }
}
