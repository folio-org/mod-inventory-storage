package org.folio.services.reindex;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.persist.InstanceRepository.INSTANCE_TABLE;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.IDS_PUBLISHED;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.ID_PUBLISHING_CANCELLED;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.ID_PUBLISHING_FAILED;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.PENDING_CANCEL;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.services.domainevent.DomainEvent.reindexEvent;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.ReindexJobRepository;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.ReindexJob;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.persist.SQLConnection;
import org.folio.services.domainevent.CommonDomainEventPublisher;
import org.folio.services.kafka.InventoryProducerRecordBuilder;
import org.folio.services.kafka.topic.KafkaTopic;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.WorkerExecutor;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;

public class ReindexJobRunner {
  public static final String REINDEX_JOB_ID_HEADER = "reindex-job-id";
  private static final Logger log = LogManager.getLogger(ReindexJobRunner.class);
  private static final int POOL_SIZE = 2;
  private static volatile WorkerExecutor workerExecutor;

  private final PostgresClientFuturized postgresClient;
  private final ReindexJobRepository reindexJobRepository;
  private final CommonDomainEventPublisher<Instance> eventPublisher;
  private final String tenantId;

  public ReindexJobRunner(Context vertxContext, Map<String, String> okapiHeaders) {
    this(new PostgresClientFuturized(PgUtil.postgresClient(vertxContext, okapiHeaders)),
      new ReindexJobRepository(vertxContext, okapiHeaders),
      vertxContext,
      new CommonDomainEventPublisher<>(vertxContext, okapiHeaders, KafkaTopic.instance()),
      tenantId(okapiHeaders));
  }

  public ReindexJobRunner(PostgresClientFuturized postgresClient, ReindexJobRepository repository,
    Context vertxContext, CommonDomainEventPublisher<Instance> domainEventPublisher,
    String tenantId) {

    this.postgresClient = postgresClient;
    this.reindexJobRepository = repository;
    this.eventPublisher = domainEventPublisher;
    this.tenantId = tenantId;

    initWorker(vertxContext);
  }

  public void startReindex(ReindexJob reindexJob) {
    workerExecutor.executeBlocking(
      promise -> streamInstanceIds(new ReindexContext(reindexJob))
        .map(notUsed -> null)
        .onComplete(promise))
      .map(notUsed -> null);
  }

  private Future<Long> streamInstanceIds(ReindexContext context) {
    return postgresClient.startTx()
      .map(context::withConnection)
      .compose(ctx -> postgresClient.selectStream(ctx.connection,
        "SELECT id FROM " + postgresClient.getFullTableName(INSTANCE_TABLE)))
      .map(context::withStream)
      .compose(this::processStream)
      .onComplete(recordsPublished -> {
        context.stream.close()
          .onComplete(notUsed -> postgresClient.endTx(context.connection))
          .onFailure(error -> log.warn("Unable to commit transaction", error));

        if (recordsPublished.failed()) {
          log.warn("Unable to reindex instances", recordsPublished.cause());
          logFailedJob(context);
        } else {
          log.info("Reindex completed");
          logReindexCompleted(recordsPublished.result(), context);
        }
      });
  }

  private void logReindexCompleted(Long recordsPublished, ReindexContext context) {
    reindexJobRepository.fetchAndUpdate(context.getJobId(),
      job -> job.withPublished(recordsPublished.intValue())
        .withJobStatus(IDS_PUBLISHED));
  }

  private Future<Long> processStream(ReindexContext context) {
    return eventPublisher.publishStream(context.stream,
      row -> rowToProducerRecord(row, context),
      recordsPublished -> logJobDetails(recordsPublished, context));
  }

  private Future<ReindexJob> logJobDetails(Long records, ReindexContext context) {
    if (!shouldLogJobDetails(records)) {
      return succeededFuture(context.reindexJob);
    }

    return reindexJobRepository
      .fetchAndUpdate(context.getJobId(), job -> job.withPublished(records.intValue()))
      .map(job -> {
        if (job.getJobStatus() == PENDING_CANCEL) {
          throw new IllegalStateException("The job has been cancelled");
        }
        return job;
      });
  }

  private boolean shouldLogJobDetails(long records) {
    return records % 1000 == 0;
  }

  private void logFailedJob(ReindexContext context) {
    reindexJobRepository.fetchAndUpdate(context.getJobId(),
      resp -> {
        var finalStatus = resp.getJobStatus() == PENDING_CANCEL
          ? ID_PUBLISHING_CANCELLED : ID_PUBLISHING_FAILED;
        return resp.withJobStatus(finalStatus);
      });
  }

  private InventoryProducerRecordBuilder rowToProducerRecord(Row row, ReindexContext reindexContext) {
    return new InventoryProducerRecordBuilder()
      .key(row.getUUID("id").toString())
      .value(reindexEvent(tenantId))
      .header(REINDEX_JOB_ID_HEADER, reindexContext.getJobId());
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
    private SQLConnection connection;
    private RowStream<Row> stream;

    private ReindexContext(ReindexJob reindexJob) {
      this.reindexJob = reindexJob;
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
  }
}
