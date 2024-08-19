package org.folio.services.consortium;

import static org.folio.InventoryKafkaTopic.INSTANCE;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.GlobalLoadSensor;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaConsumerWrapper;
import org.folio.kafka.SubscriptionDefinition;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.services.caches.ConsortiumDataCache;

public class SynchronizationVerticle extends AbstractVerticle {

  private static final String LOAD_LIMIT_PARAM = "consumer.instance-synchronization.load-limit";
  private static final String DEFAULT_LOAD_LIMIT = "5";
  private static final String TENANT_PATTERN = "\\w{1,}";

  private final ConsortiumDataCache consortiumDataCache;

  public SynchronizationVerticle(ConsortiumDataCache consortiumDataCache) {
    this.consortiumDataCache = consortiumDataCache;
  }

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    var httpClient = vertx.createHttpClient();
    var handler = new SynchronizationAsyncRecordHandler(consortiumDataCache, httpClient, vertx);

    createKafkaConsumerWrapper(handler).onComplete(startPromise);
  }

  private Future<Void> createKafkaConsumerWrapper(AsyncRecordHandler<String, String> recordHandler) {

    int loadLimit = Integer.parseInt(System.getProperty(LOAD_LIMIT_PARAM, DEFAULT_LOAD_LIMIT));
    KafkaConfig kafkaConfig = getKafkaConfig();
    SubscriptionDefinition subscriptionDefinition = SubscriptionDefinition.builder()
      .eventType(INSTANCE.topicName())
      .subscriptionPattern(INSTANCE.fullTopicName(TENANT_PATTERN))
      .build();

    KafkaConsumerWrapper<String, String> consumerWrapper = KafkaConsumerWrapper.<String, String>builder()
      .context(context)
      .vertx(vertx)
      .kafkaConfig(kafkaConfig)
      .loadLimit(loadLimit)
      .globalLoadSensor(new GlobalLoadSensor())
      .subscriptionDefinition(subscriptionDefinition)
      .build();

    return consumerWrapper
      .start(recordHandler, INSTANCE.moduleName() + SynchronizationVerticle.class.getName())
      .mapEmpty();
  }

  private KafkaConfig getKafkaConfig() {
    return KafkaConfig.builder()
      .envId(KafkaEnvironmentProperties.environment())
      .kafkaHost(KafkaEnvironmentProperties.host())
      .kafkaPort(KafkaEnvironmentProperties.port())
      .build();
  }

}
