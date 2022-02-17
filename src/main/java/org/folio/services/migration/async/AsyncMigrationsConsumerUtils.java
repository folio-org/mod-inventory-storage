package org.folio.services.migration.async;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AsyncMigrationsConsumerUtils {

  private static final String TENANT_HEADER = "x-okapi-tenant";
  private static final Logger log = LogManager.getLogger(AsyncMigrationsConsumerUtils.class);

  public Handler<KafkaConsumerRecords<String, JsonObject>> pollAsyncMigrationsMessages(KafkaConsumer<String, JsonObject> consumer,
                                                                                              Context vertxContext) {
    return records -> {
      var eventsByTenant = buildTenantRecords(records);

      eventsByTenant.entrySet().parallelStream().forEach(v -> {
        var tenantId = v.getKey();
        var headers = new HashMap<String, String>();
        headers.put(TENANT_HEADER, tenantId);

        var availableMigrations = Set.of(
          new PublicationPeriodMigrationService(vertxContext, headers));
        var jobService = new AsyncMigrationJobService(vertxContext, headers);

        var migrationEvents = buildIdsForMigrations(v.getValue());
        var migrations = migrationEvents.entrySet().stream()
          .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
          .map(entry -> {
            var migrationJob = entry.getKey();
            var ids = entry.getValue();
            var startedMigrations = availableMigrations.stream()
              .filter(javaMigration -> shouldProcessIdsForJob(javaMigration, migrationJob))
              .map(javaMigration -> javaMigration.runMigrationForIds(ids)
                .onSuccess(notUsed -> jobService.logJobProcessed(migrationJob, ids.size()))
                .onFailure(notUsed -> jobService.logJobFail(migrationJob.getId())))
              .collect(Collectors.toList());
            return CompositeFuture.all(new ArrayList<>(startedMigrations));
          }).collect(Collectors.toList());

        CompositeFuture.all(new ArrayList<>(migrations))
          .onSuccess(composite -> consumer.commit())
          .onFailure(any -> log.error("Error persisting and committing messages", any));
      });
    };
  }

  private boolean shouldProcessIdsForJob(PublicationPeriodMigrationService javaMigration,
                                                AsyncMigrationJob migrationJob) {
    return javaMigration.getMigrationName().equals(migrationJob.getName())
      && (migrationJob.getJobStatus().equals(AsyncMigrationJob.JobStatus.IN_PROGRESS)
      || migrationJob.getJobStatus().equals(AsyncMigrationJob.JobStatus.IDS_PUBLISHED));
  }

  private Map<AsyncMigrationJob, Set<String>> buildIdsForMigrations(Set<ConsumerRecord<String, JsonObject>> records) {
    Map<AsyncMigrationJob, Set<String>> result = new HashMap<>();
    records.forEach(record -> {
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

  private Map<String, Set<ConsumerRecord<String, JsonObject>>> buildTenantRecords(KafkaConsumerRecords<String, JsonObject> records) {
    var result = new HashMap<String, Set<ConsumerRecord<String, JsonObject>>>();
    records.records().iterator().forEachRemaining(record -> {
      var iterator = record.headers().headers(TENANT_HEADER).iterator();
      if (iterator.hasNext()) {
        String tenantId = new String(iterator.next().value());
        result.computeIfAbsent(tenantId, k -> {
          var set = new HashSet<ConsumerRecord<String, JsonObject>>();
          set.add(record);
          return set;
        });
        result.computeIfPresent(tenantId, (k, v) -> {
          v.add(record);
          return v;
        });
      }
    });
    return result;
  }

  private AsyncMigrationJob getMigrationJobFromMessage(ConsumerRecord<String, JsonObject> message) {
    final JsonObject payload = message.value();
    final var oldOrNew = payload.containsKey("new")
      ? payload.getJsonObject("new") : payload.getJsonObject("old");
    return oldOrNew != null ? oldOrNew.mapTo(AsyncMigrationJob.class) : null;
  }
}
