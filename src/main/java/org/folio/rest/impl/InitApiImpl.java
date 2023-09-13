package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.resource.interfaces.InitAPI;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.migration.async.AsyncMigrationConsumerVerticle;

public class InitApiImpl implements InitAPI {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> handler) {
    initAsyncMigrationVerticle(vertx)
      .onComplete(car -> {
        handler.handle(Future.succeededFuture());
        log.info("Consumer Verticles were successfully started");
      })
      .onFailure(th -> {
        handler.handle(Future.failedFuture(th));
        log.error("Consumer Verticles were not started", th);
      });
    initConsortiumDataCache(vertx, context);
  }

  private Future<Void> initAsyncMigrationVerticle(Vertx vertx) {
    Promise<Void> promise = Promise.promise();
    long startTime = System.currentTimeMillis();
    DeploymentOptions options = new DeploymentOptions();
    options.setWorker(true);
    options.setInstances(1);

    vertx.deployVerticle(AsyncMigrationConsumerVerticle.class, options, result -> {
      if (result.succeeded()) {
        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info(String.format(
          "%s deployed in %s milliseconds", AsyncMigrationConsumerVerticle.class.getName(), elapsedTime));
        promise.complete();
      } else {
        promise.fail(result.cause());
      }
    });
    return promise.future();
  }

  private void initConsortiumDataCache(Vertx vertx, Context context) {
    ConsortiumDataCache consortiumDataCache = new ConsortiumDataCache(vertx, vertx.createHttpClient());
    context.put(ConsortiumDataCache.class.getName(), consortiumDataCache);
  }
}
