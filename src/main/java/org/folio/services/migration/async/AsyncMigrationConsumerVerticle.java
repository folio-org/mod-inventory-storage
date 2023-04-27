package org.folio.services.migration.async;

import static org.folio.InventoryKafkaTopic.ASYNC_MIGRATION;
import static org.folio.services.migration.async.AsyncMigrationsConsumerUtils.pollAsyncMigrationsMessages;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.serialization.JsonObjectDeserializer;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.SimpleConfigurationReader;
import org.folio.kafka.services.KafkaEnvironmentProperties;

public class AsyncMigrationConsumerVerticle extends AbstractVerticle {
  private static final Logger log = LogManager.getLogger(AsyncMigrationConsumerVerticle.class);
  private static final String TENANT_FOR_MIGRATION = "\\w{1,}";
  private static final Long PERIOD = 1000L;

  public static Map<String, String> getKafkaConsumerProperties(String groupId) {
    Map<String, String> config = new HashMap<>();
    config.put("bootstrap.servers", KafkaEnvironmentProperties.host() + ":" + KafkaEnvironmentProperties.port());
    config.put("key.deserializer", StringDeserializer.class.getName());
    config.put("max.poll.records", SimpleConfigurationReader.getValue(
      List.of("kafka.consumer.max.poll.records", "spring.kafka.consumer.max-poll-records"), "100"));
    config.put("value.deserializer", JsonObjectDeserializer.class.getName());
    config.put("group.id", groupId);
    config.put("metadata.max.age.ms", "15000");
    config.put("auto.offset.reset", "earliest");
    config.put("enable.auto.commit", "false");
    return config;
  }

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    var topicName = ASYNC_MIGRATION.fullTopicName(TENANT_FOR_MIGRATION);

    KafkaConsumer<String, JsonObject> consumer = KafkaConsumer
      .<String, JsonObject>create(vertx,
        getKafkaConsumerProperties(AsyncMigrationConsumerVerticle.class.getSimpleName() + "_group"))
      .subscribe(Pattern.compile(topicName), ar -> {
        if (ar.succeeded()) {
          log.info("Consumer created. SubscriptionPattern: {}", topicName);
          startPromise.complete();
        } else {
          startPromise.fail(ar.cause());
        }
      }).pollTimeout(Duration.ofMillis(PERIOD));

    vertx.setPeriodic(PERIOD, v ->
      consumer.poll(Duration.ofMillis(100))
        .onSuccess(pollAsyncMigrationsMessages(consumer, vertx.getOrCreateContext())));
  }
}
