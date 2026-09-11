package org.folio.rest.support;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.CompletableFutureUtil.mapFutureResultToJavaFuture;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class CompletableFutureUtilTest {
  @Test
  void shouldReturnSuccess() {
    final CompletableFuture<Void> future = new CompletableFuture<>();

    mapFutureResultToJavaFuture(future).handle(succeededFuture());

    assertTrue(future.isDone());
  }

  @Test
  void shouldReturnFailureWhenHandlerFailed() {
    final CompletableFuture<Void> future = new CompletableFuture<>();

    mapFutureResultToJavaFuture(future).handle(failedFuture("error"));

    assertTrue(future.isCompletedExceptionally());
  }
}
