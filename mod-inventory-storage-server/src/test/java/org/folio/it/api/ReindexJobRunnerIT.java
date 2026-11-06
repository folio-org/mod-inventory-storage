package org.folio.it.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.folio.HttpStatus.SC_NO_CONTENT;
import static org.folio.InventoryKafkaTopic.INSTANCE;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.IDS_PUBLISHED;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.ID_PUBLISHING_CANCELLED;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.IN_PROGRESS;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.support.sql.StreamingTransactions.streamRowsInTransaction;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.Mockito.spy;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.dataimport.testsupport.vertx.VertxTestUtil;
import org.folio.it.BaseIntegrationTest;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.persist.ReindexJobRepository;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.ReindexJob;
import org.folio.rest.jaxrs.model.ReindexJobs;
import org.folio.services.domainevent.CommonDomainEventPublisher;
import org.folio.services.reindex.ReindexJobRunner;
import org.folio.support.DomainEventPublisherFactories;
import org.folio.support.ResourcePaths;
import org.folio.support.messages.InstanceEventMessageChecks;
import org.folio.support.sql.TestRowStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReindexJobRunnerIT extends BaseIntegrationTest {

  private final InstanceEventMessageChecks eventChecks = new InstanceEventMessageChecks(KAFKA_CONSUMER, vertxUrl());

  @Test
  @DisplayName("should reindex all instances and publish a message for each")
  void shouldReindexInstances_andPublishMessageForEach(Vertx vertx) {
    var numberOfRecords = 1100;
    var rowStream = new TestRowStream(numberOfRecords);
    var reindexJob = instanceReindexJob();
    postReindexJob(reindexJob);

    var repository = repositoryStreaming(vertx, rowStream);
    VertxTestUtil.await(repository.save(reindexJob.getId(), reindexJob, tenantId(okapiHeaders())));

    jobRunner(vertx, repository).startReindex(reindexJob);

    await().until(() -> getReindexJob(reindexJob.getId()).getJobStatus() == IDS_PUBLISHED);

    var job = getReindexJob(reindexJob.getId());

    assertThat(job.getPublished()).isEqualTo(numberOfRecords);
    assertThat(job.getJobStatus()).isEqualTo(IDS_PUBLISHED);
    assertThat(job.getSubmittedDate()).isNotNull();

    // Should be a single reindex message for each instance ID generated in the row stream.
    // The numbers should match exactly, but intermittently, the published id count is
    // greater than the number of records - no one has been able to figure out why.
    eventChecks.countOfAllPublishedInstancesIs(greaterThanOrEqualTo(numberOfRecords));
  }

  @Test
  @DisplayName("should get all instance reindex jobs")
  void shouldGetAllInstanceReindexJobs(Vertx vertx) {
    var numberOfRecords = 2;
    var rowStream = new TestRowStream(numberOfRecords);
    var reindexJob = instanceReindexJob();
    postReindexJob(reindexJob);

    var repository = repositoryStreaming(vertx, rowStream);
    VertxTestUtil.await(repository.save(reindexJob.getId(), reindexJob, tenantId(okapiHeaders())));

    jobRunner(vertx, repository).startReindex(reindexJob);

    await().until(() -> getReindexJob(reindexJob.getId()).getJobStatus() == IDS_PUBLISHED);

    var jobs = getReindexJobs();

    assertThat(jobs.getReindexJobs().getFirst().getJobStatus()).isEqualTo(IDS_PUBLISHED);
    assertThat(jobs.getTotalRecords()).isNotNull();

    eventChecks.countOfAllPublishedInstancesIs(greaterThanOrEqualTo(numberOfRecords));
  }

  @Test
  @DisplayName("should start an instance reindex job")
  void shouldStartInstanceReindex() {
    var job = postReindexJob(instanceReindexJob());

    assertThat(job).isNotNull();
    assertThat(job.getId()).isNotNull();
  }

  @Test
  @DisplayName("should cancel an in-progress reindex")
  void shouldCancelReindex_whenInProgress(Vertx vertx) {
    var rowStream = new TestRowStream(10_000_000);
    var reindexJob = instanceReindexJob();

    var repository = repositoryStreaming(vertx, rowStream);
    VertxTestUtil.await(repository.save(reindexJob.getId(), reindexJob, tenantId(okapiHeaders())));

    jobRunner(vertx, repository).startReindex(reindexJob);

    cancelReindexJob(reindexJob.getId());

    await().until(() -> getReindexJob(reindexJob.getId()).getJobStatus() == ID_PUBLISHING_CANCELLED);

    var job = getReindexJob(reindexJob.getId());

    assertThat(job.getJobStatus()).isEqualTo(ID_PUBLISHING_CANCELLED);
    assertThat(job.getPublished()).isGreaterThanOrEqualTo(1000);
  }

  private static ReindexJobRepository repositoryStreaming(Vertx vertx, TestRowStream rowStream) {
    var repository = spy(repository(vertx));

    streamRowsInTransaction(repository, vertx, rowStream);

    return repository;
  }

  private static ReindexJobRunner jobRunner(Vertx vertx, ReindexJobRepository repository) {
    CommonDomainEventPublisher<Instance> eventPublisher =
      DomainEventPublisherFactories.create(getContext(vertx)).publisher(INSTANCE);

    return new ReindexJobRunner(repository, getContext(vertx), eventPublisher, okapiHeaders());
  }

  private static ReindexJobRepository repository(Vertx vertx) {
    return new ReindexJobRepository(getContext(vertx));
  }

  private static ReindexJob getReindexJob(String id) {
    return VertxTestUtil.await(doGet(client, ResourcePaths.INSTANCE_REINDEX + "/" + id))
      .jsonBody().mapTo(ReindexJob.class);
  }

  private static ReindexJobs getReindexJobs() {
    return VertxTestUtil.await(doGet(client, ResourcePaths.INSTANCE_REINDEX + "?query=published%3E%3D0"))
      .jsonBody().mapTo(ReindexJobs.class);
  }

  private static ReindexJob postReindexJob(ReindexJob job) {
    return VertxTestUtil.await(doPost(client, ResourcePaths.INSTANCE_REINDEX, pojo2JsonObject(job)))
      .jsonBody().mapTo(ReindexJob.class);
  }

  private static void cancelReindexJob(String id) {
    var response = VertxTestUtil.await(doDelete(client, ResourcePaths.INSTANCE_REINDEX + "/" + id));
    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
  }

  private static ReindexJob instanceReindexJob() {
    return new ReindexJob()
      .withJobStatus(IN_PROGRESS)
      .withResourceName(ReindexJob.ResourceName.INSTANCE)
      .withId(UUID.randomUUID().toString())
      .withSubmittedDate(new Date());
  }

  private static Map<String, String> okapiHeaders() {
    return new CaseInsensitiveMap<>(Map.of(XOkapiHeaders.TENANT.toLowerCase(), TENANT_ID));
  }

  private static Context getContext(Vertx vertx) {
    return vertx.getOrCreateContext();
  }
}
