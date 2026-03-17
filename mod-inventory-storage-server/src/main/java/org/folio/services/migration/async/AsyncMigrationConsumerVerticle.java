package org.folio.services.migration.async;

import static org.folio.InventoryKafkaTopic.ASYNC_MIGRATION;
import static org.folio.services.migration.async.AsyncMigrationsConsumerUtils.pollAsyncMigrationsMessages;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.serialization.JsonObjectDeserializer;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.services.KafkaEnvironmentProperties;

public class AsyncMigrationConsumerVerticle extends AbstractVerticle {
  private static final Logger log = LogManager.getLogger(AsyncMigrationConsumerVerticle.class);
  private static final String TENANT_FOR_MIGRATION = "\\w{1,}";
  private static final Long PERIOD = 1000L;

  public static Map<String, String> getKafkaConsumerProperties(String groupId) {
    var kafkaConfig = KafkaConfig.builder()
      .envId(KafkaEnvironmentProperties.environment())
      .kafkaHost(KafkaEnvironmentProperties.host())
      .kafkaPort(KafkaEnvironmentProperties.port())
      .consumerValueDeserializerClass(JsonObjectDeserializer.class.getName())
      .build();
    var consumerProps = kafkaConfig.getConsumerProps();
    consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    consumerProps.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, "15000");
    consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    return consumerProps;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    log.info("start:: Starting AsyncMigrationConsumerVerticle");
    var topicName = ASYNC_MIGRATION.fullTopicName(TENANT_FOR_MIGRATION);
    KafkaConsumer<String, JsonObject> consumer = KafkaConsumer
      .create(vertx, getKafkaConsumerProperties(AsyncMigrationConsumerVerticle.class.getSimpleName() + "_group"));

    consumer.subscribe(Pattern.compile(topicName))
      .onSuccess(event -> {
        log.info("Consumer created. SubscriptionPattern: {}", topicName);
        startPromise.complete();
      })
      .onFailure(startPromise::fail);

    vertx.setPeriodic(PERIOD, v ->
      consumer.poll(Duration.ofMillis(100))
        .onSuccess(pollAsyncMigrationsMessages(consumer, vertx.getOrCreateContext())));
  }
}
