package org.folio.services.migration.async;

import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.CANCELLED;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.IDS_PUBLISHED;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.ID_PUBLISHING_FAILED;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.PENDING_CANCEL;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.persist.AsyncMigrationJobRepository;
import org.folio.rest.jaxrs.model.AsyncMigration;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;
import org.folio.rest.jaxrs.model.AsyncMigrationJobCollection;
import org.folio.rest.jaxrs.model.AsyncMigrationJobRequest;
import org.folio.rest.jaxrs.model.AsyncMigrations;
import org.folio.rest.jaxrs.model.Processed;
import org.folio.rest.jaxrs.model.Published;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClientFuturized;

public final class AsyncMigrationJobService {
  private static final List<AsyncMigrationJobRunner> MIGRATION_JOB_RUNNERS = List
    .of(new ShelvingOrderMigrationJobRunner(),
      new SubjectSeriesMigrationJobRunner());
  private static final List<AsyncMigrationJob.JobStatus> ACCEPTABLE_STATUSES = List
    .of(AsyncMigrationJob.JobStatus.IN_PROGRESS, IDS_PUBLISHED);

  private final AsyncMigrationJobRepository migrationJobRepository;
  private final AsyncMigrationContext migrationContext;

  public AsyncMigrationJobService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.migrationJobRepository = new AsyncMigrationJobRepository(vertxContext, okapiHeaders);
    var postgresClient = new PostgresClientFuturized(PgUtil.postgresClient(vertxContext, okapiHeaders));
    this.migrationContext = new AsyncMigrationContext(vertxContext, okapiHeaders, postgresClient);
  }

  public static AsyncMigrations getAvailableMigrations() {
    AsyncMigrations migrations = new AsyncMigrations();
    MIGRATION_JOB_RUNNERS.stream()
      .map(jobs -> new AsyncMigration()
        .withMigrations(Collections.singletonList(jobs.getMigrationName()))
        .withAffectedEntities(jobs.getAffectedEntities()))
      .forEach(migration -> {
        migrations.getAsyncMigrations().add(migration);
        migrations.setTotalRecords(migrations.getTotalRecords() + 1);
      });
    return migrations;
  }

  public Future<AsyncMigrationJobCollection> getAllAsyncJobs() {
    return migrationJobRepository.get(new Criterion())
      .map(list -> new AsyncMigrationJobCollection()
        .withJobs(list)
        .withTotalRecords(list.size()));
  }

  public Future<AsyncMigrationJob> submitAsyncMigration(AsyncMigrationJobRequest jobRequest) {
    var migrationJobResponse = buildInitialJob(jobRequest);
    if (isJobAvailable(jobRequest)) {
      return migrationJobRepository.save(migrationJobResponse.getId(), migrationJobResponse)
        .map(notUsed -> {
          MIGRATION_JOB_RUNNERS.stream()
            .filter(v -> jobRequest.getMigrations().contains(v.getMigrationName()))
            .forEach(v -> v.startAsyncMigration(migrationJobResponse,
              new AsyncMigrationContext(migrationContext, v.getMigrationName())));
          return migrationJobResponse;
        });
    } else {
      return Future.failedFuture(
        new IllegalArgumentException(format("One or more migrations are not available. Migrations: %s",
          String.join(" ", jobRequest.getMigrations()))));
    }
  }

  public Future<AsyncMigrationJob> cancelAsyncMigration(String jobId) {
    return migrationJobRepository.fetchAndUpdate(jobId,
      resp -> resp.withJobStatus(AsyncMigrationJob.JobStatus.PENDING_CANCEL));
  }

  public void logJobFail(String jobId) {
    migrationJobRepository.fetchAndUpdate(jobId,
      resp -> {
        var finalStatus = resp.getJobStatus() == PENDING_CANCEL
                          ? CANCELLED : ID_PUBLISHING_FAILED;
        return resp.withJobStatus(finalStatus).withFinishedDate(new Date());
      });
  }

  public void logPublishingCompleted(String migrationName, Long recordsPublished, String jobId) {
    migrationJobRepository
      .fetchAndUpdate(jobId, job -> {
        job.getPublished().stream()
          .filter(p -> migrationName.equals(p.getMigrationName()))
          .findFirst().ifPresentOrElse(p -> p.setCount(recordsPublished.intValue()),
            () -> job.getPublished().add(new Published()
              .withMigrationName(migrationName)
              .withCount(recordsPublished.intValue())));
        job.withJobStatus(IDS_PUBLISHED);
        return job;
      });
  }

  public Future<AsyncMigrationJob> logJobDetails(String migrationName, AsyncMigrationJob migrationJob, Long records) {
    if (!shouldLogJobDetails(records)) {
      return succeededFuture(migrationJob);
    }
    return migrationJobRepository
      .fetchAndUpdate(migrationJob.getId(), job -> {
        job.getPublished().stream()
          .filter(p -> migrationName.equals(p.getMigrationName()))
          .findFirst().ifPresentOrElse(p -> p.setCount(records.intValue()),
            () -> job.getPublished().add(new Published()
              .withMigrationName(migrationName)
              .withCount(records.intValue())));
        return job;
      })
      .map(job -> {
        if (job.getJobStatus() == AsyncMigrationJob.JobStatus.PENDING_CANCEL) {
          throw new IllegalStateException("The job has been cancelled");
        }
        return job;
      });
  }

  public Future<AsyncMigrationJob> logJobProcessed(String migrationName, String jobId, Integer records) {
    return migrationJobRepository
      .fetchAndUpdate(jobId, job -> {
        job.getProcessed().stream()
          .filter(p -> p.getMigrationName().equals(migrationName))
          .findFirst()
          .ifPresentOrElse(v -> v.setCount(v.getCount() + records),
            () -> job.getProcessed().add(new Processed()
              .withCount(records)
              .withMigrationName(migrationName)));
        if (ACCEPTABLE_STATUSES.contains(job.getJobStatus())) {
          var totalPublished = job.getPublished()
            .stream().map(Published::getCount)
            .mapToInt(Integer::intValue).sum();
          var totalProcessed = job.getProcessed()
            .stream().map(Processed::getCount)
            .mapToInt(Integer::intValue).sum();

          job.setJobStatus(totalProcessed >= totalPublished
                           ? AsyncMigrationJob.JobStatus.COMPLETED
                           : AsyncMigrationJob.JobStatus.IN_PROGRESS);
        }
        if (job.getJobStatus().equals(AsyncMigrationJob.JobStatus.COMPLETED)) {
          job.setFinishedDate(new Date());
        }
        return job;
      })
      .map(job -> {
        if (job.getJobStatus() == AsyncMigrationJob.JobStatus.PENDING_CANCEL) {
          throw new IllegalStateException("The job has been cancelled");
        }
        return job;
      });
  }

  private List<AsyncMigrationJobRunner> getMigrationJobRunnersByName(List<String> migrationNames) {
    return MIGRATION_JOB_RUNNERS.stream()
      .filter(runners -> migrationNames.contains(runners.getMigrationName()))
      .toList();
  }

  private boolean isJobAvailable(AsyncMigrationJobRequest jobRequest) {
    var availableMigrations = getAvailableMigrations().getAsyncMigrations()
      .stream().flatMap(v -> Stream.of(v.getMigrations())).toList()
      .stream().flatMap(List::stream).toList();
    return availableMigrations.containsAll(jobRequest.getMigrations());
  }

  private AsyncMigrationJob buildInitialJob(AsyncMigrationJobRequest request) {
    var jobRunners = getMigrationJobRunnersByName(request.getMigrations());
    var affectedEntities = new HashSet<String>();
    jobRunners.forEach(asyncMigrationJobRunner -> affectedEntities.addAll(asyncMigrationJobRunner
      .getAffectedEntities()
      .stream().map(Enum::name)
      .toList()));
    return new AsyncMigrationJob()
      .withJobStatus(AsyncMigrationJob.JobStatus.IN_PROGRESS)
      .withMigrations(request.getMigrations())
      .withSubmittedDate(new Date())
      .withAffectedEntities(new ArrayList<>(affectedEntities))
      .withId(randomUUID().toString());
  }

  private boolean shouldLogJobDetails(long records) {
    return records % 1000 == 0;
  }
}
