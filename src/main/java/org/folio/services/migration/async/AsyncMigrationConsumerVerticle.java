package org.folio.services.migration.async;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.services.kafka.topic.KafkaTopic;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.folio.Environment.environmentName;
import static org.folio.services.kafka.KafkaProperties.getKafkaConsumerProperties;

public class AsyncMigrationConsumerVerticle extends AbstractVerticle {
  private static final String TENANT_HEADER = "x-okapi-tenant";
  private final Logger log = LogManager.getLogger(getClass());

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    JsonObject config = vertx.getOrCreateContext().config();
    var tenantId = config.getString(TENANT_HEADER);
    var topicName = KafkaTopic.asyncMigration(tenantId, environmentName()).getTopicName();
    var headers = new HashMap<String, String>();
    headers.put(TENANT_HEADER, tenantId);

    KafkaConsumer<String, JsonObject> consumer = KafkaConsumer
      .<String, JsonObject>create(vertx, getKafkaConsumerProperties(tenantId,
        topicName + "_group"))
      .subscribe(topicName, startPromise);
    poll(consumer, headers);
  }

  private void poll(KafkaConsumer<String, JsonObject> consumer, HashMap<String, String> headers) {
    Promise<KafkaConsumerRecords<String, JsonObject>> pollPromise = Promise.promise();
    consumer.poll(Duration.ofMillis(100), pollPromise);

    var availableMigrations = Set.of(
      new PublicationPeriodMigrationService(vertx.getOrCreateContext(), headers));

    pollPromise.future()
      .compose(records -> {
        var migrationEvents = buildIdsForMigrations(records);
        var migrations = migrationEvents.entrySet().stream()
          .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
          .map(entry -> {
            var migrationName = entry.getKey();
            var ids = entry.getValue();
            var startedMigrations = availableMigrations.stream()
              .filter(javaMigration -> javaMigration.getMigrationName().equals(migrationName))
              .peek(javaMigration -> log.info(
                "Following migration is to be executed [migration={}] for ids [idsCount={}]", javaMigration, ids.size()))
              .map(javaMigration -> javaMigration.runMigrationForIds(ids))
              .collect(Collectors.toList());
            return CompositeFuture.all(new ArrayList<>(startedMigrations));
          }).collect(Collectors.toList());
        return CompositeFuture.all(new ArrayList<>(migrations));
      })
      .compose(composite -> {
        Promise<Void> commitPromise = Promise.promise();
        consumer.commit(commitPromise);
        return commitPromise.future();
      })
      .onComplete(any -> {
        if (any.failed()) {
          log.error("Error persisting and committing messages", any.cause());
        }
        poll(consumer, headers);
      });
  }

  private Map<String, Set<String>> buildIdsForMigrations(KafkaConsumerRecords<String, JsonObject> records) {
    Map<String, Set<String>> result = new HashMap<>();
    records.records().iterator().forEachRemaining(record -> {
      String id = record.key();
      String migrationName = getMigrationNameFromMessage(record);
      result.computeIfAbsent(migrationName, k -> {
        var set = new HashSet<String>();
        set.add(id);
        return set;
      });
      result.computeIfPresent(migrationName, (k, v) -> {
        v.add(id);
        return v;
      });
    });
    return result;
  }

  private static String getMigrationNameFromMessage(ConsumerRecord<String, JsonObject> message) {
    final JsonObject payload = message.value();
    final var oldOrNew = payload.containsKey("new")
      ? payload.getJsonObject("new") : payload.getJsonObject("old");
    return oldOrNew != null ? oldOrNew.getString("name") : null;
  }

}
