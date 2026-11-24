package org.folio.rest.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.CANCELLED;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.COMPLETED;
import static org.folio.rest.jaxrs.model.AsyncMigrationJob.JobStatus.IN_PROGRESS;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.support.http.InterfaceUrls.migrationJobsUrl;
import static org.folio.services.migration.MigrationName.INSTANCE_STATISTICAL_CODES;
import static org.folio.services.migration.MigrationName.ITEM_ORDER_MIGRATION;
import static org.folio.services.migration.async.StateTableMigrationConfig.INSTANCE_STATISTICAL_CODES_CONFIG;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.persist.AsyncMigrationJobRepository;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;
import org.folio.rest.jaxrs.model.AsyncMigrationJobRequest;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.migration.async.AsyncMigrationContext;
import org.folio.services.migration.async.BackgroundMigrationSettings;
import org.folio.services.migration.async.StateTableMigrationJobRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class InstanceStatisticalCodesMigrationTest extends TestBaseWithInventoryUtil {

  private static final String NAME = INSTANCE_STATISTICAL_CODES.getValue();
  private static final String CODE_1 = "10000000-0000-4000-8000-000000000001";
  private static final String CODE_2 = "10000000-0000-4000-8000-000000000002";
  private static final String CODE_3 = "10000000-0000-4000-8000-000000000003";

  private final PostgresClient pg = postgresClient(getContext(), okapiHeaders());
  private final String schema = pg.getSchemaName();

  @Before
  public void setUp() {
    cleanUp();
    sql("INSERT INTO %s.statistical_code (id, jsonb) VALUES ('" + CODE_1 + "', "
      + "'{\"id\":\"" + CODE_1 + "\",\"code\":\"migtest1\",\"name\":\"migtest1\"}'::jsonb), "
      + "('" + CODE_2 + "', '{\"id\":\"" + CODE_2 + "\",\"code\":\"migtest2\",\"name\":\"migtest2\"}'::jsonb)");
  }

  @After
  public void tearDown() {
    cleanUp();
  }

  private void cleanUp() {
    sql("DELETE FROM %s.instance_statistical_code");
    sql("DELETE FROM %s.instance");
    sql("DELETE FROM %s.statistical_code WHERE jsonb->>'code' LIKE 'migtest%%'");
    sql("DELETE FROM %s.background_migration_state WHERE name = '" + NAME + "'");
  }

  @Test
  public void chunkBoundaries() {
    for (var count : new int[] {0, 2, 3, 5}) {
      cleanUp();
      sql("INSERT INTO %s.statistical_code (id, jsonb) VALUES ('" + CODE_1 + "', "
        + "'{\"id\":\"" + CODE_1 + "\",\"code\":\"migtest1\",\"name\":\"migtest1\"}'::jsonb)");
      for (int i = 1; i <= count; i++) {
        insertInstance(i, "[\"" + CODE_1 + "\"]");
      }
      var job = submit(runner(), request(false, false));
      awaitState("COMPLETED");
      awaitJobCompleted(job);
      assertThat("rows for " + count, joinCount(), is((long) count));
      assertThat(stateLong("processed_count"), is((long) count));
      assertThat(stateLong("inserted_count"), is((long) count));
      assertThat(stateLong("skipped_count"), is(0L));
    }
  }

  @Test
  public void invalidIdsAreSkippedAndCaseIsNormalised() {
    sql("INSERT INTO %s.statistical_code (id, jsonb) VALUES ('" + CODE_3 + "', "
      + "'{\"id\":\"" + CODE_3 + "\",\"code\":\"migtest3\",\"name\":\"migtest3\"}'::jsonb)");
    insertInstance(1, "[\"" + CODE_1 + "\", \"" + CODE_3 + "\"]");
    insertInstance(2, "[\"" + CODE_1 + "\"]");
    insertInstance(3, "[\"" + CODE_2.toUpperCase() + "\"]");
    insertInstance(4, "[]");
    sql("DELETE FROM %s.instance_statistical_code");
    sql("DELETE FROM %s.statistical_code WHERE id = '" + CODE_3 + "'");
    submit(runner(), request(false, false));
    awaitState("COMPLETED");
    assertThat(joinCount(), is(3L));
    assertThat(stateLong("inserted_count"), is(3L));
    assertThat(stateLong("skipped_count"), is(1L));
    assertThat(stateLong("processed_count"), is(3L));
  }

  @Test
  public void instanceWithNilUuidIsMigrated() {
    insertInstance(0, "[\"" + CODE_1 + "\"]");
    submit(runner(), request(false, false));
    awaitState("COMPLETED");
    assertThat(joinCount(), is(1L));
  }

  @Test
  public void repeatedPostOnCompletedReturnsSameJobAndCreatesNoRecord() {
    insertInstance(1, "[\"" + CODE_1 + "\"]");
    var first = submit(runner(), request(false, false));
    awaitState("COMPLETED");
    var jobsBefore = jobCount();
    var second = submit(runner(), request(false, false));
    var third = submit(runner(), request(false, false));
    assertThat(second.getId(), is(first.getId()));
    assertThat(third.getId(), is(first.getId()));
    assertThat(jobCount(), is(jobsBefore));
  }

  @Test
  public void postOnStartedWithoutResumeReturnsCurrentJob() {
    var current = job();
    get(new AsyncMigrationJobRepository(getContext(), okapiHeaders()).save(current.getId(), current));
    sql("INSERT INTO %s.background_migration_state (name, status, job_id) VALUES ('" + NAME
      + "', 'STARTED', '" + current.getId() + "')");
    insertInstance(1, "[\"" + CODE_1 + "\"]");
    var jobsBefore = jobCount();
    var returned = submit(runner(), request(false, false));
    assertThat(returned.getId(), is(current.getId()));
    assertThat(jobCount(), is(jobsBefore));
    assertThat(joinCount(), is(0L));
  }

  @Test
  public void resumeContinuesFromCursor() {
    for (int i = 1; i <= 4; i++) {
      insertInstance(i, "[\"" + CODE_1 + "\"]");
    }
    sql("INSERT INTO %s.background_migration_state (name, status, last_processed_id, processed_count) VALUES ('"
      + NAME + "', 'FAILED', '" + instanceId(2) + "', 2)");
    var job = submit(runner(), request(true, false));
    awaitState("COMPLETED");
    awaitJobCompleted(job);
    assertThat(joinCount(), is(2L));
    assertThat(stateLong("processed_count"), is(4L));
  }

  @Test
  public void resumeFromStartedContinuesFromCursor() {
    for (int i = 1; i <= 4; i++) {
      insertInstance(i, "[\"" + CODE_1 + "\"]");
    }
    var old = job();
    get(new AsyncMigrationJobRepository(getContext(), okapiHeaders()).save(old.getId(), old));
    sql("INSERT INTO %s.background_migration_state (name, status, job_id, last_processed_id, processed_count) "
      + "VALUES ('" + NAME + "', 'STARTED', '" + old.getId() + "', '" + instanceId(2) + "', 2)");
    var job = submit(runner(), request(true, false));
    assertThat(job.getId().equals(old.getId()), is(false));
    awaitState("COMPLETED");
    awaitJobCompleted(job);
    assertThat(asyncMigration.getMigrationJob(old.getId()).getJobStatus(), is(CANCELLED));
    assertThat(stateRow().getUUID("job_id").toString(), is(job.getId()));
    assertThat(joinCount(), is(2L));
    assertThat(stateLong("processed_count"), is(4L));
  }

  @Test
  public void instanceUpdateDuringChunkDoesNotLeaveStaleRows() {
    insertInstance(1, "[\"" + CODE_1 + "\"]");
    sql("DELETE FROM %s.instance_statistical_code");
    var locked = new CompletableFuture<Void>();
    var tx = holdRowLock("UPDATE %s.instance SET jsonb = jsonb_set(jsonb, '{statisticalCodeIds}', '[\""
      + CODE_2 + "\"]') WHERE id = '" + instanceId(1) + "'", locked);
    get(locked);
    get(runner().submit(request(false, false), context()));
    get(tx);
    awaitState("COMPLETED");
    assertThat(staleRows(), is(0L));
    assertThat(joinCount(), is(1L));
  }

  @Test
  public void holdingsUpdateDuringMigrationKeepsJoinTableConsistent() {
    for (int i = 1; i <= 5; i++) {
      insertInstance(i, "[\"" + CODE_1 + "\"]");
    }
    var holdingsId = UUID.randomUUID();
    sql("INSERT INTO %s.holdings_record (id, jsonb, instanceid) VALUES ('" + holdingsId + "', "
      + "'{\"id\":\"" + holdingsId + "\",\"instanceId\":\"" + instanceId(3) + "\"}'::jsonb, '"
      + instanceId(3) + "')");
    sql("DELETE FROM %s.instance_statistical_code");
    var locked = new CompletableFuture<Void>();
    var tx = holdRowLock("UPDATE %s.holdings_record SET jsonb = jsonb || '{\"hrid\":\"h1\"}'::jsonb "
      + "WHERE id = '" + holdingsId + "'", locked);
    get(locked);
    get(runner().submit(request(false, false), context()));
    get(tx);
    awaitState("COMPLETED");
    assertThat(staleRows(), is(0L));
    assertThat(joinCount(), is(5L));
    sql("DELETE FROM %s.holdings_record WHERE id = '" + holdingsId + "'");
  }

  @Test
  public void restartResetsCounters() {
    insertInstance(1, "[\"" + CODE_1 + "\"]");
    insertInstance(2, "[\"" + CODE_1 + "\"]");
    sql("INSERT INTO %s.background_migration_state (name, status, last_processed_id, processed_count, "
      + "inserted_count, skipped_count) VALUES ('" + NAME + "', 'COMPLETED', '" + instanceId(2)
      + "', 50, 40, 10)");
    submit(runner(), request(false, true));
    await().atMost(20, SECONDS).until(() -> joinCount() == 2L);
    awaitState("COMPLETED");
    assertThat(stateLong("processed_count"), is(2L));
    assertThat(stateLong("inserted_count"), is(2L));
    assertThat(stateLong("skipped_count"), is(0L));
  }

  @Test
  public void parallelPostsCreateExactlyOneJob() {
    for (int i = 1; i <= 5; i++) {
      insertInstance(i, "[\"" + CODE_1 + "\"]");
    }
    var runner = runner();
    sql("DELETE FROM %s.instance_statistical_code");
    var jobsBefore = jobCount();
    var first = runner.submit(request(false, false), context()).toCompletionStage().toCompletableFuture();
    var second = runner.submit(request(false, false), context()).toCompletionStage().toCompletableFuture();
    get(CompletableFuture.allOf(first, second));
    assertThat(jobCount(), is(jobsBefore + 1));
    assertThat(first.join().getId(), is(second.join().getId()));
    awaitState("COMPLETED");
    assertThat(joinCount(), is(5L));
  }

  @Test
  public void cancelLeadsToCancelledInBothTables() {
    for (int i = 1; i <= 10; i++) {
      insertInstance(i, "[\"" + CODE_1 + "\"]");
    }
    var slow = new StateTableMigrationJobRunner(INSTANCE_STATISTICAL_CODES_CONFIG,
      new BackgroundMigrationSettings(1, 4, 400, 0));
    var job = submit(slow, request(false, false));
    asyncMigration.cancelMigrationJob(job.getId());
    await().atMost(20, SECONDS).until(() -> asyncMigration.getMigrationJob(job.getId()).getJobStatus() == CANCELLED);
    awaitState("CANCELLED");
  }

  @Test
  public void combinedMigrationsRequestIsRejected() {
    var request = new AsyncMigrationJobRequest().withMigrations(List.of(NAME, ITEM_ORDER_MIGRATION.getValue()));
    var response = get(getClient().post(migrationJobsUrl(""), request, TENANT_ID));
    assertThat(response.getStatusCode(), is(400));
  }

  private AsyncMigrationJobRequest request(boolean resume, boolean restart) {
    return new AsyncMigrationJobRequest().withMigrations(List.of(NAME)).withResume(resume).withRestart(restart);
  }

  private StateTableMigrationJobRunner runner() {
    return new StateTableMigrationJobRunner(INSTANCE_STATISTICAL_CODES_CONFIG,
      new BackgroundMigrationSettings(2, 4, 1, 0));
  }

  private AsyncMigrationJob submit(StateTableMigrationJobRunner runner, AsyncMigrationJobRequest request) {
    sql("DELETE FROM %s.instance_statistical_code");
    return get(runner.submit(request, context()));
  }

  private AsyncMigrationContext context() {
    return new AsyncMigrationContext(getContext(), okapiHeaders(), pg);
  }

  private static AsyncMigrationJob job() {
    return new AsyncMigrationJob()
      .withJobStatus(IN_PROGRESS)
      .withId(UUID.randomUUID().toString())
      .withMigrations(List.of(NAME))
      .withSubmittedDate(new Date());
  }

  private String instanceId(int n) {
    return String.format("00000000-0000-4000-8000-%012d", n);
  }

  private void insertInstance(int n, String codesJson) {
    var id = instanceId(n);
    sql("INSERT INTO %s.instance (id, jsonb) VALUES ('" + id + "', "
      + "'{\"id\":\"" + id + "\",\"title\":\"t" + n + "\",\"statisticalCodeIds\":" + codesJson + "}'::jsonb)");
  }

  private void awaitJobCompleted(AsyncMigrationJob job) {
    await().atMost(20, SECONDS).until(() -> asyncMigration.getMigrationJob(job.getId()).getJobStatus() == COMPLETED);
  }

  private void awaitState(String status) {
    await().atMost(20, SECONDS).until(() -> status.equals(stateString("status")));
  }

  private String stateString(String column) {
    var row = stateRow();
    return row == null ? null : row.getString(column);
  }

  private long stateLong(String column) {
    return stateRow().getLong(column);
  }

  private Row stateRow() {
    var rows = sql("SELECT * FROM %s.background_migration_state WHERE name = '" + NAME + "'");
    return rows.rowCount() == 0 ? null : rows.iterator().next();
  }

  private long staleRows() {
    return sql("SELECT count(*) AS c FROM %1$s.instance_statistical_code isc "
      + "LEFT JOIN %1$s.instance i ON i.id = isc.instance_id "
      + "WHERE i.id IS NULL OR NOT (i.jsonb->'statisticalCodeIds' ? isc.statistical_code_id::text)")
      .iterator().next().getLong("c");
  }

  /**
   * Runs the statement in a transaction that stays open for a while, so a migration chunk that reads the
   * locked row has to wait for the commit.
   */
  private Future<Void> holdRowLock(String template, CompletableFuture<Void> locked) {
    var statement = String.format(template, schema);
    return pg.withTrans(conn -> conn.execute(statement).compose(notUsed -> {
      locked.complete(null);
      Promise<Void> promise = Promise.promise();
      getVertx().setTimer(1500, id -> promise.complete());
      return promise.future();
    }));
  }

  private long joinCount() {
    return sql("SELECT count(*) AS c FROM %s.instance_statistical_code").iterator().next().getLong("c");
  }

  private long jobCount() {
    return sql("SELECT count(*) AS c FROM %s.async_migration_job").iterator().next().getLong("c");
  }

  private RowSet<Row> sql(String template) {
    return get(pg.execute(String.format(template, schema)));
  }

  private static Map<String, String> okapiHeaders() {
    return new CaseInsensitiveMap<>(Map.of(TENANT.toLowerCase(), TENANT_ID));
  }

  private static Context getContext() {
    return getVertx().getOrCreateContext();
  }
}
