package org.folio.services.iteration;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.kafka.services.KafkaEnvironmentProperties.environment;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.CANCELLATION_PENDING;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.CANCELLED;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.COMPLETED;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.FAILED;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.WorkerExecutor;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.kafka.services.KafkaProducerRecordBuilder;
import org.folio.persist.InstanceRepository;
import org.folio.persist.IterationJobRepository;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.IterationJob;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.domainevent.CommonDomainEventPublisher;
import org.folio.services.domainevent.DomainEvent;
import org.folio.services.domainevent.DomainEventType;

public class IterationJobRunner {

  public static final String ITERATION_JOB_ID_HEADER = "iteration-job-id";

  private static final Logger log = LogManager.getLogger(IterationJobRunner.class);
  private static final int POOL_SIZE = 2;
  private static final int UPDATE_PUBLISHED_EVERY = 1000;
  private static volatile WorkerExecutor workerExecutor;

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final PostgresClientFuturized postgresClient;
  private final IterationJobRepository jobRepository;
  private final InstanceRepository instanceRepository;
  private CommonDomainEventPublisher<Instance> eventPublisher;

  public IterationJobRunner(Context vertxContext, Map<String, String> okapiHeaders) {
    this(new PostgresClientFuturized(PgUtil.postgresClient(vertxContext, okapiHeaders)),
      new IterationJobRepository(vertxContext, okapiHeaders),
      new InstanceRepository(vertxContext, okapiHeaders),
      vertxContext,
      okapiHeaders);
  }

  public IterationJobRunner(PostgresClientFuturized postgresClient, IterationJobRepository repository,
                            InstanceRepository instanceRepository, Context vertxContext,
                            Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;

    this.postgresClient = postgresClient;
    this.jobRepository = repository;
    this.instanceRepository = instanceRepository;

    initWorker(vertxContext);
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

  public void startIteration(IterationJob job) {
    String fullTopicName = KafkaTopicNameHelper.formatTopicName(environment(),
      tenantId(okapiHeaders),
      job.getJobParams().getTopicName());
    eventPublisher = new CommonDomainEventPublisher<>(vertxContext, okapiHeaders,
      fullTopicName);

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

          logIterationCompleted(published, context);
        }
      });
  }

  private Future<RowStream<Row>> selectInstanceIds(IterationContext ctx) {
    return instanceRepository.getAllIds(ctx.connection);
  }

  private void logIterationCompleted(Long recordsPublished, IterationContext context) {
    jobRepository.fetchAndUpdate(context.getJobId(),
      job -> job.withMessagesPublished(recordsPublished.intValue())
        .withJobStatus(COMPLETED));
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

    return jobRepository
      .fetchAndUpdate(context.getJobId(), job -> job.withMessagesPublished(records.intValue()))
      .map(job -> {
        if (job.getJobStatus() == CANCELLATION_PENDING) {
          throw new IllegalStateException("The job has been cancelled");
        }
        return job;
      });
  }

  private boolean shouldLogJobDetails(long records) {
    return records % UPDATE_PUBLISHED_EVERY == 0;
  }

  private void logFailedJob(IterationContext context) {
    jobRepository.fetchAndUpdate(context.getJobId(),
      resp -> {
        var finalStatus = resp.getJobStatus() == CANCELLATION_PENDING
                          ? CANCELLED
                          : FAILED;

        return resp.withJobStatus(finalStatus);
      });
  }

  private KafkaProducerRecordBuilder<String, Object> rowToProducerRecord(Row row, IterationContext context) {
    return new KafkaProducerRecordBuilder<String, Object>(TenantTool.tenantId(okapiHeaders))
      .key(row.getUUID("id").toString())
      .value(iterationEvent(context.getEventType()))
      .header(ITERATION_JOB_ID_HEADER, context.getJobId());
  }

  private DomainEvent<Object> iterationEvent(String eventType) {
    return new DomainEvent<>(null, null, DomainEventType.valueOf(eventType), tenantId(okapiHeaders));
  }

  private static final class IterationContext {

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
