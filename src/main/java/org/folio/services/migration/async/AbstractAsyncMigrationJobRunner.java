package org.folio.services.migration.async;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.domainevent.CommonDomainEventPublisher;
import org.folio.services.kafka.InventoryProducerRecordBuilder;
import org.folio.services.kafka.topic.KafkaTopic;

import static org.folio.Environment.environmentName;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.services.domainevent.DomainEvent.asyncMigrationEvent;

public abstract class AbstractAsyncMigrationJobRunner implements AsyncMigrationJobRunner {

  public static final String ASYNC_MIGRATION_JOB_ID_HEADER = "async-migration-job-id";
  public static final String ASYNC_MIGRATION_JOB_NAME = "async-migration-job-name";
  private final Logger log = LogManager.getLogger(getClass());

  @Override
  public void startAsyncMigration(AsyncMigrationJob migrationJob, AsyncMigrationContext context) {
    context.getVertxContext().executeBlocking(v ->
      startMigration(migrationJob, context)
        .onComplete(result -> v.complete()));
  }

  protected Future<Void> startMigration(AsyncMigrationJob migrationJob, AsyncMigrationContext context) {
    var migrationService = new AsyncMigrationJobService(context.getVertxContext(), context.getOkapiHeaders());
    var publisher = new CommonDomainEventPublisher<AsyncMigrationJob>(context.getVertxContext(), context.getOkapiHeaders(),
      KafkaTopic.asyncMigration(tenantId(context.getOkapiHeaders()), environmentName()));
    var streamingContext = new StreamingContext(migrationJob, context, migrationService, publisher);

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
          context.getAsyncMigrationService().logJobFail(context.getJobId());
        } else {
          log.info("Publishing records for migration completed");
          context.getAsyncMigrationService()
            .logPublishingCompleted(context.getMigrationContext().getMigrationName(), recordsPublished.result(), context.getJobId());
        }
      });
  }

  private Future<Long> processStream(StreamingContext context) {
    return context.getPublisher().publishStream(context.stream,
      row -> rowToProducerRecord(row, context),
      recordsPublished -> context.getAsyncMigrationService()
        .logJobDetails(context.getMigrationContext().getMigrationName(), context.getJob(), recordsPublished));
  }

  private InventoryProducerRecordBuilder rowToProducerRecord(Row row, StreamingContext context) {
    return new InventoryProducerRecordBuilder()
      .key(row.getUUID("id").toString())
      .value(asyncMigrationEvent(context.getJob(), TenantTool.tenantId(context.getMigrationContext().getOkapiHeaders())))
      .header(ASYNC_MIGRATION_JOB_ID_HEADER, context.getJobId())
      .header(ASYNC_MIGRATION_JOB_NAME, context.getMigrationContext().getMigrationName());
  }

  private static class StreamingContext {
    private final AsyncMigrationJob job;
    private final AsyncMigrationContext migrationContext;
    private final AsyncMigrationJobService migrationService;
    private final CommonDomainEventPublisher<AsyncMigrationJob> publisher;
    private SQLConnection connection;
    private RowStream<Row> stream;

    private StreamingContext(AsyncMigrationJob job, AsyncMigrationContext migrationContext,
                             AsyncMigrationJobService migrationService,
                             CommonDomainEventPublisher<AsyncMigrationJob> publisher) {
      this.job = job;
      this.migrationContext = migrationContext;
      this.migrationService = migrationService;
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

    private AsyncMigrationJob getJob() {
      return job;
    }

    public AsyncMigrationContext getMigrationContext() {
      return migrationContext;
    }

    public AsyncMigrationJobService getAsyncMigrationService() {
      return migrationService;
    }

    public CommonDomainEventPublisher<AsyncMigrationJob> getPublisher() {
      return publisher;
    }
  }

  protected abstract Future<RowStream<Row>> openStream(PostgresClientFuturized postgresClient, SQLConnection connection);

}
