package org.folio.services.migration.async;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.AsyncMigrationJobRepository;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.domainevent.CommonDomainEventPublisher;
import org.folio.services.kafka.InventoryProducerRecordBuilder;
import org.folio.services.kafka.topic.KafkaTopic;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.Environment.environmentName;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.IDS_PUBLISHED;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.ID_PUBLISHING_CANCELLED;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.ID_PUBLISHING_FAILED;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.PENDING_CANCEL;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.services.domainevent.DomainEvent.asyncMigrationEvent;

public abstract class AbstractAsyncMigrationJobRunner {

  public static final String ASYNC_MIGRATION_JOB_ID_HEADER = "async-migration-job-id";
  private final Logger log = LogManager.getLogger(getClass());

  protected Future<Void> startMigration(AsyncMigrationJob migrationJob, AsyncMigrationContext context) {
    var asyncMigrationJobRepository = new AsyncMigrationJobRepository(context.getVertxContext(), context.getOkapiHeaders());
    var publisher = new CommonDomainEventPublisher<Instance>(context.getVertxContext(), context.getOkapiHeaders(),
      KafkaTopic.asyncMigration(tenantId(context.getOkapiHeaders()), environmentName()));
    var streamingContext = new StreamingContext(migrationJob, context, asyncMigrationJobRepository, publisher);

    return streamIdsForMigration(streamingContext)
      .onSuccess(records -> log.info("All ids for the class has been " +
        "sent [class={}, idsCount={}]", getClass(), records))
      .onFailure(error -> log.error("Unable to complete migration for the class [class={}]",
        getClass(), error))
      .mapEmpty();
  }

  private Future<Long> streamIdsForMigration(StreamingContext context) {
    var postgresClient = context.getMigrationContext().getPostgresClient();
    return postgresClient.startTx()
      .map(context::withConnection)
      .compose(streamingContext -> openStream(postgresClient, streamingContext.connection))
      .map(context::withStream)
      .compose(this::processStream)
      .onComplete(recordsPublished -> {
        context.stream.close()
          .onComplete(notUsed -> postgresClient.endTx(context.connection))
          .onFailure(error -> log.warn("Unable to commit transaction", error));

        if (recordsPublished.failed()) {
          log.warn("Unable to publish ids for async migration", recordsPublished.cause());
          logJobFail(context.migrationJobRepository, context.job);
        } else {
          log.info("Publishing records for migration completed");
          logPublishingCompleted(recordsPublished.result(), context);
        }
      });
  }

  private Future<Long> processStream(StreamingContext context) {
    return context.getPublisher().publishStream(context.stream,
      row -> rowToProducerRecord(row, context),
      recordsPublished -> logJobDetails(context.migrationJobRepository, context.job, recordsPublished));
  }

  private InventoryProducerRecordBuilder rowToProducerRecord(Row row, StreamingContext context) {
    return new InventoryProducerRecordBuilder()
      .key(row.getUUID("id").toString())
      .value(asyncMigrationEvent(TenantTool.tenantId(context.getMigrationContext().getOkapiHeaders())))
      .header(ASYNC_MIGRATION_JOB_ID_HEADER, context.getJobId());
  }

  private void logJobFail(AsyncMigrationJobRepository repository, AsyncMigrationJob job) {
    repository.fetchAndUpdate(job.getId(),
      resp -> {
        var finalStatus = resp.getJobStatus() == PENDING_CANCEL
          ? ID_PUBLISHING_CANCELLED : ID_PUBLISHING_FAILED;
        return resp.withJobStatus(finalStatus);
      });
  }

  private void logPublishingCompleted(Long recordsPublished, StreamingContext context) {
    context.getMigrationJobRepository().fetchAndUpdate(context.getJobId(),
      job -> job.withPublished(recordsPublished.intValue())
        .withJobStatus(IDS_PUBLISHED));
  }

  private Future<AsyncMigrationJob> logJobDetails(AsyncMigrationJobRepository repository, AsyncMigrationJob migrationJob, Long records) {
    if (!shouldLogJobDetails(records)) {
      return succeededFuture(migrationJob);
    }
    return repository
      .fetchAndUpdate(migrationJob.getId(), job -> job.withPublished(records.intValue()))
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

  private static class StreamingContext {
    private final AsyncMigrationJob job;
    private final AsyncMigrationContext migrationContext;
    private final AsyncMigrationJobRepository migrationJobRepository;
    private final CommonDomainEventPublisher<Instance> publisher;
    private SQLConnection connection;
    private RowStream<Row> stream;

    private StreamingContext(AsyncMigrationJob job, AsyncMigrationContext migrationContext,
                             AsyncMigrationJobRepository migrationJobRepository,
                             CommonDomainEventPublisher<Instance> publisher) {
      this.job = job;
      this.migrationContext = migrationContext;
      this.migrationJobRepository = migrationJobRepository;
      this.publisher = publisher;
    }

    private AbstractAsyncMigrationJobRunner.StreamingContext withConnection(SQLConnection connection) {
      this.connection = connection;
      return this;
    }

    private AbstractAsyncMigrationJobRunner.StreamingContext withStream(RowStream<Row> stream) {
      this.stream = stream;
      return this;
    }

    private String getJobId() {
      return job.getId();
    }

    public AsyncMigrationContext getMigrationContext() {
      return migrationContext;
    }

    public AsyncMigrationJobRepository getMigrationJobRepository() {
      return migrationJobRepository;
    }

    public CommonDomainEventPublisher<Instance> getPublisher() {
      return publisher;
    }
  }

  protected abstract Future<RowStream<Row>> openStream(PostgresClientFuturized postgresClient, SQLConnection connection);

}
