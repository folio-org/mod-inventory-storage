package org.folio.rest.support;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.CompletableFutureUtil.mapFutureResultToJavaFuture;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CompletableFuture;
import org.junit.Test;

public class CompletableFutureUtilTest {
  @Test
  public void shouldReturnSuccess() {
    final CompletableFuture<Void> future = new CompletableFuture<>();

    mapFutureResultToJavaFuture(future).handle(succeededFuture());

    assertTrue(future.isDone());
  }

  @Test
  public void shouldReturnFailureWhenHandlerFailed() {
    final CompletableFuture<Void> future = new CompletableFuture<>();

    mapFutureResultToJavaFuture(future).handle(failedFuture("error"));

    assertTrue(future.isCompletedExceptionally());
  }
}
