package org.folio.it.api;

import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.folio.it.HoldingsStorageFixtures.createHolding;
import static org.folio.it.HoldingsStorageFixtures.createLoanType;
import static org.folio.it.HoldingsStorageFixtures.createMaterialType;
import static org.folio.it.InstanceStorageFixtures.createInstance;
import static org.folio.it.InstanceStorageFixtures.createInstanceType;
import static org.folio.it.LocationStorageFixtures.createLocation;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.CANCELLED;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.COMPLETED;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.IN_PROGRESS;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.services.migration.MigrationName.ITEM_ORDER_MIGRATION;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import io.vertx.sqlclient.Tuple;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.dataimport.testsupport.vertx.VertxTestUtil;
import org.folio.it.BaseIntegrationTest;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.persist.AsyncMigrationJobRepository;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;
import org.folio.rest.jaxrs.model.AsyncMigrationJobCounts;
import org.folio.rest.jaxrs.model.AsyncMigrationJobRequest;
import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;
import org.folio.rest.persist.Conn;
import org.folio.services.migration.async.AsyncMigrationContext;
import org.folio.services.migration.async.ItemOrderMigrationJobRunner;
import org.folio.support.ResourcePaths;
import org.folio.support.builders.ItemRequestBuilder;
import org.folio.support.sql.TestRowStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AsyncMigrationIT extends BaseIntegrationTest {

  @Test
  @DisplayName("should migrate items for the item-order migration")
  void shouldMigrateItems_forItemOrderMigration() {
    // the migration streams distinct holdings ids (one per holding, regardless of how many
    // items it has), so creating 101 items under a single holding still expects a published/
    // processed count of 1 - the item count only exercises the per-holding reordering at scale
    var numberOfItems = 101;
    var expectedHoldingsCount = 1;
    var instanceTypeId = createInstanceType(client);
    var instanceId = createInstance(client, "an instance", instanceTypeId);
    var holdingId = createHolding(client, instanceId, createLocation(client));
    var materialTypeId = createMaterialType(client);
    var loanTypeId = createLoanType(client);

    for (int i = 0; i < numberOfItems; i++) {
      createItemWithCallNumber(holdingId, materialTypeId, loanTypeId, "K1 .M44");
    }

    var migrationJob = postMigrationJob();

    await().atMost(Duration.ofSeconds(25))
      .until(() -> getMigrationJob(migrationJob.getId()).getJobStatus() == COMPLETED);

    var job = getMigrationJob(migrationJob.getId());

    assertThat(sumCounts(job.getPublished())).isEqualTo(expectedHoldingsCount);
    assertThat(sumCounts(job.getProcessed())).isEqualTo(expectedHoldingsCount);
    assertThat(job.getJobStatus()).isEqualTo(COMPLETED);
    assertThat(job.getSubmittedDate()).isNotNull();
  }

  @Test
  @DisplayName("should get the available migrations")
  void shouldGetAvailableMigrations() {
    var migrations = VertxTestUtil.await(doGet(client, ResourcePaths.MIGRATIONS)).jsonBody();

    assertThat(migrations.getInteger("totalRecords")).isEqualTo(1);
  }

  @Test
  @DisplayName("should get all migration jobs")
  void shouldGetAllMigrationJobs() {
    postMigrationJob();

    var migrationJobs = VertxTestUtil.await(doGet(client, ResourcePaths.MIGRATION_JOBS)).jsonBody();

    assertThat(migrationJobs.getJsonArray("jobs")).isNotEmpty();
  }

  @Test
  @DisplayName("should cancel an in-progress migration")
  void shouldCancelMigration_whenInProgress(Vertx vertx) {
    var rowStream = new TestRowStream(5_000_000);
    var migrationJob = migrationJob();
    var repository = new AsyncMigrationJobRepository(getContext(vertx), okapiHeaders());
    var mockPostgresClient = spy(postgresClient(getContext(vertx), okapiHeaders()));
    var connection = mock(Conn.class);

    when(mockPostgresClient.withTrans(any()))
      .thenAnswer(invocation -> invocation.<Function<Conn, Future<Object>>>getArgument(0)
        .apply(connection));
    when(connection.selectStream(anyString(), any(Tuple.class), any())).thenAnswer(invocation -> {
      invocation.<Handler<RowStream<Row>>>getArgument(2).handle(rowStream);
      return succeededFuture();
    });

    VertxTestUtil.await(repository.save(migrationJob.getId(), migrationJob));

    var context = new AsyncMigrationContext(getContext(vertx), okapiHeaders(), mockPostgresClient);
    new ItemOrderMigrationJobRunner().startAsyncMigration(migrationJob,
      new AsyncMigrationContext(context, ITEM_ORDER_MIGRATION.getValue()));

    cancelMigrationJob(migrationJob.getId());

    await().until(() -> getMigrationJob(migrationJob.getId()).getJobStatus() == CANCELLED);

    var job = getMigrationJob(migrationJob.getId());

    assertThat(job.getJobStatus()).isEqualTo(CANCELLED);
    assertThat(job.getPublished().getFirst().getCount()).isGreaterThanOrEqualTo(1000);
  }

  private static void createItemWithCallNumber(String holdingId, String materialTypeId, String loanTypeId,
                                                String callNumber) {
    var request = new ItemRequestBuilder()
      .forHolding(UUID.fromString(holdingId))
      .withMaterialType(UUID.fromString(materialTypeId))
      .withPermanentLoanType(UUID.fromString(loanTypeId))
      .withItemLevelCallNumber(callNumber)
      .create()
      .put("effectiveCallNumberComponents", pojo2JsonObject(
        new EffectiveCallNumberComponents().withCallNumber(callNumber)));

    VertxTestUtil.await(doPost(client, ResourcePaths.ITEMS, request));
  }

  private static int sumCounts(List<AsyncMigrationJobCounts> counts) {
    return counts.stream().mapToInt(AsyncMigrationJobCounts::getCount).sum();
  }

  private static AsyncMigrationJob postMigrationJob() {
    var request = new AsyncMigrationJobRequest().withMigrations(List.of(ITEM_ORDER_MIGRATION.getValue()));
    return VertxTestUtil.await(doPost(client, ResourcePaths.MIGRATION_JOBS, pojo2JsonObject(request))).jsonBody()
      .mapTo(AsyncMigrationJob.class);
  }

  private static AsyncMigrationJob getMigrationJob(String id) {
    return VertxTestUtil.await(doGet(client, ResourcePaths.MIGRATION_JOBS + "/" + id))
      .jsonBody().mapTo(AsyncMigrationJob.class);
  }

  private static void cancelMigrationJob(String id) {
    VertxTestUtil.await(doDelete(client, ResourcePaths.MIGRATION_JOBS + "/" + id));
  }

  private static AsyncMigrationJob migrationJob() {
    return new AsyncMigrationJob()
      .withJobStatus(IN_PROGRESS)
      .withId(UUID.randomUUID().toString())
      .withMigrations(Collections.singletonList(ITEM_ORDER_MIGRATION.getValue()))
      .withSubmittedDate(new Date());
  }

  private static Map<String, String> okapiHeaders() {
    return new CaseInsensitiveMap<>(Map.of(XOkapiHeaders.TENANT.toLowerCase(), TENANT_ID));
  }

  private static Context getContext(Vertx vertx) {
    return vertx.getOrCreateContext();
  }
}
