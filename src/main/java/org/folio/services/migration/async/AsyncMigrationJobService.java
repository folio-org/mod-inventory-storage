package org.folio.services.migration.async;

import io.vertx.core.Context;
import io.vertx.core.Future;
import org.folio.persist.AsyncMigrationJobRepository;
import org.folio.rest.jaxrs.model.AsyncMigration;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;
import org.folio.rest.jaxrs.model.AsyncMigrationJobRequest;
import org.folio.rest.jaxrs.model.AsyncMigrations;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClientFuturized;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.IDS_PUBLISHED;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.ID_PUBLISHING_CANCELLED;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.ID_PUBLISHING_FAILED;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.PENDING_CANCEL;

public final class AsyncMigrationJobService {
  private static final List<AsyncMigrationJobRunner> migrationJobRunners = List.of(new PublicationPeriodMigrationJobRunner());
  private final static List<AsyncMigrationJob.JobStatus> ACCEPTABLE_STATUSES = List.of(AsyncMigrationJob.JobStatus.IN_PROGRESS, IDS_PUBLISHED);

  private final AsyncMigrationJobRepository migrationJobRepository;
  private final AsyncMigrationContext migrationContext;

  public AsyncMigrationJobService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.migrationJobRepository = new AsyncMigrationJobRepository(vertxContext, okapiHeaders);
    var postgresClient = new PostgresClientFuturized(PgUtil.postgresClient(vertxContext, okapiHeaders));
    this.migrationContext = new AsyncMigrationContext(vertxContext, okapiHeaders, postgresClient);
  }

  public Future<AsyncMigrationJob> submitAsyncMigration(AsyncMigrationJobRequest jobRequest) {
    var migrationJobResponse = buildInitialJob(jobRequest);
    if (isJobAvailable(jobRequest)) {
      return migrationJobRepository.save(migrationJobResponse.getId(), migrationJobResponse)
        .map(notUsed -> {
          migrationJobRunners.stream()
            .filter(v -> v.getMigrationName().equals(jobRequest.getName())).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("This migration with is not available."))
            .startAsyncMigration(migrationJobResponse, migrationContext);
          return migrationJobResponse;
        });
    } else {
      return Future.failedFuture(
        new IllegalArgumentException(format("Migration with name %s is not available.", jobRequest.getName())));
    }
  }

  public Future<AsyncMigrationJob> cancelAsyncMigration(String jobId) {
    return migrationJobRepository.fetchAndUpdate(jobId,
      resp -> resp.withJobStatus(AsyncMigrationJob.JobStatus.PENDING_CANCEL));
  }

  public static AsyncMigrations getAvailableMigrations() {
    AsyncMigrations migrations = new AsyncMigrations();
    migrationJobRunners.stream()
      .map(jobs -> new AsyncMigration()
        .withName(jobs.getMigrationName())
        .withAffectedEntities(jobs.getAffectedEntities()))
      .forEach(migration -> {
        migrations.getAsyncMigrations().add(migration);
        migrations.setTotalRecords(migrations.getTotalRecords() + 1);
      });
    return migrations;
  }

  private Optional<AsyncMigrationJobRunner> getMigrationJobRunnerByName(String name) {
    return migrationJobRunners.stream()
      .filter(runners -> runners.getMigrationName().equals(name))
      .findFirst();
  }

  private boolean isJobAvailable(AsyncMigrationJobRequest jobRequest) {
    return getAvailableMigrations().getAsyncMigrations().stream()
      .anyMatch(v -> v.getName().equals(jobRequest.getName()));
  }

  private AsyncMigrationJob buildInitialJob(AsyncMigrationJobRequest request) {
    var jobRunner = getMigrationJobRunnerByName(request.getName());
    var affectedEntities = new ArrayList<String>();
    jobRunner.ifPresent(asyncMigrationJobRunner -> affectedEntities.addAll(asyncMigrationJobRunner
      .getAffectedEntities()
      .stream().map(Enum::name)
      .collect(Collectors.toList())));
    return new AsyncMigrationJob()
      .withJobStatus(AsyncMigrationJob.JobStatus.IN_PROGRESS)
      .withPublished(0)
      .withName(request.getName())
      .withSubmittedDate(new Date())
      .withAffectedEntities(affectedEntities)
      .withId(randomUUID().toString());
  }

  public void logJobFail(String jobId) {
    migrationJobRepository.fetchAndUpdate(jobId,
      resp -> {
        var finalStatus = resp.getJobStatus() == PENDING_CANCEL
          ? ID_PUBLISHING_CANCELLED : ID_PUBLISHING_FAILED;
        return resp.withJobStatus(finalStatus).withFinishedDate(new Date());
      });
  }

  public void logPublishingCompleted(Long recordsPublished, String jobId) {
    migrationJobRepository
      .fetchAndUpdate(jobId, job -> job
        .withPublished(recordsPublished.intValue())
        .withJobStatus(IDS_PUBLISHED));
  }

  public Future<AsyncMigrationJob> logJobDetails(AsyncMigrationJob migrationJob, Long records) {
    if (!shouldLogJobDetails(records)) {
      return succeededFuture(migrationJob);
    }
    return migrationJobRepository
      .fetchAndUpdate(migrationJob.getId(), job -> job.withPublished(records.intValue()))
      .map(job -> {
        if (job.getJobStatus() == AsyncMigrationJob.JobStatus.PENDING_CANCEL) {
          throw new IllegalStateException("The job has been cancelled");
        }
        return job;
      });
  }

  public Future<AsyncMigrationJob> logJobProcessed(AsyncMigrationJob migrationJob, Integer records) {
    return migrationJobRepository
      .fetchAndUpdate(migrationJob.getId(), job -> {
        job.setProcessed(job.getProcessed() + records);
        if (ACCEPTABLE_STATUSES.contains(migrationJob.getJobStatus())) {
          job.setJobStatus(job.getProcessed() >= job.getPublished()
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

  private boolean shouldLogJobDetails(long records) {
    return records % 1000 == 0;
  }
}
