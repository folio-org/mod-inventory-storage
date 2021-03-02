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

public class ReindexJobRunner {
  private static final Logger log = LogManager.getLogger(ReindexJobRunner.class);
  private static final int POOL_SIZE = 2;
  private static volatile WorkerExecutor workerExecutor;

  private final InstanceDomainEventPublisher publisher;
  private final PostgresClientFuturized postgresClient;
  private final ReindexJobRepository reindexJobRepository;

  public ReindexJobRunner(Context vertxContext, Map<String, String> okapiHeaders) {
    this(vertxContext, okapiHeaders, new PostgresClientFuturized(PgUtil
      .postgresClient(vertxContext, okapiHeaders)));
  }

  public ReindexJobRunner(Context vertxContext, Map<String, String> okapiHeaders,
    PostgresClientFuturized postgresClient) {

    this.publisher = new InstanceDomainEventPublisher(vertxContext, okapiHeaders);
    this.postgresClient = postgresClient;
    this.reindexJobRepository = new ReindexJobRepository(vertxContext, okapiHeaders);

    initWorker(vertxContext);
  }

  public void startReindex(ReindexJob reindexJob) {
    workerExecutor.executeBlocking(
      promise -> streamInstanceIds(new ReindexContext(reindexJob))
        .map(notUsed -> null)
        .onComplete(promise))
      .map(notUsed -> null);
  }

  private Future<ReindexContext> streamInstanceIds(ReindexContext context) {
    return postgresClient.startTx()
      .map(context::withConnection)
      .compose(ctx -> postgresClient.selectStream(ctx.connection,
        "SELECT id FROM " + postgresClient.getFullTableName(INSTANCE_TABLE)))
      .map(context::withStream)
      .compose(this::processStream)
      .onComplete(result -> {
        context.stream.close()
          .onComplete(notUsed -> postgresClient.endTx(context.connection))
          .onFailure(error -> log.warn("Unable to commit transaction", error));

        if (result.failed()) {
          log.warn("Unable to reindex instances", result.cause());
          logFailedJob(context);
        } else {
          log.info("Reindex completed");
          logReindexCompleted(context);
        }
      });
  }

  private void logReindexCompleted(ReindexContext context) {
    reindexJobRepository.fetchAndUpdate(context.getJobId(),
      job -> job.withPublished(context.getRecordsPublished()).withJobStatus(COMPLETED));
  }

  private Future<ReindexContext> processStream(ReindexContext context) {
    Promise<ReindexContext> result = Promise.promise();
    var stream = context.stream;

    stream.endHandler(notUsed -> {
      log.debug("End of the stream has reached");
      result.tryComplete(context);
    }).exceptionHandler(error -> {
      log.warn("Unable to reindex instances", error);
      result.tryFail(error);
    }).handler(row -> {
      var instanceId = row.getUUID("id");

      publisher.publishInstanceReindex(instanceId.toString(), context.getJobId())
      .onFailure(error ->
        log.warn("Unable to publish reindex event for instance [id = {}, jobId={}]",
          instanceId, context.getJobId()));

      context.incrementPublishedRecords();
      log.debug("Records processed so far: " + context.getRecordsPublished());

      if (shouldLogJobDetails(context)) {
        logJobDetails(context).onFailure(error -> {
          stream.pause();
          result.tryFail(error);
        });
      }
    });

    return result.future();
  }

  private Future<ReindexJob> logJobDetails(ReindexContext context) {
    return reindexJobRepository.fetchAndUpdate(context.getJobId(),
      job -> {
        if (job.getJobStatus() == PENDING_CANCEL) {
          throw new IllegalStateException("The job has been cancelled");
        }
        return job.withPublished(context.getRecordsPublished());
      });
  }

  private boolean shouldLogJobDetails(ReindexContext context) {
    return context.getRecordsPublished() % 1000 == 0;
  }

  private void logFailedJob(ReindexContext context) {
    reindexJobRepository.fetchAndUpdate(context.getJobId(),
      resp -> {
        var finalStatus = resp.getJobStatus() == PENDING_CANCEL ? CANCELLED : FAILED;
        return resp.withJobStatus(finalStatus).withPublished(context.getRecordsPublished());
      });
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

  private static class ReindexContext {
    private final ReindexJob reindexJob;
    private final AtomicInteger recordsPublished;
    private SQLConnection connection;
    private RowStream<Row> stream;

    private ReindexContext(ReindexJob reindexJob) {
      this.reindexJob = reindexJob;
      this.recordsPublished = new AtomicInteger(0);
    }

    private ReindexContext withConnection(SQLConnection connection) {
      this.connection = connection;
      return this;
    }

    private ReindexContext withStream(RowStream<Row> stream) {
      this.stream = stream;
      return this;
    }

    private String getJobId() {
      return reindexJob.getId();
    }

    private int getRecordsPublished() {
      return recordsPublished.get();
    }

    public void incrementPublishedRecords() {
      recordsPublished.incrementAndGet();
    }
  }
}
