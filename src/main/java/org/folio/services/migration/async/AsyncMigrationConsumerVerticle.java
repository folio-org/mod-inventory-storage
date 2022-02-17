package org.folio.services.migration.async;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.services.kafka.topic.KafkaTopic;

import java.time.Duration;
import java.util.regex.Pattern;

import static org.folio.Environment.environmentName;
import static org.folio.services.kafka.KafkaProperties.getKafkaConsumerProperties;

public class AsyncMigrationConsumerVerticle extends AbstractVerticle {
  private static final Logger log = LogManager.getLogger(AsyncMigrationConsumerVerticle.class);
  private static final Long PERIOD = 1000L;
  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    var topicName = KafkaTopic.asyncMigration(environmentName()).getTopicName();

    KafkaConsumer<String, JsonObject> consumer = KafkaConsumer
      .<String, JsonObject>create(vertx, getKafkaConsumerProperties(AsyncMigrationConsumerVerticle.class.getSimpleName() + "_group"))
      .subscribe(Pattern.compile(topicName), ar -> {
        if (ar.succeeded()) {
          log.info("Consumer created. SubscriptionPattern: " + topicName);
          startPromise.complete();
        } else {
          startPromise.fail(ar.cause());
        }
      }).pollTimeout(Duration.ofMillis(PERIOD));

    vertx.setPeriodic(PERIOD, v ->
      consumer.poll(Duration.ofMillis(100))
        .onSuccess(new AsyncMigrationsConsumerUtils().pollAsyncMigrationsMessages(consumer, vertx.getOrCreateContext())));
  }
}
