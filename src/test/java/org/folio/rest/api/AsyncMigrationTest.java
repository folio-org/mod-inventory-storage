package org.folio.rest.api;

import static io.vertx.core.Future.succeededFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.CANCELLED;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.IN_PROGRESS;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import io.vertx.core.Context;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;
import junitparams.JUnitParamsRunner;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.persist.AsyncMigrationJobRepository;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;
import org.folio.rest.jaxrs.model.AsyncMigrationJobCollection;
import org.folio.rest.jaxrs.model.AsyncMigrationJobRequest;
import org.folio.rest.jaxrs.model.AsyncMigrations;
import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;
import org.folio.rest.jaxrs.model.Processed;
import org.folio.rest.jaxrs.model.Published;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.support.sql.TestRowStream;
import org.folio.services.migration.async.AsyncMigrationContext;
import org.folio.services.migration.async.PublicationPeriodMigrationJobRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class AsyncMigrationTest extends TestBaseWithInventoryUtil {

  private final AsyncMigrationJobRepository repository = getRepository();

  private static Map<String, String> okapiHeaders() {
    return new CaseInsensitiveMap<>(Map.of(TENANT.toLowerCase(), TENANT_ID));
  }

  private static Context getContext() {
    return getVertx().getOrCreateContext();
  }

  private static AsyncMigrationJob migrationJob() {
    return new AsyncMigrationJob()
      .withJobStatus(IN_PROGRESS)
      .withId(UUID.randomUUID().toString())
      .withMigrations(Collections.singletonList("publicationPeriodMigration"))
      .withSubmittedDate(new Date());
  }

  @Test
  public void canMigrateItemsInstances() {
    var numberOfRecords = 101;

    var holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    IntStream.range(0, numberOfRecords).parallel().forEach(v ->
      itemsClient.create(JsonObject.mapFrom(buildItem(holdingsRecordId, ONLINE_LOCATION_ID, ANNEX_LIBRARY_LOCATION_ID)
        .withItemLevelCallNumber("K1 .M44")
        .withEffectiveCallNumberComponents(new EffectiveCallNumberComponents().withCallNumber("K1 .M44")))));

    IntStream.range(0, numberOfRecords).parallel().forEach(v ->
      instancesClient.create(new JsonObject()
        .put("title", "test" + v)
        .put("source", "MARC")
        .put("instanceTypeId", "30fffe0e-e985-4144-b2e2-1e8179bdb41f")
        .put("publication", new JsonArray()
          .add(new JsonObject()
            .put("role", "Publication")
            .put("place", "New York, NY, United States of America")
            .put("publisher", "Oxford University Press")
            .put("dateOfPublication", "[2018]"))
          .add(new JsonObject()
            .put("dateOfPublication", "©2018"))
        )));

    String sql = "update " + getPostgresClientFuturized().getFullTableName("instance")
      + " set jsonb = jsonb - 'publicationPeriod' where jsonb->> 'title' like 'test%'";
    postgresClient(getContext(), okapiHeaders()).execute(sql);
    await().atMost(10, SECONDS)
      .until(() -> instancesClient.getByQuery("?query=publicationPeriod.start==2018").isEmpty());

    var migrationJob = asyncMigration.postMigrationJob(new AsyncMigrationJobRequest()
      .withMigrations(Arrays.asList("publicationPeriodMigration", "itemShelvingOrderMigration")));

    await().atMost(25, SECONDS).until(() -> asyncMigration.getMigrationJob(migrationJob.getId())
      .getJobStatus() == AsyncMigrationJob.JobStatus.COMPLETED);

    var job = asyncMigration.getMigrationJob(migrationJob.getId());

    assertThat(job.getPublished().stream().map(Published::getCount)
      .mapToInt(Integer::intValue).sum(), is(numberOfRecords * 2));
    assertThat(job.getProcessed().stream().map(Processed::getCount)
      .mapToInt(Integer::intValue).sum(), is(numberOfRecords * 2));
    assertThat(job.getJobStatus(), is(AsyncMigrationJob.JobStatus.COMPLETED));
    assertThat(job.getSubmittedDate(), notNullValue());

    postgresClient(getContext(), okapiHeaders()).delete("instances",
      new Criterion().addCriterion(new Criteria().addField("title").setOperation("like").setVal("test%"))).result();
  }

  @Test
  public void canMigrateInstanceSubjectsAndSeries() {
    var numberOfRecords = 10;

    IntStream.range(0, numberOfRecords).parallel().forEach(v ->
      instancesClient.create(new JsonObject()
        .put("title", "test" + v)
        .put("source", "MARC")
        .put("instanceTypeId", "30fffe0e-e985-4144-b2e2-1e8179bdb41f")));

    var countDownLatch = new CountDownLatch(1);

    postgresClient(getContext(), okapiHeaders()).execute(
        "UPDATE " + getPostgresClientFuturized().getFullTableName("instance")
          + " SET jsonb = jsonb || '{\"series\":[\"Harry Potter V.1\", \"Harry Potter V.1\"], "
          + "\"subjects\": [\"fantasy\", \"magic\"]}' RETURNING id::text;")
      .onSuccess(event -> countDownLatch.countDown());

    await().atMost(5, SECONDS).until(() -> countDownLatch.getCount() == 0L);

    var migrationJob = asyncMigration.postMigrationJob(new AsyncMigrationJobRequest()
      .withMigrations(List.of("subjectSeriesMigration")));

    await().atMost(25, SECONDS).until(() -> asyncMigration.getMigrationJob(migrationJob.getId())
      .getJobStatus() == AsyncMigrationJob.JobStatus.COMPLETED);

    var job = asyncMigration.getMigrationJob(migrationJob.getId());

    assertThat(job.getPublished().stream().map(Published::getCount)
      .mapToInt(Integer::intValue).sum(), is(numberOfRecords));
    assertThat(job.getProcessed().stream().map(Processed::getCount)
      .mapToInt(Integer::intValue).sum(), is(numberOfRecords));
    assertThat(job.getJobStatus(), is(AsyncMigrationJob.JobStatus.COMPLETED));
    assertThat(job.getSubmittedDate(), notNullValue());
  }

  @Test
  public void canGetAvailableMigrations() {
    AsyncMigrations migrations = asyncMigration.getMigrations();
    assertNotNull(migrations);
    assertEquals(Integer.valueOf(3), migrations.getTotalRecords());
    assertEquals("publicationPeriodMigration", migrations.getAsyncMigrations().get(0).getMigrations().get(0));
  }

  @Test
  public void canGetAllAvailableMigrationJobs() {
    asyncMigration.postMigrationJob(new AsyncMigrationJobRequest()
      .withMigrations(List.of("itemShelvingOrderMigration")));
    AsyncMigrationJobCollection migrations = asyncMigration.getAllMigrationJobs();
    assertNotNull(migrations);
    assertFalse(migrations.getJobs().isEmpty());
  }

  @Test
  public void canCancelMigration() {
    var rowStream = new TestRowStream(5_000_000);
    var migrationJob = migrationJob();
    var postgresClientFuturized = spy(getPostgresClientFuturized());

    doReturn(succeededFuture(rowStream))
      .when(postgresClientFuturized).selectStream(any(), anyString());

    get(repository.save(migrationJob.getId(), migrationJob).toCompletionStage()
      .toCompletableFuture());
    var amc = new AsyncMigrationContext(getContext(), okapiHeaders(), postgresClientFuturized);
    jobRunner().startAsyncMigration(migrationJob, new AsyncMigrationContext(amc, "publicationPeriodMigration"));

    asyncMigration.cancelMigrationJob(migrationJob.getId());

    await().until(() -> asyncMigration.getMigrationJob(migrationJob.getId())
      .getJobStatus() == CANCELLED);

    var job = asyncMigration.getMigrationJob(migrationJob.getId());

    assertThat(job.getJobStatus(), is(CANCELLED));
    assertThat(job.getPublished().get(0).getCount(), greaterThanOrEqualTo(1000));
  }

  private PostgresClientFuturized getPostgresClientFuturized() {
    var postgresClient = postgresClient(getContext(), okapiHeaders());
    return new PostgresClientFuturized(postgresClient);
  }

  private PublicationPeriodMigrationJobRunner jobRunner() {
    return new PublicationPeriodMigrationJobRunner();
  }

  private AsyncMigrationJobRepository getRepository() {
    return new AsyncMigrationJobRepository(getContext(), okapiHeaders());
  }
}
