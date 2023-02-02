package org.folio.rest.api;

import static io.vertx.core.Future.succeededFuture;
import static org.awaitility.Awaitility.await;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.CANCELLED;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.COMPLETED;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.IN_PROGRESS;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.persist.InstanceInternalRepository;
import org.folio.persist.IterationJobRepository;
import org.folio.rest.jaxrs.model.IterationJob;
import org.folio.rest.jaxrs.model.IterationJobParams;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.support.fixtures.InstanceIterationFixture;
import org.folio.rest.support.messages.InstanceEventMessageChecks;
import org.folio.rest.support.sql.TestRowStream;
import org.folio.services.iteration.IterationJobRunner;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import io.vertx.core.Context;
import lombok.SneakyThrows;

public class IterationJobRunnerTest extends TestBaseWithInventoryUtil {

  // use usual instance topic for testing purposes, because it doesn't matter
  // what the topic is for testing. this also prevents from adding changes to
  // FakeKafkaConsumer
  private static final String TEST_TOPIC = "inventory.instance";

  private static InstanceIterationFixture instanceIteration;

  private IterationJobRepository jobRepository;
  private InstanceInternalRepository instanceRepository;
  private IterationJobRunner jobRunner;

  private final InstanceEventMessageChecks instanceMessageChecks
    = new InstanceEventMessageChecks(kafkaConsumer);;

  @BeforeClass
  public static void beforeClass() {
    TestBase.beforeAll();

    instanceIteration = new InstanceIterationFixture(getClient());
  }

  @SneakyThrows
  @Before
  public void beforeEach() {
    jobRepository = new IterationJobRepository(getContext(), okapiHeaders());
    instanceRepository = mock(InstanceInternalRepository.class);

    var postgresClient = postgresClient(getContext(), okapiHeaders());
    jobRunner = new IterationJobRunner(new PostgresClientFuturized(postgresClient),
        jobRepository, instanceRepository, getContext(), okapiHeaders());

    removeAllEvents();
  }

  @Test
  public void canIterateInstances() {
    var numberOfRecords = 1100;
    var rowStream = new TestRowStream(numberOfRecords);
    var iterationJob = iterationJob();

    when(instanceRepository.getAllIds(any()))
        .thenReturn(succeededFuture(rowStream));

    get(jobRepository.save(iterationJob.getId(), iterationJob));

    jobRunner.startIteration(iterationJob);

    await().until(() -> instanceIteration.getIterationJob(iterationJob.getId())
      .getJobStatus() == COMPLETED);

    var job = instanceIteration.getIterationJob(iterationJob.getId());

    assertThat(job.getMessagesPublished(), is(numberOfRecords));
    assertThat(job.getJobStatus(), is(COMPLETED));
    assertThat(job.getSubmittedDate(), notNullValue());

    // Should be a single iteration message for each instance ID generated in the row stream
    instanceMessageChecks.countOfAllPublishedInstancesIs(
      greaterThanOrEqualTo(numberOfRecords));
  }

  @Test
  public void canCancelIteration() {
    var rowStream = new TestRowStream(10_000_000);
    var iterationJob = iterationJob();

    when(instanceRepository.getAllIds(any()))
        .thenReturn(succeededFuture(rowStream));

    get(jobRepository.save(iterationJob.getId(), iterationJob));

    jobRunner.startIteration(iterationJob);

    await().until(() -> instanceIteration.getIterationJob(iterationJob.getId())
      .getMessagesPublished() >= 1000);

    instanceIteration.cancelIterationJob(iterationJob.getId());

    await().until(() -> instanceIteration.getIterationJob(iterationJob.getId())
      .getJobStatus() == CANCELLED);

    var job = instanceIteration.getIterationJob(iterationJob.getId());

    assertThat(job.getJobStatus(), is(CANCELLED));
    assertThat(job.getMessagesPublished(), greaterThanOrEqualTo(1000));
  }

  private static IterationJob iterationJob() {
    return new IterationJob()
      .withId(UUID.randomUUID().toString())
      .withJobStatus(IN_PROGRESS)
      .withSubmittedDate(new Date())
      .withJobParams(
        new IterationJobParams().withTopicName(TEST_TOPIC)
      );
  }

  private static Map<String, String> okapiHeaders() {
    return new CaseInsensitiveMap<>(Map.of(TENANT.toLowerCase(), TENANT_ID));
  }

  private static Context getContext() {
    return getVertx().getOrCreateContext();
  }

}
