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
import org.folio.services.DomainEventConsumerVerticle;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.migration.async.AsyncMigrationConsumerVerticle;

public class InitApiImpl implements InitAPI {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> handler) {
    initConsortiumDataCache(vertx, context);

    initAsyncMigrationVerticle(vertx)
      .compose(v -> initDomainEventConsumerVerticle(vertx))
      .onComplete(v -> handler.handle(Future.succeededFuture()))
      .onFailure(th -> handler.handle(Future.failedFuture(th)));
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
        log.info("AsyncMigrationConsumerVerticle was deployed in {} milliseconds", elapsedTime);
        promise.complete();
      } else {
        log.error("AsyncMigrationConsumerVerticle was not started", result.cause());
        promise.fail(result.cause());
      }
    });
    return promise.future();
  }

  private Future<Void> initDomainEventConsumerVerticle(Vertx vertx) {
    DeploymentOptions options = new DeploymentOptions()
      .setWorker(true)
      .setInstances(1);

    return vertx.deployVerticle(DomainEventConsumerVerticle.class, options)
      .onSuccess(ar -> log.info(
        "initDomainEventConsumerVerticle:: DomainEventConsumerVerticle verticle was successfully started"))
      .onFailure(e -> log.error(
        "initDomainEventConsumerVerticle:: DomainEventConsumerVerticle verticle was not successfully started", e))
      .mapEmpty();
  }

  private void initConsortiumDataCache(Vertx vertx, Context context) {
    ConsortiumDataCache consortiumDataCache = new ConsortiumDataCache(vertx, vertx.createHttpClient());
    context.put(ConsortiumDataCache.class.getName(), consortiumDataCache);
  }
}
