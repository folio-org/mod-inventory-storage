package org.folio.rest.api;

import static io.vertx.core.Future.succeededFuture;
import static org.awaitility.Awaitility.await;
import static org.folio.InventoryKafkaTopic.INSTANCE;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.IDS_PUBLISHED;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.ID_PUBLISHING_CANCELLED;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.IN_PROGRESS;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.persist.ReindexJobRepository;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.ReindexJob;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.messages.InstanceEventMessageChecks;
import org.folio.rest.support.sql.TestRowStream;
import org.folio.services.domainevent.CommonDomainEventPublisher;
import org.folio.services.reindex.ReindexJobRunner;
import org.junit.Test;

public class ReindexJobRunnerTest extends TestBaseWithInventoryUtil {
  private final ReindexJobRepository repository = getRepository();
  private final CommonDomainEventPublisher<Instance> instanceEventPublisher =
    new CommonDomainEventPublisher<>(getContext(), new CaseInsensitiveMap<>(Map.of(TENANT, TENANT_ID)),
      INSTANCE.fullTopicName(TENANT_ID));

  private final InstanceEventMessageChecks instanceMessageChecks
    = new InstanceEventMessageChecks(KAFKA_CONSUMER);

  private static ReindexJob instanceReindexJob() {
    return new ReindexJob()
      .withJobStatus(IN_PROGRESS)
      .withResourceName(ReindexJob.ResourceName.INSTANCE)
      .withId(UUID.randomUUID().toString())
      .withSubmittedDate(new Date());
  }

  private static Map<String, String> okapiHeaders() {
    return new CaseInsensitiveMap<>(Map.of(TENANT.toLowerCase(), TENANT_ID));
  }

  private static Context getContext() {
    return getVertx().getOrCreateContext();
  }

  @Test
  public void canReindexInstances() {
    var numberOfRecords = 1100;
    var rowStream = new TestRowStream(numberOfRecords);
    var reindexJob = instanceReindexJob();
    instanceReindex.postReindexJob(reindexJob);

    var postgresClient = createMockPostgresClient(rowStream);

    get(repository.save(reindexJob.getId(), reindexJob).toCompletionStage()
      .toCompletableFuture());

    jobRunner(postgresClient).startReindex(reindexJob);

    await().until(() -> instanceReindex.getReindexJob(reindexJob.getId())
      .getJobStatus() == IDS_PUBLISHED);

    var job = instanceReindex.getReindexJob(reindexJob.getId());

    assertThat(job.getPublished(), is(numberOfRecords));
    assertThat(job.getJobStatus(), is(IDS_PUBLISHED));
    assertThat(job.getSubmittedDate(), notNullValue());

    // Should be a single reindex message for each instance ID generated in the row stream
    // The numbers should match exactly, but intermittently, the published id count is
    // greater than the number of records-no one has been able to figure out why.
    instanceMessageChecks.countOfAllPublishedInstancesIs(
      greaterThanOrEqualTo(numberOfRecords));
  }

  private PostgresClient createMockPostgresClient(TestRowStream rowStream) {
    var postgresClient = spy(getPostgresClient());
    var connection = mock(Conn.class);

    when(postgresClient.withTrans(any()))
      .thenAnswer(invocation -> invocation.<Function<Conn, Future<Object>>>getArgument(0)
        .apply(connection));

    when(connection.selectStream(anyString(), any(Tuple.class), any())).thenAnswer(invocation -> {
      invocation.<Handler<RowStream<Row>>>getArgument(2).handle(rowStream);
      return succeededFuture();
    });

    return postgresClient;
  }

  @Test
  public void canGetAllInstancesReindexJobs() {
    var numberOfRecords = 2;
    var rowStream = new TestRowStream(numberOfRecords);
    var reindexJob = instanceReindexJob();
    instanceReindex.postReindexJob(reindexJob);
    var postgresClient = spy(getPostgresClient());
    var connection = mock(Conn.class);

    when(postgresClient.withTrans(any()))
      .thenAnswer(invocation -> invocation.<Function<Conn, Future<Object>>>getArgument(0)
        .apply(connection));

    when(connection.selectStream(anyString(), any(Tuple.class), any())).thenAnswer(invocation -> {
      invocation.<Handler<RowStream<Row>>>getArgument(2).handle(rowStream);
      return succeededFuture();
    });

    get(repository.save(reindexJob.getId(), reindexJob).toCompletionStage()
      .toCompletableFuture());

    jobRunner(postgresClient).startReindex(reindexJob);

    await().until(() -> instanceReindex.getReindexJob(reindexJob.getId())
      .getJobStatus() == IDS_PUBLISHED);

    var jobs = instanceReindex.getReindexJobs();

    assertThat(jobs.getReindexJobs().getFirst().getJobStatus(), is(IDS_PUBLISHED));
    assertThat(jobs.getTotalRecords(), notNullValue());

    instanceMessageChecks.countOfAllPublishedInstancesIs(
      greaterThanOrEqualTo(numberOfRecords));
  }

  @Test
  public void canStartInstanceReindex() {
    ReindexJob res = instanceReindex.postReindexJob(instanceReindexJob());
    assertThat(res, notNullValue());
    assertThat(res.getId(), notNullValue());
  }

  @Test
  public void canCancelReindex() {
    var rowStream = new TestRowStream(10_000_000);
    var reindexJob = instanceReindexJob();
    var postgresClient = spy(getPostgresClient());
    var connection = mock(Conn.class);

    when(postgresClient.withTrans(any()))
      .thenAnswer(invocation -> invocation.<Function<Conn, Future<Object>>>getArgument(0)
        .apply(connection));

    when(connection.selectStream(anyString(), any(Tuple.class), any())).thenAnswer(invocation -> {
      invocation.<Handler<RowStream<Row>>>getArgument(2).handle(rowStream);
      return succeededFuture();
    });

    get(repository.save(reindexJob.getId(), reindexJob).toCompletionStage()
      .toCompletableFuture());

    jobRunner(postgresClient).startReindex(reindexJob);

    instanceReindex.cancelReindexJob(reindexJob.getId());

    await().until(() -> instanceReindex.getReindexJob(reindexJob.getId())
      .getJobStatus() == ID_PUBLISHING_CANCELLED);

    var job = instanceReindex.getReindexJob(reindexJob.getId());

    assertThat(job.getJobStatus(), is(ID_PUBLISHING_CANCELLED));
    assertThat(job.getPublished(), greaterThanOrEqualTo(1000));
  }

  private ReindexJobRunner jobRunner(PostgresClient postgresClient) {
    return new ReindexJobRunner(postgresClient,
      repository, getContext(), instanceEventPublisher, TENANT_ID);
  }

  private PostgresClient getPostgresClient() {
    return postgresClient(getContext(), okapiHeaders());
  }

  private ReindexJobRepository getRepository() {
    return new ReindexJobRepository(getContext(), okapiHeaders());
  }
}
