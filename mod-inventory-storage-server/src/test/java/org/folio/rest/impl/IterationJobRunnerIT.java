package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.CANCELLED;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.COMPLETED;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.IN_PROGRESS;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.persist.InstanceRepository;
import org.folio.persist.IterationJobRepository;
import org.folio.rest.jaxrs.model.IterationJob;
import org.folio.rest.jaxrs.model.IterationJobParams;
import org.folio.rest.support.messages.InstanceEventMessageChecks;
import org.folio.rest.support.sql.TestRowStream;
import org.folio.services.iteration.IterationJobRunner;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

class IterationJobRunnerIT extends BaseIntegrationTest {

  // use the usual instance topic for testing purposes, because it doesn't matter what the
  // topic is for testing, and this also prevents adding changes to FakeKafkaConsumer
  private static final String TEST_TOPIC = "inventory.instance";

  private final InstanceEventMessageChecks eventChecks = new InstanceEventMessageChecks(KAFKA_CONSUMER);

  @Test
  void canIterateInstances(Vertx vertx) {
    var numberOfRecords = 1100;
    var rowStream = new TestRowStream(numberOfRecords);
    var iterationJob = iterationJob();
    var instanceRepository = mock(InstanceRepository.class);
    when(instanceRepository.getAllIds(any())).thenReturn(succeededFuture(rowStream));
    var jobRepository = new IterationJobRepository(getContext(vertx), okapiHeaders());
    var jobRunner = new IterationJobRunner(postgresClient(getContext(vertx), okapiHeaders()),
      jobRepository, instanceRepository, getContext(vertx), okapiHeaders());

    get(jobRepository.save(iterationJob.getId(), iterationJob));

    jobRunner.startIteration(iterationJob);

    await().until(() -> getIterationJob(iterationJob.getId()).getJobStatus() == COMPLETED);

    var job = getIterationJob(iterationJob.getId());

    assertThat(job.getMessagesPublished()).isEqualTo(numberOfRecords);
    assertThat(job.getJobStatus()).isEqualTo(COMPLETED);
    assertThat(job.getSubmittedDate()).isNotNull();

    // Should be a single iteration message for each instance ID generated in the row stream
    eventChecks.countOfAllPublishedInstancesIs(Matchers.greaterThanOrEqualTo(numberOfRecords));
  }

  @Test
  void canCancelIteration(Vertx vertx) {
    var rowStream = new TestRowStream(10_000_000);
    var iterationJob = iterationJob();
    var instanceRepository = mock(InstanceRepository.class);
    when(instanceRepository.getAllIds(any())).thenReturn(succeededFuture(rowStream));
    var jobRepository = new IterationJobRepository(getContext(vertx), okapiHeaders());
    var jobRunner = new IterationJobRunner(postgresClient(getContext(vertx), okapiHeaders()),
      jobRepository, instanceRepository, getContext(vertx), okapiHeaders());

    get(jobRepository.save(iterationJob.getId(), iterationJob));

    jobRunner.startIteration(iterationJob);

    await().until(() -> getIterationJob(iterationJob.getId()).getMessagesPublished() >= 1000);

    cancelIterationJob(iterationJob.getId());

    await().until(() -> getIterationJob(iterationJob.getId()).getJobStatus() == CANCELLED);

    var job = getIterationJob(iterationJob.getId());

    assertThat(job.getJobStatus()).isEqualTo(CANCELLED);
    assertThat(job.getMessagesPublished()).isGreaterThanOrEqualTo(1000);
  }

  private static IterationJob getIterationJob(String id) {
    return get(doGet(client, ResourcePaths.INSTANCE_ITERATION + "/" + id)).jsonBody().mapTo(IterationJob.class);
  }

  private static void cancelIterationJob(String id) {
    var response = get(doDelete(client, ResourcePaths.INSTANCE_ITERATION + "/" + id));
    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
  }

  private static IterationJob iterationJob() {
    return new IterationJob()
      .withId(UUID.randomUUID().toString())
      .withJobStatus(IN_PROGRESS)
      .withSubmittedDate(new Date())
      .withJobParams(new IterationJobParams().withTopicName(TEST_TOPIC));
  }

  private static Map<String, String> okapiHeaders() {
    return new CaseInsensitiveMap<>(Map.of(XOkapiHeaders.TENANT.toLowerCase(), TENANT_ID));
  }

  private static Context getContext(Vertx vertx) {
    return vertx.getOrCreateContext();
  }
}
