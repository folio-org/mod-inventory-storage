package org.folio.services.reindex;

import static org.folio.rest.impl.InstanceStorageAPI.INSTANCE_TABLE;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.CANCELLED;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.COMPLETED;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.FAILED;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.PENDING_CANCEL;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.WorkerExecutor;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.ReindexJobRepository;
import org.folio.rest.jaxrs.model.ReindexJob;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.persist.SQLConnection;
import org.folio.services.domainevent.InstanceDomainEventPublisher;

public final class ReindexJobRunner {
  private static final Logger log = LogManager.getLogger(ReindexJobRunner.class);
  private static final int POOL_SIZE = 2;
  private static volatile WorkerExecutor workerExecutor;

  private final ReindexJob reindexJob;
  private final InstanceDomainEventPublisher publisher;
  private final PostgresClientFuturized postgresClient;
  private final ReindexJobRepository reindexJobRepository;
  private final AtomicInteger recordsPublished;
  private SQLConnection connection;
  private RowStream<Row> stream;

  public ReindexJobRunner(Context vertxContext, Map<String, String> okapiHeaders, ReindexJob reindexJob) {
    this(vertxContext, okapiHeaders, new PostgresClientFuturized(PgUtil
      .postgresClient(vertxContext, okapiHeaders)), reindexJob);
  }

  public ReindexJobRunner(Context vertxContext, Map<String, String> okapiHeaders,
    PostgresClientFuturized postgresClient, ReindexJob reindexJob) {

    this.reindexJob = reindexJob;
    this.publisher = new InstanceDomainEventPublisher(vertxContext, okapiHeaders);
    this.postgresClient = postgresClient;
    this.reindexJobRepository = new ReindexJobRepository(vertxContext, okapiHeaders);
    this.recordsPublished = new AtomicInteger(0);

    initWorker(vertxContext);
  }

  public void startReindex() {
    workerExecutor.executeBlocking(
      promise -> streamInstanceIds()
        .map(notUsed -> null)
        .onComplete(promise))
      .map(notUsed -> null);
  }

  private Future<Void> streamInstanceIds() {
    return postgresClient.startTx()
      .map(this::setConnection)
      .compose(con -> postgresClient.selectStream(con, "SELECT id FROM " + INSTANCE_TABLE))
      .map(this::setStream)
      .compose(this::processStream)
      .onComplete(result -> {
        stream.close()
          .onComplete(notUsed -> postgresClient.endTx(connection))
          .onFailure(error -> log.warn("Unable to commit transaction", error));

        if (result.failed()) {
          log.warn("Unable to reindex instances", result.cause());
          logFailedJob();
        } else {
          log.info("Reindex completed");
          logReindexCompleted();
        }
      });
  }

  private void logReindexCompleted() {
    reindexJobRepository.fetchAndUpdate(reindexJob.getId(),
      job -> job.withPublished(recordsPublished.get()).withJobStatus(COMPLETED));
  }

  private Future<Void> processStream(RowStream<Row> stream) {
    Promise<Void> result = Promise.promise();

    stream.endHandler(notUsed -> {
      log.debug("End of the stream has reached");
      result.tryComplete();
    }).exceptionHandler(error -> {
      log.warn("Unable to reindex instances", error);
      result.tryFail(error);
    }).handler(row -> {
      var instanceId = row.getUUID("id");

      publisher.publishInstanceReindex(instanceId.toString())
      .onFailure(error ->
        log.warn("Unable to publish reindex event for instance [id = {}, jobId={}]",
          instanceId, reindexJob.getId()));

      recordsPublished.incrementAndGet();
      log.debug("Records processed so far: " + recordsPublished);

      if (shouldLogJobDetails()) {
        logJobDetails().onFailure(error -> {
          stream.pause();
          result.tryFail(error);
        });
      }
    });

    return result.future();
  }

  private Future<ReindexJob> logJobDetails() {
    return reindexJobRepository.fetchAndUpdate(reindexJob.getId(),
      job -> {
        if (job.getJobStatus() == PENDING_CANCEL) {
          throw new IllegalStateException("The job has been cancelled");
        }
        return job.withPublished(recordsPublished.get());
      });
  }

  private boolean shouldLogJobDetails() {
    return recordsPublished.get() % 1000 == 0;
  }

  private void logFailedJob() {
    reindexJobRepository.fetchAndUpdate(reindexJob.getId(),
      resp -> {
        var finalStatus = resp.getJobStatus() == PENDING_CANCEL ? CANCELLED : FAILED;
        return resp.withJobStatus(finalStatus).withPublished(recordsPublished.get());
      });
  }

  private RowStream<Row> setStream(RowStream<Row> stream) {
    this.stream = stream;
    return stream;
  }

  private SQLConnection setConnection(SQLConnection connection) {
    this.connection = connection;
    return connection;
  }

  private static void initWorker(Context vertxContext) {
    if (workerExecutor == null) {
      synchronized (ReindexJobRunner.class) {
        if (workerExecutor == null) {
          workerExecutor = vertxContext.owner()
            .createSharedWorkerExecutor("instance-reindex", POOL_SIZE);
        }
      }
    }
  }
}
