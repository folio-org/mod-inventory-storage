package org.folio.rest.api;

import static io.vertx.core.Future.succeededFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.CANCELLED;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.COMPLETED;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.IN_PROGRESS;
import static org.folio.rest.persist.PgUtil.postgresClient;
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
import io.vertx.core.Context;
import org.folio.persist.InstanceRepository;
import org.folio.persist.IterationJobRepository;
import org.folio.rest.jaxrs.model.IterationJob;
import org.folio.rest.jaxrs.model.IterationJobParams;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.support.fixtures.InstanceIterationFixture;
import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.folio.rest.support.sql.TestRowStream;
import org.folio.services.iteration.IterationJobRunner;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class IterationJobRunnerTest extends TestBaseWithInventoryUtil {

  // use usual instance topic for testing purposes, because it doesn't matter
  // what the topic is for testing. this also prevents from adding changes to
  // FakeKafkaConsumer
  private static final String TEST_TOPIC = "inventory.instance";

  private static InstanceIterationFixture instanceIteration;

  private IterationJobRepository jobRepository;
  private InstanceRepository instanceRepository;
  private IterationJobRunner jobRunner;


  @BeforeClass
  public static void beforeClass() throws Exception {
    instanceIteration = new InstanceIterationFixture(client);
  }

  @Before
  public void setUp() {
    jobRepository = new IterationJobRepository(getContext(), okapiHeaders());
    instanceRepository = mock(InstanceRepository.class);

    var postgresClient = postgresClient(getContext(), okapiHeaders());
    jobRunner = new IterationJobRunner(new PostgresClientFuturized(postgresClient),
        jobRepository, instanceRepository, getContext(), okapiHeaders());

    // Make sure no events are left over from previous runs
    FakeKafkaConsumer.removeAllEvents();
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
    await().atMost(5, SECONDS)
      .until(FakeKafkaConsumer::getAllPublishedInstanceIdsCount, greaterThanOrEqualTo(numberOfRecords));
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
    return StorageTestSuite.getVertx().getOrCreateContext();
  }

}
