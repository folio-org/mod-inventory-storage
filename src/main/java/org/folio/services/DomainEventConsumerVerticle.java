package org.folio.services;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.serialization.JsonObjectDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.InventoryKafkaTopic;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.GlobalLoadSensor;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaConsumerWrapper;
import org.folio.kafka.SimpleConfigurationReader;
import org.folio.kafka.SubscriptionDefinition;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.services.migration.async.AsyncMigrationConsumerVerticle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.folio.InventoryKafkaTopic.INSTANCE;

public class DomainEventConsumerVerticle extends AbstractVerticle {

  private static final Logger LOG = LogManager.getLogger(DomainEventConsumerVerticle.class);
  private static final String TENANT_PATTERN = "\\w{1,}";
  private static final int LOAD_LIMIT = 5;
  public static final String MODULE_NAME = "inventory";

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    var topicName = INSTANCE.fullTopicName(TENANT_PATTERN);
    String groupId = DomainEventConsumerVerticle.class.getSimpleName() + "_group";

    createKafkaConsumerWrapper(INSTANCE, null)
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
