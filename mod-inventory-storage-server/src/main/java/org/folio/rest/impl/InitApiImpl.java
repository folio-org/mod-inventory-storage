package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.ApplicationConfig;
import org.folio.persist.InstanceRepository;
import org.folio.rest.resource.interfaces.InitAPI;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.caches.SettingCache;
import org.folio.services.consortium.ServicePointSynchronizationVerticle;
import org.folio.services.consortium.ShadowInstanceSynchronizationVerticle;
import org.folio.services.consortium.SynchronizationVerticle;
import org.folio.services.instance.InstanceDateTypeService;
import org.folio.services.migration.async.AsyncBaseMigrationService;
import org.folio.services.migration.async.AsyncMigrationConsumerVerticle;
import org.folio.services.migration.async.AsyncMigrationJobService;
import org.folio.services.s3storage.FolioS3ClientFactory;
import org.folio.services.s3storage.FolioS3ClientFactory.S3ConfigType;
import org.folio.services.servicepoint.ServicePointService;
import org.folio.services.setting.SettingUpdateConsumerVerticle;
import org.folio.spring.SpringContextUtil;
import org.folio.utils.Environment;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings({"java:S6813", "SpringJavaInjectionPointsAutowiringInspection"})
public class InitApiImpl implements InitAPI {

  private static final Logger log = LogManager.getLogger();

  private static final String S3_CONFIG_REQUIRED_KEY = "S3_CONFIG_REQUIRED";

  @Autowired
  private HttpClient httpClient;
  @Autowired
  private ConsortiumDataCache consortiumDataCache;
  @Autowired
  private SettingCache settingCache;
  @Autowired
  private AsyncMigrationJobService asyncMigrationJobService;
  @Autowired
  private List<AsyncBaseMigrationService> asyncMigrationServices;
  @Autowired
  private InstanceRepository instanceRepository;
  @Autowired
  private ServicePointService servicePointService;
  @Autowired
  private InstanceDateTypeService instanceDateTypeService;

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> handler) {
    SpringContextUtil.init(vertx, context, ApplicationConfig.class);
    SpringContextUtil.autowireDependencies(this, context);
    validateS3Configurations()
      .compose(v -> initAsyncMigrationVerticle(vertx))
      .compose(v -> initShadowInstanceSynchronizationVerticle(vertx))
      .compose(v -> initSynchronizationVerticle(vertx))
      .compose(v -> initServicePointSynchronizationVerticle(vertx))
      .compose(v -> initSettingUpdateConsumerVerticle(vertx))
      .map(true)
      .onComplete(handler);
  }

  private Future<Void> validateS3Configurations() {
    var isS3ConfigRequired = Environment.getBoolValue(S3_CONFIG_REQUIRED_KEY, Boolean.FALSE);
    if (Boolean.FALSE.equals(isS3ConfigRequired)) {
      return Future.succeededFuture();
    }
    try {
      for (S3ConfigType configType : S3ConfigType.values()) {
        var folioS3Client = FolioS3ClientFactory.getFolioS3Client(configType);
        folioS3Client.createBucketIfNotExists();
        log.info("validateS3Configurations:: S3 client configured successfully for config type {}", configType);
      }
      return Future.succeededFuture();
    } catch (Exception e) {
      log.error("init:: S3 configuration validation failed", e);
      return Future.failedFuture(e);
    }
  }

  private Future<Void> initAsyncMigrationVerticle(Vertx vertx) {
    long startTime = System.currentTimeMillis();
    DeploymentOptions options = new DeploymentOptions();
    options.setThreadingModel(ThreadingModel.WORKER);
    options.setInstances(1);

    return vertx.deployVerticle(
        () -> new AsyncMigrationConsumerVerticle(asyncMigrationJobService, asyncMigrationServices), options)
      .onSuccess(result -> {
        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info("initAsyncMigrationVerticle:: AsyncMigrationConsumerVerticle was deployed in {} milliseconds",
          elapsedTime);
      })
      .onFailure(failure ->
        log.error("initAsyncMigrationVerticle:: AsyncMigrationConsumerVerticle was not started", failure))
      .mapEmpty();
  }

  private Future<Void> initShadowInstanceSynchronizationVerticle(Vertx vertx) {
    DeploymentOptions options = new DeploymentOptions()
      .setThreadingModel(ThreadingModel.WORKER)
      .setInstances(1);

    return vertx.deployVerticle(
        () -> new ShadowInstanceSynchronizationVerticle(consortiumDataCache, httpClient, instanceRepository), options)
      .onSuccess(v -> log.info("initShadowInstanceSynchronizationVerticle:: "
                               + "ShadowInstanceSynchronizationVerticle verticle was successfully started"))
      .onFailure(e -> log.error("initShadowInstanceSynchronizationVerticle:: "
                                + "ShadowInstanceSynchronizationVerticle verticle was not successfully started", e))
      .mapEmpty();
  }

  private Future<Object> initSynchronizationVerticle(Vertx vertx) {
    DeploymentOptions options = new DeploymentOptions()
      .setThreadingModel(ThreadingModel.WORKER)
      .setInstances(1);

    return vertx.deployVerticle(
        () -> new SynchronizationVerticle(consortiumDataCache, httpClient, instanceDateTypeService), options)
      .onSuccess(v -> log.info("initSynchronizationVerticle:: "
                               + "SynchronizationVerticle verticle was successfully started"))
      .onFailure(e -> log.error("initSynchronizationVerticle:: "
                                + "SynchronizationVerticle verticle was not successfully started", e))
      .mapEmpty();
  }

  private Future<Object> initServicePointSynchronizationVerticle(Vertx vertx) {

    DeploymentOptions options = new DeploymentOptions()
      .setThreadingModel(ThreadingModel.WORKER)
      .setInstances(1);

    return vertx.deployVerticle(
        () -> new ServicePointSynchronizationVerticle(consortiumDataCache, httpClient, servicePointService),
        options)
      .onSuccess(v -> log.info("initServicePointSynchronizationVerticle:: "
                               + "ServicePointSynchronizationVerticle verticle was successfully started"))
      .onFailure(e -> log.error("initServicePointSynchronizationVerticle:: "
                                + "ServicePointSynchronizationVerticle verticle was not successfully started", e))
      .mapEmpty();
  }

  private Future<Void> initSettingUpdateConsumerVerticle(Vertx vertx) {
    return vertx.deployVerticle(
        new SettingUpdateConsumerVerticle(settingCache), SettingUpdateConsumerVerticle.getDeploymentOptions())
      .onSuccess(v -> log.info("initSettingUpdateConsumerVerticle:: SettingUpdateConsumerVerticle verticle "
        + "was successfully started"))
      .onFailure(e -> log.error("initSettingUpdateConsumerVerticle:: SettingUpdateConsumerVerticle verticle "
        + "was not successfully started", e))
      .mapEmpty();
  }
}
