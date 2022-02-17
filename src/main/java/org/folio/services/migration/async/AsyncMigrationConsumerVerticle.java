package org.folio.services.migration.async;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;
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
  private static final Logger log = LogManager.getLogger(AsyncMigrationConsumerVerticle.class);

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
    vertx.setPeriodic(1000L, v ->
      pollAsyncMigrationsMessages(consumer, headers, vertx.getOrCreateContext()));
  }

  private void pollAsyncMigrationsMessages(KafkaConsumer<String, JsonObject> consumer,
                                           HashMap<String, String> headers, Context vertxContext) {

    Promise<KafkaConsumerRecords<String, JsonObject>> pollPromise = Promise.promise();
    consumer.poll(Duration.ofMillis(100), pollPromise);

    var availableMigrations = Set.of(
      new PublicationPeriodMigrationService(vertxContext, headers));
    var jobService = new AsyncMigrationJobService(vertxContext, headers);

    pollPromise.future()
      .compose(records -> {
        var migrationEvents = buildIdsForMigrations(records);
        var migrations = migrationEvents.entrySet().stream()
          .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
          .map(entry -> {
            var migrationJob = entry.getKey();
            var ids = entry.getValue();
            var startedMigrations = availableMigrations.stream()
              .filter(javaMigration -> shouldProcessIdsForJob(javaMigration, migrationJob))
              .peek(javaMigration -> log.info(
                "Following migration is to be executed [migration={}] for ids [idsCount={}]", javaMigration, ids.size()))
              .map(javaMigration -> javaMigration.runMigrationForIds(ids)
                .onSuccess(notUsed -> jobService.logJobProcessed(migrationJob, ids.size()))
                .onFailure(notUsed -> jobService.logJobFail(migrationJob.getId())))
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
      .onFailure(any -> log.error("Error persisting and committing messages", any));
  }

  private boolean shouldProcessIdsForJob(PublicationPeriodMigrationService javaMigration,
                                         AsyncMigrationJob migrationJob) {
    return javaMigration.getMigrationName().equals(migrationJob.getName())
      && (migrationJob.getJobStatus().equals(AsyncMigrationJob.JobStatus.IN_PROGRESS)
      || migrationJob.getJobStatus().equals(AsyncMigrationJob.JobStatus.IDS_PUBLISHED));
  }

  private Map<AsyncMigrationJob, Set<String>> buildIdsForMigrations(KafkaConsumerRecords<String, JsonObject> records) {
    Map<AsyncMigrationJob, Set<String>> result = new HashMap<>();
    records.records().iterator().forEachRemaining(record -> {
      String id = record.key();
      AsyncMigrationJob migrationName = getMigrationJobFromMessage(record);
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

  private static AsyncMigrationJob getMigrationJobFromMessage(ConsumerRecord<String, JsonObject> message) {
    final JsonObject payload = message.value();
    final var oldOrNew = payload.containsKey("new")
      ? payload.getJsonObject("new") : payload.getJsonObject("old");
    return oldOrNew != null ? oldOrNew.mapTo(AsyncMigrationJob.class) : null;
  }
}
