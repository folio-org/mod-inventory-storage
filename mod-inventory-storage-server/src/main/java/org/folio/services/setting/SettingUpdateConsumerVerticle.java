package org.folio.services.setting;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.ThreadingModel;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.InventoryKafkaTopic;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.GlobalLoadSensor;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaConsumerWrapper;
import org.folio.kafka.SubscriptionDefinition;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.services.caches.SettingCache;
import org.folio.utils.Environment;

public class SettingUpdateConsumerVerticle extends AbstractVerticle {
  private static final Logger log = LogManager.getLogger(SettingUpdateConsumerVerticle.class);
  private static final String LOAD_LIMIT_PARAM = "consumer.setting-update.load-limit";
  private static final int DEFAULT_LOAD_LIMIT = 5;
  private static final String TENANT_PATTERN = "\\w{1,}";
  private static final String CONSUMER_GROUP_ID = InventoryKafkaTopic.SETTING.moduleName()
    + SettingUpdateConsumerVerticle.class.getName() + "_" + UUID.randomUUID();
  private final SettingCache cache;

  public SettingUpdateConsumerVerticle(SettingCache cache) {
    this.cache = cache;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    log.info("start:: Starting SettingUpdateConsumerVerticle with consumerGroupId={}", CONSUMER_GROUP_ID);
    var handler = new SettingUpdateKafkaHandler(cache);

    createKafkaConsumerWrapper(handler).onComplete(startPromise);
  }

  private Future<Void> createKafkaConsumerWrapper(
    AsyncRecordHandler<String, String> recordHandler) {

    int loadLimit = Environment.getIntValue(LOAD_LIMIT_PARAM, DEFAULT_LOAD_LIMIT);
    KafkaConfig kafkaConfig = getKafkaConfig();
    SubscriptionDefinition subscriptionDefinition = SubscriptionDefinition.builder()
      .eventType(InventoryKafkaTopic.SETTING.topicName())
      .subscriptionPattern(InventoryKafkaTopic.SETTING.fullTopicName(TENANT_PATTERN))
      .build();

    KafkaConsumerWrapper<String, String> consumer = KafkaConsumerWrapper.<String, String>builder()
      .context(context)
      .vertx(vertx)
      .kafkaConfig(kafkaConfig)
      .loadLimit(loadLimit)
      .globalLoadSensor(new GlobalLoadSensor())
      .subscriptionDefinition(subscriptionDefinition)
      .build();

    return consumer
      .start(recordHandler, CONSUMER_GROUP_ID)
      .mapEmpty();
  }

  private KafkaConfig getKafkaConfig() {
    return KafkaConfig.builder()
      .envId(KafkaEnvironmentProperties.environment())
      .kafkaHost(KafkaEnvironmentProperties.host())
      .kafkaPort(KafkaEnvironmentProperties.port())
      .build();
  }

  public static DeploymentOptions getDeploymentOptions() {
    return new DeploymentOptions()
      .setThreadingModel(ThreadingModel.WORKER)
      .setInstances(1);
  }
}

