package org.folio.services.iteration;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.Environment.environmentName;
import static org.folio.persist.InstanceRepository.INSTANCE_TABLE;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.IDS_PUBLISHED;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.ID_PUBLISHING_CANCELLED;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.ID_PUBLISHING_FAILED;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.PENDING_CANCEL;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import java.util.Map;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.WorkerExecutor;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.IterationJobRepository;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.IterationJob;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.persist.SQLConnection;
import org.folio.services.domainevent.CommonDomainEventPublisher;
import org.folio.services.domainevent.DomainEvent;
import org.folio.services.domainevent.DomainEventType;
import org.folio.services.kafka.InventoryProducerRecordBuilder;
import org.folio.services.kafka.topic.KafkaTopic;

public class IterationJobRunner {

  public static final String ITERATION_JOB_ID_HEADER = "iteration-job-id";

  private static final Logger log = LogManager.getLogger(IterationJobRunner.class);
  private static final int POOL_SIZE = 2;
  private static final int UPDATE_PUBLISHED_EVERY = 1000;
  private static volatile WorkerExecutor workerExecutor;

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final PostgresClientFuturized postgresClient;
  private final IterationJobRepository repository;
  private CommonDomainEventPublisher<Instance> eventPublisher;


  public IterationJobRunner(Context vertxContext, Map<String, String> okapiHeaders) {
    this(new PostgresClientFuturized(PgUtil.postgresClient(vertxContext, okapiHeaders)),
      new IterationJobRepository(vertxContext, okapiHeaders),
      vertxContext,
      okapiHeaders);
  }

  public IterationJobRunner(PostgresClientFuturized postgresClient, IterationJobRepository repository,
                          Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;

    this.postgresClient = postgresClient;
    this.repository = repository;

    initWorker(vertxContext);
  }

  public void startIteration(IterationJob job) {
    eventPublisher = new CommonDomainEventPublisher<>(vertxContext, okapiHeaders,
        KafkaTopic.forName(job.getJobParams().getTopicName(), tenantId(okapiHeaders), environmentName()));

    workerExecutor.executeBlocking(
      promise -> streamInstanceIds(new IterationContext(job))
        .map(notUsed -> null)
        .onComplete(promise))
      .map(notUsed -> null);
  }

  private Future<Long> streamInstanceIds(IterationContext context) {
    return postgresClient.startTx()
      .map(context::withConnection)
      .compose(this::selectInstanceIds)
      .map(context::withStream)
      .compose(this::processStream)
      .onComplete(recordsPublished -> {
        context.stream.close()
          .onComplete(notUsed -> postgresClient.endTx(context.connection))
          .onFailure(error -> log.warn("Unable to commit transaction", error));

        if (recordsPublished.failed()) {
          log.warn("Unable to iterate instances", recordsPublished.cause());

          logFailedJob(context);
        } else {
          var published = recordsPublished.result();
          log.info("Instance iteration completed: totalRecords = {}", published);

          logReindexCompleted(published, context);
        }
      });
  }

  private Future<RowStream<Row>> selectInstanceIds(IterationContext ctx) {
    return postgresClient.selectStream(ctx.connection,
      "SELECT id FROM " + postgresClient.getFullTableName(INSTANCE_TABLE));
  }

  private void logReindexCompleted(Long recordsPublished, IterationContext context) {
    repository.fetchAndUpdate(context.getJobId(),
      job -> job.withPublished(recordsPublished.intValue())
        .withJobStatus(IDS_PUBLISHED));
  }

  private Future<Long> processStream(IterationContext context) {
    return eventPublisher.publishStream(context.stream,
      row -> rowToProducerRecord(row, context),
      recordsPublished -> logJobDetails(recordsPublished, context));
  }

  private Future<IterationJob> logJobDetails(Long records, IterationContext context) {
    if (!shouldLogJobDetails(records)) {
      return succeededFuture(context.job);
    }

    return repository
      .fetchAndUpdate(context.getJobId(), job -> job.withPublished(records.intValue()))
      .map(job -> {
        if (job.getJobStatus() == PENDING_CANCEL) {
          throw new IllegalStateException("The job has been cancelled");
        }
        return job;
      });
  }

  private boolean shouldLogJobDetails(long records) {
    return records % UPDATE_PUBLISHED_EVERY == 0;
  }

  private void logFailedJob(IterationContext context) {
    repository.fetchAndUpdate(context.getJobId(),
      resp -> {
        var finalStatus = resp.getJobStatus() == PENDING_CANCEL
          ? ID_PUBLISHING_CANCELLED
          : ID_PUBLISHING_FAILED;

        return resp.withJobStatus(finalStatus);
      });
  }

  private InventoryProducerRecordBuilder rowToProducerRecord(Row row, IterationContext context) {
    return new InventoryProducerRecordBuilder()
      .key(row.getUUID("id").toString())
      .value(iterationEvent(context.getEventType()))
      .header(ITERATION_JOB_ID_HEADER, context.getJobId());
  }

  private DomainEvent<Object> iterationEvent(String eventType) {
    return new DomainEvent<>(null, null, DomainEventType.valueOf(eventType), tenantId(okapiHeaders));
  }

  private static void initWorker(Context vertxContext) {
    if (workerExecutor == null) {
      synchronized (IterationJobRunner.class) {
        if (workerExecutor == null) {
          workerExecutor = vertxContext.owner()
            .createSharedWorkerExecutor("instance-iteration", POOL_SIZE);
        }
      }
    }
  }

  private static class IterationContext {

    private final IterationJob job;
    private SQLConnection connection;
    private RowStream<Row> stream;

    private IterationContext(IterationJob job) {
      this.job = job;
    }

    private IterationContext withConnection(SQLConnection connection) {
      this.connection = connection;
      return this;
    }

    private IterationContext withStream(RowStream<Row> stream) {
      this.stream = stream;
      return this;
    }

    private String getJobId() {
      return job.getId();
    }

    private String getEventType() {
      return job.getJobParams().getEventType();
    }

  }

}
