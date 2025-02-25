package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.resource.interfaces.InitAPI;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.consortium.ServicePointSynchronizationVerticle;
import org.folio.services.consortium.ShadowInstanceSynchronizationVerticle;
import org.folio.services.consortium.SynchronizationVerticle;
import org.folio.services.migration.async.AsyncMigrationConsumerVerticle;

public class InitApiImpl implements InitAPI {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> handler) {
    initConsortiumDataCache(vertx, context);
    initAsyncMigrationVerticle(vertx)
      .compose(v -> initShadowInstanceSynchronizationVerticle(vertx, getConsortiumDataCache(context)))
      .compose(v -> initSynchronizationVerticle(vertx, getConsortiumDataCache(context)))
      .compose(v -> initServicePointSynchronizationVerticle(vertx, getConsortiumDataCache(context)))
      .map(true)
      .onComplete(handler);
  }

  private Future<Void> initAsyncMigrationVerticle(Vertx vertx) {
    Promise<Void> promise = Promise.promise();
    long startTime = System.currentTimeMillis();
    DeploymentOptions options = new DeploymentOptions();
    options.setThreadingModel(ThreadingModel.WORKER);
    options.setInstances(1);

    vertx.deployVerticle(AsyncMigrationConsumerVerticle.class, options, result -> {
      if (result.succeeded()) {
        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info("initAsyncMigrationVerticle:: AsyncMigrationConsumerVerticle was deployed in {} milliseconds",
          elapsedTime);
        promise.complete();
      } else {
        log.error("initAsyncMigrationVerticle:: AsyncMigrationConsumerVerticle was not started", result.cause());
        promise.fail(result.cause());
      }
    });
    return promise.future();
  }

  private Future<Void> initShadowInstanceSynchronizationVerticle(Vertx vertx, ConsortiumDataCache consortiumDataCache) {
    DeploymentOptions options = new DeploymentOptions()
      .setThreadingModel(ThreadingModel.WORKER)
      .setInstances(1);

    return vertx.deployVerticle(() -> new ShadowInstanceSynchronizationVerticle(consortiumDataCache), options)
      .onSuccess(v -> log.info("initShadowInstanceSynchronizationVerticle:: "
        + "ShadowInstanceSynchronizationVerticle verticle was successfully started"))
      .onFailure(e -> log.error("initShadowInstanceSynchronizationVerticle:: "
        + "ShadowInstanceSynchronizationVerticle verticle was not successfully started", e))
      .mapEmpty();
  }

  private Future<Object> initSynchronizationVerticle(Vertx vertx, ConsortiumDataCache consortiumDataCache) {
    DeploymentOptions options = new DeploymentOptions()
      .setThreadingModel(ThreadingModel.WORKER)
      .setInstances(1);

    return vertx.deployVerticle(() -> new SynchronizationVerticle(consortiumDataCache), options)
      .onSuccess(v -> log.info("initSynchronizationVerticle:: "
                               + "SynchronizationVerticle verticle was successfully started"))
      .onFailure(e -> log.error("initSynchronizationVerticle:: "
                                + "SynchronizationVerticle verticle was not successfully started", e))
      .mapEmpty();
  }

  private Future<Object> initServicePointSynchronizationVerticle(Vertx vertx,
    ConsortiumDataCache consortiumDataCache) {

    DeploymentOptions options = new DeploymentOptions()
      .setThreadingModel(ThreadingModel.WORKER)
      .setInstances(1);

    return vertx.deployVerticle(() -> new ServicePointSynchronizationVerticle(consortiumDataCache),
        options)
      .onSuccess(v -> log.info("initServicePointSynchronizationVerticle:: "
        + "ServicePointSynchronizationVerticle verticle was successfully started"))
      .onFailure(e -> log.error("initServicePointSynchronizationVerticle:: "
        + "ServicePointSynchronizationVerticle verticle was not successfully started", e))
      .mapEmpty();
  }

  private void initConsortiumDataCache(Vertx vertx, Context context) {
    ConsortiumDataCache consortiumDataCache = new ConsortiumDataCache(vertx, vertx.createHttpClient());
    context.put(ConsortiumDataCache.class.getName(), consortiumDataCache);
  }

  private ConsortiumDataCache getConsortiumDataCache(Context context) {
    return context.get(ConsortiumDataCache.class.getName());
  }

}
