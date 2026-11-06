package org.folio.services.consortium;

import static org.folio.InventoryKafkaTopic.INSTANCE;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.GlobalLoadSensor;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaConsumerWrapper;
import org.folio.kafka.SubscriptionDefinition;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.persist.InstanceRepository;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.utils.Environment;

public class ShadowInstanceSynchronizationVerticle extends AbstractVerticle {

  private static final Logger log = LogManager.getLogger(ShadowInstanceSynchronizationVerticle.class);

  private static final String LOAD_LIMIT_PARAM = "consumer.instance-synchronization.load-limit";
  private static final int DEFAULT_LOAD_LIMIT = 5;
  private static final String TENANT_PATTERN = "\\w{1,}";

  private final ConsortiumDataCache consortiumDataCache;
  private final InstanceRepository instanceRepository;
  private final HttpClient httpClient;

  public ShadowInstanceSynchronizationVerticle(ConsortiumDataCache consortiumDataCache, HttpClient httpClient,
                                               InstanceRepository instanceRepository) {
    this.instanceRepository = instanceRepository;
    this.consortiumDataCache = consortiumDataCache;
    this.httpClient = httpClient;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    log.info("start:: Starting ShadowInstanceSynchronizationVerticle");
    ShadowInstanceSynchronizationHandler handler =
      new ShadowInstanceSynchronizationHandler(consortiumDataCache, httpClient, instanceRepository);

    createKafkaConsumerWrapper(handler).onComplete(startPromise);
  }

  private Future<Void> createKafkaConsumerWrapper(
    AsyncRecordHandler<String, String> recordHandler) {

    int loadLimit = Environment.getIntValue(LOAD_LIMIT_PARAM, DEFAULT_LOAD_LIMIT);
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
      .start(recordHandler, INSTANCE.moduleName() + ShadowInstanceSynchronizationVerticle.class.getName())
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
