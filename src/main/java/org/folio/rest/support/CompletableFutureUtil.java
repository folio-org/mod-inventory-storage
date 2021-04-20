package org.folio.rest.support;

import java.util.concurrent.CompletableFuture;
import io.vertx.core.AsyncResult;
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

}
