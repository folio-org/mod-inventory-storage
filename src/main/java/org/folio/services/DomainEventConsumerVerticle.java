package org.folio.services;

import static org.folio.InventoryKafkaTopic.INSTANCE;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import org.folio.InventoryKafkaTopic;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.GlobalLoadSensor;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaConsumerWrapper;
import org.folio.kafka.SubscriptionDefinition;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.services.caches.ConsortiumDataCache;

public class DomainEventConsumerVerticle extends AbstractVerticle {

  private static final String TENANT_PATTERN = "\\w{1,}";
  private static final int LOAD_LIMIT = 5;

  private final ConsortiumDataCache consortiumDataCache;

  public DomainEventConsumerVerticle(ConsortiumDataCache consortiumDataCache) {
    this.consortiumDataCache = consortiumDataCache;
  }

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    HttpClient httpClient = vertx.createHttpClient();
    DomainEventKafkaRecordHandler domainEventKafkaRecordHandler =
      new DomainEventKafkaRecordHandler(consortiumDataCache, httpClient, vertx);

    createKafkaConsumerWrapper(INSTANCE, domainEventKafkaRecordHandler)
      .onFailure(startPromise::fail)
      .onSuccess(v -> startPromise.complete());
  }

  private Future<KafkaConsumerWrapper<String, String>> createKafkaConsumerWrapper(InventoryKafkaTopic topic,
                                                                                  AsyncRecordHandler<String, String> recordHandler) {
    KafkaConfig kafkaConfig = getKafkaConfig();
    SubscriptionDefinition subscriptionDefinition = SubscriptionDefinition.builder()
      .eventType(topic.topicName())
      .subscriptionPattern(topic.fullTopicName(TENANT_PATTERN))
      .build();

    KafkaConsumerWrapper<String, String> consumerWrapper = KafkaConsumerWrapper.<String, String>builder()
      .context(context)
      .vertx(vertx)
      .kafkaConfig(kafkaConfig)
      .loadLimit(LOAD_LIMIT)
      .globalLoadSensor(new GlobalLoadSensor())
      .subscriptionDefinition(subscriptionDefinition)
      .build();

    return consumerWrapper.start(recordHandler, topic.moduleName() + DomainEventConsumerVerticle.class.getName())
      .map(consumerWrapper);
  }

  private KafkaConfig getKafkaConfig() {
    return KafkaConfig.builder()
      .envId(KafkaEnvironmentProperties.environment())
      .kafkaHost(KafkaEnvironmentProperties.host())
      .kafkaPort(KafkaEnvironmentProperties.port())
      .build();
  }

}