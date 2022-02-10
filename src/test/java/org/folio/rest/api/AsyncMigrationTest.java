package org.folio.rest.api;

import io.vertx.core.Context;
import org.folio.persist.AsyncMigrationJobRepository;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;
import org.folio.rest.jaxrs.model.AsyncMigrationJobRequest;
import org.folio.rest.jaxrs.model.AsyncMigrations;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.folio.rest.support.sql.TestRowStream;
import org.folio.services.migration.async.AsyncMigrationContext;
import org.folio.services.migration.async.PublicationPeriodMigrationJobRunner;
import org.junit.Test;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static io.vertx.core.Future.succeededFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.IDS_PUBLISHED;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.ID_PUBLISHING_CANCELLED;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.IN_PROGRESS;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class AsyncMigrationTest extends TestBaseWithInventoryUtil {

  private final AsyncMigrationJobRepository repository = getRepository();

  @Test
  public void canCreateMigrationJob() {
    FakeKafkaConsumer.removeAllEvents();
    var job = asyncMigration.postMigrationJob(new AsyncMigrationJobRequest().withName("publicationPeriodMigration"));
    assertNotNull(job.getId());

    await().until(() -> asyncMigration.getMigrationJob(job.getId())
      .getJobStatus() == IDS_PUBLISHED);
  }

  @Test
  public void canMigrateInstances() {
    var numberOfRecords = 1100;
    var rowStream = new TestRowStream(numberOfRecords);
    var migrationJob = migrationJob();
    var postgresClientFuturized = spy(getPostgresClientFuturized());

    doReturn(succeededFuture(rowStream))
      .when(postgresClientFuturized).selectStream(any(), anyString());

    get(repository.save(migrationJob.getId(), migrationJob).toCompletionStage()
      .toCompletableFuture());


    FakeKafkaConsumer.removeAllEvents();
    jobRunner().startAsyncMigration(migrationJob, new AsyncMigrationContext(getContext(), okapiHeaders(), postgresClientFuturized));

    await().atMost(20, SECONDS).until(() -> asyncMigration.getMigrationJob(migrationJob.getId())
      .getJobStatus() == AsyncMigrationJob.JobStatus.COMPLETED);

    var job = asyncMigration.getMigrationJob(migrationJob.getId());

    assertThat(job.getPublished(), is(numberOfRecords));
    assertThat(job.getProcessed(), is(numberOfRecords));
    assertThat(job.getJobStatus(), is(AsyncMigrationJob.JobStatus.COMPLETED));
    assertThat(job.getSubmittedDate(), notNullValue());

    await().atMost(5, SECONDS)
      .until(FakeKafkaConsumer::getAllPublishedMigrationsCount, greaterThanOrEqualTo(numberOfRecords));
  }

  @Test
  public void canGetAvailableMigrations() {
    AsyncMigrations migrations = asyncMigration.getMigrations();
    assertNotNull(migrations);
    assertEquals(Integer.valueOf(1), migrations.getTotalRecords());
    assertEquals("publicationPeriodMigration", migrations.getAsyncMigrations().get(0).getName());
  }

  @Test
  public void canCancelMigration() {
    var rowStream = new TestRowStream(10_000_000);
    var migrationJob = migrationJob();
    var postgresClientFuturized = spy(getPostgresClientFuturized());

    doReturn(succeededFuture(rowStream))
      .when(postgresClientFuturized).selectStream(any(), anyString());

    get(repository.save(migrationJob.getId(), migrationJob).toCompletionStage()
      .toCompletableFuture());

    jobRunner().startAsyncMigration(migrationJob, new AsyncMigrationContext(getContext(), okapiHeaders(), postgresClientFuturized));

    asyncMigration.cancelMigrationJob(migrationJob.getId());

    await().until(() -> asyncMigration.getMigrationJob(migrationJob.getId())
      .getJobStatus() == ID_PUBLISHING_CANCELLED);

    var job = asyncMigration.getMigrationJob(migrationJob.getId());

    assertThat(job.getJobStatus(), is(ID_PUBLISHING_CANCELLED));
    assertThat(job.getPublished(), greaterThanOrEqualTo(1000));
  }

  private PostgresClientFuturized getPostgresClientFuturized() {
    var postgresClient = postgresClient(getContext(), okapiHeaders());
    return new PostgresClientFuturized(postgresClient);
  }

  private static Map<String, String> okapiHeaders() {
    return Map.of(TENANT.toLowerCase(), TENANT_ID);
  }

  private static Context getContext() {
    return StorageTestSuite.getVertx().getOrCreateContext();
  }

  private PublicationPeriodMigrationJobRunner jobRunner() {
    return new PublicationPeriodMigrationJobRunner();
  }

  private AsyncMigrationJobRepository getRepository() {
    return new AsyncMigrationJobRepository(getContext(), okapiHeaders());
  }

  private static AsyncMigrationJob migrationJob() {
    return new AsyncMigrationJob()
      .withJobStatus(IN_PROGRESS)
      .withId(UUID.randomUUID().toString())
      .withName("publicationPeriodMigration")
      .withSubmittedDate(new Date());
  }
}
