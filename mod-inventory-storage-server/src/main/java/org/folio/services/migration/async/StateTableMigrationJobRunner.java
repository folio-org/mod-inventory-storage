package org.folio.services.migration.async;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.AsyncMigrationJobRepository;
import org.folio.rest.jaxrs.model.AffectedEntity;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;
import org.folio.rest.jaxrs.model.AsyncMigrationJobCounts;
import org.folio.rest.jaxrs.model.AsyncMigrationJobRequest;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;

/**
 * Generic background migration that populates a join table from a jsonb array of reference ids in chunks.
 * The progress is kept in the background_migration_state table, so the migration is resumable.
 * Everything table-specific is taken from {@link StateTableMigrationConfig}.
 */
public class StateTableMigrationJobRunner implements AsyncMigrationJobRunner {

  private static final String STARTED = "STARTED";
  private static final String COMPLETED = "COMPLETED";
  private static final String FAILED = "FAILED";
  private static final String CANCELLED = "CANCELLED";

  private static final Logger log = LogManager.getLogger(StateTableMigrationJobRunner.class);
  private static final String STATE_TABLE = "background_migration_state";
  private static final UUID NIL_UUID = new UUID(0L, 0L);
  private static final int LOG_EVERY_N_CHUNKS = 10;
  private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

  private static final String INSERT_STATE_SQL = "INSERT INTO %s.%s (name, status, job_id, started_at, updated_at) "
    + "VALUES ($1, 'STARTED', $2, now(), now()) ON CONFLICT (name) DO NOTHING";
  private static final String SELECT_STATE_SQL = "SELECT status, job_id, processed_count, finished_at "
    + "FROM %s.%s WHERE name = $1";
  private static final String RESUME_STATE_SQL = "UPDATE %s.%s SET status = 'STARTED', job_id = $2, "
    + "finished_at = NULL, error_message = NULL, updated_at = now() "
    + "WHERE name = $1 AND job_id IS NOT DISTINCT FROM $3::uuid RETURNING last_processed_id, processed_count";
  private static final String RESTART_STATE_SQL = "UPDATE %s.%s SET status = 'STARTED', job_id = $2, "
    + "last_processed_id = NULL, processed_count = 0, inserted_count = 0, skipped_count = 0, "
    + "started_at = now(), finished_at = NULL, error_message = NULL, updated_at = now() "
    + "WHERE name = $1 AND job_id IS NOT DISTINCT FROM $3::uuid RETURNING last_processed_id, processed_count";
  private static final String PROGRESS_STATE_SQL = "UPDATE %s.%s SET last_processed_id = $3, "
    + "processed_count = processed_count + $4, inserted_count = inserted_count + $5, "
    + "skipped_count = skipped_count + $6, status = $7::text, "
    + "finished_at = CASE WHEN $7::text = 'COMPLETED' THEN now() ELSE NULL END, updated_at = now() "
    + "WHERE name = $1 AND job_id = $2 RETURNING processed_count, inserted_count, skipped_count";
  private static final String FINISH_STATE_SQL = "UPDATE %s.%s SET status = $3, error_message = $4, "
    + "finished_at = now(), updated_at = now() "
    + "WHERE name = $1 AND job_id = $2 AND status <> 'COMPLETED'";
  // The non-empty predicate deliberately does not match the expression index on the jsonb field, so the planner
  // uses the primary key index scan with LIMIT instead of building a bitmap on that index for every chunk.
  // skipped_count = invalid + duplicate + already existing ids (code_count minus rows actually inserted).
  private static final String CHUNK_SQL = "WITH chunk AS ("
    + " SELECT id, jsonb FROM %1$s.%2$s"
    + " WHERE id %8$s $1 AND jsonb->'%3$s' IS NOT NULL AND jsonb->'%3$s' <> '[]'::jsonb"
    + " ORDER BY id LIMIT $2 FOR SHARE),"
    + " codes AS ("
    + " SELECT chunk.id AS owner_id, lower(code) AS code FROM chunk"
    + " CROSS JOIN LATERAL jsonb_array_elements_text("
    + " CASE WHEN jsonb_typeof(chunk.jsonb->'%3$s') = 'array' THEN chunk.jsonb->'%3$s' ELSE '[]'::jsonb END"
    + " ) AS code),"
    + " ins AS ("
    + " INSERT INTO %1$s.%4$s (%5$s, %6$s)"
    + " SELECT DISTINCT codes.owner_id, ref.id FROM codes JOIN %1$s.%7$s ref ON ref.id::text = codes.code"
    + " ON CONFLICT DO NOTHING RETURNING 1)"
    + " SELECT (SELECT count(*) FROM chunk) AS chunk_count,"
    + " (SELECT id FROM chunk ORDER BY id DESC LIMIT 1) AS last_id,"
    + " (SELECT count(*) FROM codes) AS code_count,"
    + " (SELECT count(*) FROM ins) AS inserted_count";

  private final StateTableMigrationConfig config;
  private final BackgroundMigrationSettings settings;

  public StateTableMigrationJobRunner(StateTableMigrationConfig config) {
    this(config, BackgroundMigrationSettings.fromEnvironment());
  }

  public StateTableMigrationJobRunner(StateTableMigrationConfig config, BackgroundMigrationSettings settings) {
    this.config = config;
    this.settings = settings;
  }

  @Override
  public String getMigrationName() {
    return config.migrationName();
  }

  @Override
  public List<AffectedEntity> getAffectedEntities() {
    return List.of(config.affectedEntity());
  }

  /**
   * State-table migrations are never started through the generic path, see {@link #submit}.
   */
  @Override
  public void startAsyncMigration(AsyncMigrationJob migrationJob, AsyncMigrationContext context) {
    throw new UnsupportedOperationException("State-table migrations are started via submit()");
  }

  /**
   * Handles POST: decides by the state table whether a new run is started and returns the job.
   */
  public Future<AsyncMigrationJob> submit(AsyncMigrationJobRequest request, AsyncMigrationContext context) {
    var restart = Boolean.TRUE.equals(request.getRestart());
    var resume = Boolean.TRUE.equals(request.getResume());
    return claim(context).compose(claim -> claim.job() != null
      ? succeededFuture(claim.job())
      : decide(context, buildJob(), resume, restart));
  }

  /**
   * Creates the state row and the job record in one transaction, so a concurrent request that sees the state
   * row always sees the job record too. If the state row already exists, nothing is written.
   */
  private Future<Claim> claim(AsyncMigrationContext context) {
    var newJob = buildJob();
    var postgresClient = context.getPostgresClient();
    return postgresClient.withTrans(conn -> conn
        .execute(format(INSERT_STATE_SQL, postgresClient.getSchemaName(), STATE_TABLE),
          Tuple.of(getMigrationName(), UUID.fromString(newJob.getId())))
        .compose(rows -> rows.rowCount() == 1
          ? conn.save(AsyncMigrationJobRepository.TABLE_NAME, newJob.getId(), newJob).map(true)
          : succeededFuture(false)))
      .map(inserted -> {
        if (!inserted) {
          return new Claim(null);
        }
        log.info("Background migration started [migration={}, jobId={}]", getMigrationName(), newJob.getId());
        startLoop(context, UUID.fromString(newJob.getId()), null);
        return new Claim(newJob);
      });
  }

  /**
   * Auto-start: creates the state row and starts the loop only if the row does not exist yet.
   */
  public Future<Void> autoStart(AsyncMigrationContext context) {
    return claim(context).mapEmpty();
  }

  private Future<AsyncMigrationJob> decide(AsyncMigrationContext context, AsyncMigrationJob newJob,
                                           boolean resume, boolean restart) {
    var postgresClient = context.getPostgresClient();
    var schema = postgresClient.getSchemaName();
    return postgresClient.execute(format(SELECT_STATE_SQL, schema, STATE_TABLE), Tuple.of(getMigrationName()))
      .compose(rows -> {
        var row = rows.iterator().next();
        var status = row.getString("status");
        var jobId = row.getUUID("job_id");
        var completed = COMPLETED.equals(status);
        var noNewRun = !restart && (completed || STARTED.equals(status) && !resume);
        if (!noNewRun) {
          return startRun(context, newJob, restart, jobId);
        }
        return existingJob(context, row, completed);
      });
  }

  private Future<AsyncMigrationJob> existingJob(AsyncMigrationContext context, Row row, boolean completed) {
    var jobId = row.getUUID("job_id");
    var existing = jobId != null
                   ? repository(context).getById(jobId.toString())
                   : succeededFuture((AsyncMigrationJob) null);
    return existing.compose(job -> {
      if (job == null) {
        return succeededFuture(statusJob(row, completed));
      }
      if (completed && job.getJobStatus() != AsyncMigrationJob.JobStatus.COMPLETED) {
        return repository(context).fetchAndUpdate(job.getId(), j -> completeFromState(j, row));
      }
      return succeededFuture(job);
    });
  }

  private AsyncMigrationJob completeFromState(AsyncMigrationJob job, Row row) {
    var finished = row.getOffsetDateTime("finished_at");
    job.getProcessed().removeIf(p -> getMigrationName().equals(p.getMigrationName()));
    job.getProcessed().add(counts(row.getLong("processed_count")));
    return job.withJobStatus(AsyncMigrationJob.JobStatus.COMPLETED)
      .withFinishedDate(finished == null ? new Date() : Date.from(finished.toInstant()));
  }

  /**
   * Status-only job built from the state row, used when the job record is missing and no new run is started.
   */
  private AsyncMigrationJob statusJob(Row row, boolean completed) {
    var finished = row.getOffsetDateTime("finished_at");
    var job = new AsyncMigrationJob()
      .withId(row.getUUID("job_id") == null ? null : row.getUUID("job_id").toString())
      .withJobStatus(completed ? AsyncMigrationJob.JobStatus.COMPLETED : AsyncMigrationJob.JobStatus.IN_PROGRESS)
      .withMigrations(new ArrayList<>(List.of(getMigrationName())))
      .withAffectedEntities(new ArrayList<>(List.of(config.affectedEntity().name())))
      .withFinishedDate(completed && finished != null ? Date.from(finished.toInstant()) : null);
    job.getProcessed().add(counts(row.getLong("processed_count")));
    return job;
  }

  private Future<AsyncMigrationJob> startRun(AsyncMigrationContext context, AsyncMigrationJob job,
                                             boolean restart, UUID previousJobId) {
    var postgresClient = context.getPostgresClient();
    var sql = format(restart ? RESTART_STATE_SQL : RESUME_STATE_SQL, postgresClient.getSchemaName(), STATE_TABLE);
    return postgresClient.withTrans(conn -> conn
        .execute(sql, Tuple.of(getMigrationName(), UUID.fromString(job.getId())).addUUID(previousJobId))
        .compose(rows -> saveIfWon(conn, job, rows)))
      .compose(started -> {
        if (!started.won()) {
          return currentJob(context);
        }
        return closePreviousJob(context, previousJobId).map(notUsed -> {
          log.info("Background migration started [migration={}, jobId={}, lastProcessedId={}]",
            getMigrationName(), job.getId(), started.cursor());
          startLoop(context, UUID.fromString(job.getId()), started.cursor());
          return job;
        });
      });
  }

  private Future<Started> saveIfWon(Conn conn, AsyncMigrationJob job, RowSet<Row> rows) {
    if (rows.rowCount() == 0) {
      return succeededFuture(new Started(false, null));
    }
    var row = rows.iterator().next();
    var alreadyProcessed = row.getLong("processed_count");
    if (alreadyProcessed > 0) {
      job.getProcessed().add(counts(alreadyProcessed));
    }
    return conn.save(AsyncMigrationJobRepository.TABLE_NAME, job.getId(), job)
      .map(new Started(true, row.getUUID("last_processed_id")));
  }

  /**
   * Another request has taken over the state row, return its job.
   */
  private Future<AsyncMigrationJob> currentJob(AsyncMigrationContext context) {
    var postgresClient = context.getPostgresClient();
    return postgresClient.execute(
        format(SELECT_STATE_SQL, postgresClient.getSchemaName(), STATE_TABLE), Tuple.of(getMigrationName()))
      .compose(rows -> {
        var winner = rows.iterator().next().getUUID("job_id");
        return winner == null ? succeededFuture((AsyncMigrationJob) null)
          : repository(context).getById(winner.toString());
      })
      .compose(winnerJob -> winnerJob != null ? succeededFuture(winnerJob)
        : Future.<AsyncMigrationJob>failedFuture(new IllegalStateException(
          "Concurrent migration request in progress, please retry")));
  }

  private Future<Void> closePreviousJob(AsyncMigrationContext context, UUID previousJobId) {
    if (previousJobId == null) {
      return succeededFuture();
    }
    return repository(context).fetchAndUpdate(previousJobId.toString(), this::cancelIfRunning)
      .<Void>mapEmpty()
      .recover(error -> {
        log.warn("Unable to close the superseded job [migration={}, jobId={}]", getMigrationName(),
          previousJobId, error);
        return succeededFuture();
      });
  }

  private AsyncMigrationJob cancelIfRunning(AsyncMigrationJob job) {
    var status = job.getJobStatus();
    if (status == AsyncMigrationJob.JobStatus.IN_PROGRESS || status == AsyncMigrationJob.JobStatus.PENDING_CANCEL) {
      job.withJobStatus(AsyncMigrationJob.JobStatus.CANCELLED).withFinishedDate(new Date());
    }
    return job;
  }

  private AsyncMigrationJob buildJob() {
    return new AsyncMigrationJob()
      .withId(randomUUID().toString())
      .withJobStatus(AsyncMigrationJob.JobStatus.IN_PROGRESS)
      .withMigrations(new ArrayList<>(List.of(getMigrationName())))
      .withAffectedEntities(new ArrayList<>(List.of(config.affectedEntity().name())))
      .withSubmittedDate(new Date());
  }

  private AsyncMigrationJobRepository repository(AsyncMigrationContext context) {
    return new AsyncMigrationJobRepository(context.getVertxContext(), context.getOkapiHeaders());
  }

  private AsyncMigrationJobCounts counts(long processed) {
    return new AsyncMigrationJobCounts()
      .withMigrationName(getMigrationName())
      .withCount((int) Math.min(processed, Integer.MAX_VALUE));
  }

  private void startLoop(AsyncMigrationContext context, UUID jobId, UUID cursor) {
    var run = new Run(context, jobId);
    step(run, cursor);
  }

  /**
   * The cursor is null for the first chunk, which is then read inclusively so the nil UUID is not skipped.
   */
  private void step(Run run, UUID lastId) {
    processWithRetry(run, lastId, 0)
      .compose(result -> reflectProgress(run, result).recover(error -> {
        log.warn("Unable to update the job progress [migration={}, jobId={}]", getMigrationName(), run.jobId, error);
        return succeededFuture(new Outcome(result, false));
      }))
      .onSuccess(outcome -> {
        run.chunks++;
        logChunk(run, outcome.result());
        if (outcome.result().completed()) {
          log.info("Background migration completed [migration={}, jobId={}, processed={}, inserted={}, skipped={}]",
            getMigrationName(), run.jobId, outcome.result().processedTotal(), outcome.result().insertedTotal(),
            outcome.result().skippedTotal());
        } else if (outcome.cancelRequested()) {
          cancel(run);
        } else {
          run.vertx.setTimer(Math.max(1L, settings.pauseMs()), id -> step(run, outcome.result().lastId()));
        }
      })
      .onFailure(error -> handleFailure(run, error));
  }

  private Future<ChunkResult> processWithRetry(Run run, UUID lastId, int attempt) {
    return processChunk(run, lastId)
      .recover(error -> {
        if (error instanceof SupersededException || attempt >= settings.retries()) {
          return failedFuture(error);
        }
        log.warn("Background migration chunk failed, will retry [migration={}, jobId={}, lastProcessedId={}, "
          + "attempt={}]", getMigrationName(), run.jobId, lastId, attempt + 1, error);
        Promise<ChunkResult> promise = Promise.promise();
        run.vertx.setTimer(Math.max(1L, settings.pauseMs()),
          id -> processWithRetry(run, lastId, attempt + 1).onComplete(promise));
        return promise.future();
      });
  }

  private Future<ChunkResult> processChunk(Run run, UUID lastId) {
    var client = run.postgresClient;
    var schema = client.getSchemaName();
    var chunkSql = format(CHUNK_SQL, schema, config.sourceTable(), config.jsonbField(), config.joinTable(),
      config.joinSourceColumn(), config.joinReferenceColumn(), config.referenceTable(),
      lastId == null ? ">=" : ">");
    var from = lastId == null ? NIL_UUID : lastId;
    return client.withTrans(conn -> conn.execute("SET LOCAL work_mem = '" + settings.workMemMb() + "MB'")
      .compose(notUsed -> conn.execute(chunkSql, Tuple.of(from, (long) settings.chunkSize())))
      .compose(rows -> {
        var row = rows.iterator().next();
        var count = row.getLong("chunk_count");
        var inserted = row.getLong("inserted_count");
        var skipped = Math.max(0L, row.getLong("code_count") - inserted);
        var newLastId = row.getUUID("last_id") == null ? lastId : row.getUUID("last_id");
        var completed = count < settings.chunkSize();
        return conn.execute(format(PROGRESS_STATE_SQL, schema, STATE_TABLE),
            Tuple.of(getMigrationName(), run.jobId, newLastId, count, inserted, skipped)
              .addString(completed ? COMPLETED : STARTED))
          .compose(updated -> updated.rowCount() == 0
            ? Future.<ChunkResult>failedFuture(new SupersededException())
            : succeededFuture(toResult(updated.iterator().next(), newLastId, completed)));
      }));
  }

  private ChunkResult toResult(Row total, UUID lastId, boolean completed) {
    return new ChunkResult(lastId, completed, total.getLong("processed_count"),
      total.getLong("inserted_count"), total.getLong("skipped_count"));
  }

  private Future<Outcome> reflectProgress(Run run, ChunkResult result) {
    return run.repository.fetchAndUpdate(run.jobId.toString(), job -> {
      job.getProcessed().removeIf(p -> getMigrationName().equals(p.getMigrationName()));
      job.getProcessed().add(counts(result.processedTotal()));
      if (result.completed()) {
        job.withJobStatus(AsyncMigrationJob.JobStatus.COMPLETED).withFinishedDate(new Date());
      }
      return job;
    }).map(job -> new Outcome(result, job.getJobStatus() == AsyncMigrationJob.JobStatus.PENDING_CANCEL));
  }

  private void logChunk(Run run, ChunkResult result) {
    if (run.chunks % LOG_EVERY_N_CHUNKS == 0) {
      log.info("Background migration progress [migration={}, jobId={}, processed={}, inserted={}, skipped={}, "
          + "lastProcessedId={}]", getMigrationName(), run.jobId, result.processedTotal(),
        result.insertedTotal(), result.skippedTotal(), result.lastId());
    }
  }

  private void cancel(Run run) {
    finishState(run, CANCELLED, null)
      .compose(notUsed -> run.repository.fetchAndUpdate(run.jobId.toString(),
        job -> job.withJobStatus(AsyncMigrationJob.JobStatus.CANCELLED).withFinishedDate(new Date())))
      .onSuccess(notUsed -> log.info("Background migration cancelled [migration={}, jobId={}]",
        getMigrationName(), run.jobId))
      .onFailure(error -> log.error("Unable to cancel background migration [migration={}, jobId={}]",
        getMigrationName(), run.jobId, error));
  }

  private void handleFailure(Run run, Throwable error) {
    if (error instanceof SupersededException) {
      log.info("Background migration loop stopped, a newer run has taken over [migration={}, jobId={}]",
        getMigrationName(), run.jobId);
      run.repository.fetchAndUpdate(run.jobId.toString(), this::cancelIfRunning)
        .onFailure(e -> log.warn("Unable to close the superseded job [migration={}, jobId={}]",
          getMigrationName(), run.jobId, e));
      return;
    }
    log.error("Background migration failed [migration={}, jobId={}]", getMigrationName(), run.jobId, error);
    var message = String.valueOf(error.getMessage());
    finishState(run, FAILED, message.substring(0, Math.min(message.length(), MAX_ERROR_MESSAGE_LENGTH)))
      .compose(notUsed -> run.repository.fetchAndUpdate(run.jobId.toString(),
        job -> job.getJobStatus() == AsyncMigrationJob.JobStatus.COMPLETED
          ? job
          : job.withJobStatus(AsyncMigrationJob.JobStatus.ID_PUBLISHING_FAILED).withFinishedDate(new Date())))
      .onFailure(e -> log.error("Unable to record failure of background migration [migration={}, jobId={}]",
        getMigrationName(), run.jobId, e));
  }

  private Future<Void> finishState(Run run, String status, String errorMessage) {
    return run.postgresClient.execute(
        format(FINISH_STATE_SQL, run.postgresClient.getSchemaName(), STATE_TABLE),
        Tuple.of(getMigrationName(), run.jobId, status).addString(errorMessage))
      .mapEmpty();
  }

  private static final class SupersededException extends RuntimeException {
    private SupersededException() {
      super("Migration run has been superseded by a newer run", null, false, false);
    }
  }

  private record Claim(AsyncMigrationJob job) { }

  private record Started(boolean won, UUID cursor) { }

  private record ChunkResult(UUID lastId, boolean completed,
                             long processedTotal, long insertedTotal, long skippedTotal) { }

  private record Outcome(ChunkResult result, boolean cancelRequested) { }

  private static final class Run {
    private final PostgresClient postgresClient;
    private final AsyncMigrationJobRepository repository;
    private final Vertx vertx;
    private final UUID jobId;
    private long chunks;

    private Run(AsyncMigrationContext context, UUID jobId) {
      this.postgresClient = context.getPostgresClient();
      this.repository = new AsyncMigrationJobRepository(context.getVertxContext(), context.getOkapiHeaders());
      this.vertx = context.getVertxContext().owner();
      this.jobId = jobId;
    }
  }
}
