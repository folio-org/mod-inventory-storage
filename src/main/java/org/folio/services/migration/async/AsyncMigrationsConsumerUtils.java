package org.folio.services.migration.async;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecords;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;
import org.apache.commons.collections4.map.CaseInsensitiveMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.folio.services.migration.async.AbstractAsyncMigrationJobRunner.ASYNC_MIGRATION_JOB_NAME;

public class AsyncMigrationsConsumerUtils {

  private static final String TENANT_HEADER = "x-okapi-tenant";
  private static final Logger log = LogManager.getLogger(AsyncMigrationsConsumerUtils.class);

  public static Handler<KafkaConsumerRecords<String, JsonObject>> pollAsyncMigrationsMessages(KafkaConsumer<String, JsonObject> consumer,
                                                                                              Context vertxContext) {
    return records -> {
      var eventsByTenant = buildTenantRecords(records);

      eventsByTenant.entrySet().parallelStream().forEach(v -> {
        var tenantId = v.getKey();
        var headers = new CaseInsensitiveMap<String, String>();
        headers.put(TENANT_HEADER, tenantId);

        var availableMigrations = Set.of(
          new PublicationPeriodMigrationService(vertxContext, headers),
          new ShelvingOrderAsyncMigrationService(vertxContext, headers));
        var jobService = new AsyncMigrationJobService(vertxContext, headers);

        var migrationEvents = buildIdsForMigrations(v.getValue());
        var migrations = migrationEvents.entrySet().stream()
          .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
          .map(entry -> {
            var migrationJob = entry.getKey().getJob();
            var migrationName = entry.getKey().getMigrationName();
            var ids = entry.getValue();
            var startedMigrations = availableMigrations.stream()
              .filter(javaMigration -> shouldProcessIdsForJob(javaMigration, migrationJob, migrationName))
              .map(javaMigration -> javaMigration.runMigrationForIds(ids)
                .onSuccess(notUsed -> jobService.logJobProcessed(migrationName, migrationJob.getId(), ids.size()))
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

  private static boolean shouldProcessIdsForJob(AsyncBaseMigrationService javaMigration,
                                                AsyncMigrationJob migrationJob,
                                                String migrationName) {
    return migrationName.equals(javaMigration.getMigrationName())
      && (migrationJob.getJobStatus().equals(AsyncMigrationJob.JobStatus.IN_PROGRESS)
      || migrationJob.getJobStatus().equals(AsyncMigrationJob.JobStatus.IDS_PUBLISHED));
  }

  private static Map<MigrationContext, Set<String>> buildIdsForMigrations(Set<ConsumerRecord<String, JsonObject>> records) {
    Map<MigrationContext, Set<String>> result = new HashMap<>();
    records.forEach(record -> {

      var headerIterator = record
        .headers().headers(ASYNC_MIGRATION_JOB_NAME)
        .iterator();

      if (headerIterator.hasNext()) {

        String id = record.key();
        var migrationContext = new MigrationContext(
          new String(headerIterator.next().value()),
          getMigrationJobFromMessage(record));

        result.computeIfAbsent(migrationContext, k -> {
          var set = new HashSet<String>();
          set.add(id);
          return set;
        });
        result.computeIfPresent(migrationContext, (k, v) -> {
          v.add(id);
          return v;
        });
      }
    });
    return result;
  }

  private static Map<String, Set<ConsumerRecord<String, JsonObject>>> buildTenantRecords(KafkaConsumerRecords<String, JsonObject> records) {
    var result = new HashMap<String, Set<ConsumerRecord<String, JsonObject>>>();
    records.records().iterator().forEachRemaining(record -> {
      var iterator = record
        .headers().headers(TENANT_HEADER)
        .iterator();
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

  private static AsyncMigrationJob getMigrationJobFromMessage(ConsumerRecord<String, JsonObject> message) {
    final JsonObject payload = message.value();
    final var oldOrNew = payload.containsKey("new")
      ? payload.getJsonObject("new") : payload.getJsonObject("old");
    return oldOrNew != null ? oldOrNew.mapTo(AsyncMigrationJob.class) : null;
  }

  private static class MigrationContext {
    private final String migrationName;
    private final AsyncMigrationJob job;

    public MigrationContext(String migrationName, AsyncMigrationJob job) {
      this.migrationName = migrationName;
      this.job = job;
    }

    public String getMigrationName() {
      return migrationName;
    }

    public AsyncMigrationJob getJob() {
      return job;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;

      if (!(o instanceof MigrationContext)) return false;

      MigrationContext that = (MigrationContext) o;

      return new EqualsBuilder().append(getMigrationName(), that.getMigrationName())
        .append(getJob(), that.getJob()).isEquals();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder(17, 37)
        .append(getMigrationName()).append(getJob()).toHashCode();
    }
  }
}
