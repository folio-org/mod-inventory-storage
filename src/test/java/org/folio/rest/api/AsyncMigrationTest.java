package org.folio.rest.api;

import static io.vertx.core.Future.succeededFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.CANCELLED;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.IN_PROGRESS;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.services.migration.MigrationName.ITEM_SHELVING_ORDER_MIGRATION;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import io.vertx.sqlclient.Tuple;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;
import junitparams.JUnitParamsRunner;
import lombok.SneakyThrows;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.persist.AsyncMigrationJobRepository;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;
import org.folio.rest.jaxrs.model.AsyncMigrationJobCollection;
import org.folio.rest.jaxrs.model.AsyncMigrationJobRequest;
import org.folio.rest.jaxrs.model.AsyncMigrations;
import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;
import org.folio.rest.jaxrs.model.Processed;
import org.folio.rest.jaxrs.model.Published;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.sql.TestRowStream;
import org.folio.services.migration.async.AsyncMigrationContext;
import org.folio.services.migration.async.ShelvingOrderMigrationJobRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class AsyncMigrationTest extends TestBaseWithInventoryUtil {

  private final AsyncMigrationJobRepository repository = getRepository();

  @SneakyThrows
  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));
  }

  @Test
  public void canMigrateItems() {
    var numberOfRecords = 101;

    var holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    IntStream.range(0, numberOfRecords).parallel().forEach(v ->
      itemsClient.create(pojo2JsonObject(buildItem(holdingsRecordId, ONLINE_LOCATION_ID, ANNEX_LIBRARY_LOCATION_ID)
        .withItemLevelCallNumber("K1 .M44")
        .withEffectiveCallNumberComponents(new EffectiveCallNumberComponents().withCallNumber("K1 .M44")))));

    var migrationJob = asyncMigration.postMigrationJob(new AsyncMigrationJobRequest()
      .withMigrations(List.of(ITEM_SHELVING_ORDER_MIGRATION.getValue())));

    await().atMost(25, SECONDS).until(() -> asyncMigration.getMigrationJob(migrationJob.getId())
                                              .getJobStatus() == AsyncMigrationJob.JobStatus.COMPLETED);

    var job = asyncMigration.getMigrationJob(migrationJob.getId());

    assertThat(job.getPublished().stream().map(Published::getCount)
      .mapToInt(Integer::intValue).sum(), is(numberOfRecords));
    assertThat(job.getProcessed().stream().map(Processed::getCount)
      .mapToInt(Integer::intValue).sum(), is(numberOfRecords));
    assertThat(job.getJobStatus(), is(AsyncMigrationJob.JobStatus.COMPLETED));
    assertThat(job.getSubmittedDate(), notNullValue());

    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));
  }

  @Test
  public void canGetAvailableMigrations() {
    AsyncMigrations migrations = asyncMigration.getMigrations();
    assertNotNull(migrations);
    assertEquals(Integer.valueOf(2), migrations.getTotalRecords());
  }

  @Test
  public void canGetAllAvailableMigrationJobs() {
    asyncMigration.postMigrationJob(new AsyncMigrationJobRequest()
      .withMigrations(List.of(ITEM_SHELVING_ORDER_MIGRATION.getValue())));
    AsyncMigrationJobCollection migrations = asyncMigration.getAllMigrationJobs();
    assertNotNull(migrations);
    assertFalse(migrations.getJobs().isEmpty());
  }

  @Test
  public void canCancelMigration() {
    var rowStream = new TestRowStream(5_000_000);
    var migrationJob = migrationJob();
    var postgresClient = spy(getPostgresClient());
    var connection = mock(Conn.class);

    when(postgresClient.withTrans(any()))
      .thenAnswer(invocation -> invocation.<Function<Conn, Future<Object>>>getArgument(0)
        .apply(connection));

    when(connection.selectStream(anyString(), any(Tuple.class), any())).thenAnswer(invocation -> {
      invocation.<Handler<RowStream<Row>>>getArgument(2).handle(rowStream);
      return succeededFuture();
    });

    get(repository.save(migrationJob.getId(), migrationJob).toCompletionStage()
      .toCompletableFuture());
    var amc = new AsyncMigrationContext(getContext(), okapiHeaders(), postgresClient);
    jobRunner().startAsyncMigration(migrationJob,
      new AsyncMigrationContext(amc, ITEM_SHELVING_ORDER_MIGRATION.getValue()));

    asyncMigration.cancelMigrationJob(migrationJob.getId());

    await().until(() -> asyncMigration.getMigrationJob(migrationJob.getId())
                          .getJobStatus() == CANCELLED);

    var job = asyncMigration.getMigrationJob(migrationJob.getId());

    assertThat(job.getJobStatus(), is(CANCELLED));
    assertThat(job.getPublished().getFirst().getCount(), greaterThanOrEqualTo(1000));
  }

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
      .withMigrations(Collections.singletonList(ITEM_SHELVING_ORDER_MIGRATION.getValue()))
      .withSubmittedDate(new Date());
  }

  private PostgresClient getPostgresClient() {
    return postgresClient(getContext(), okapiHeaders());
  }

  private ShelvingOrderMigrationJobRunner jobRunner() {
    return new ShelvingOrderMigrationJobRunner();
  }

  private AsyncMigrationJobRepository getRepository() {
    return new AsyncMigrationJobRepository(getContext(), okapiHeaders());
  }
}
