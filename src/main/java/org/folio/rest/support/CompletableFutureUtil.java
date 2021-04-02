package org.folio.rest.support;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class CompletableFutureUtil {

  private static final Logger log = LogManager.getLogger();

  public static final long TIMEOUT = 180;

  private CompletableFutureUtil() {}

  public static <T> Handler<AsyncResult<T>> mapFutureResultToJavaFuture(CompletableFuture<T> future) {
    return result -> {
      if (result.succeeded()) {
        future.complete(result.result());
      } else {
        future.completeExceptionally(result.cause());
      }
    };
  }

  public static <T> T getFutureResult(Future<T> future) {
    log.info("Started \"getFutureResult\", future: {} ...", future);
    return getFutureResult(future.toCompletionStage().toCompletableFuture());
  }

  public static <T> T getFutureResult(CompletableFuture<T> completableFuture) {
    log.info("Started \"getFutureResult\", completableFuture: {} ...", completableFuture);
    try {
      return completableFuture.get(TIMEOUT, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      log.error("Error occurs in getFutureResult: {}", e.getMessage());
      throw new RuntimeException(e);
    }
  }

}
