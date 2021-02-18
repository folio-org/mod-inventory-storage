package org.folio.rest.api;

import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static org.awaitility.Awaitility.await;
import static org.folio.rest.api.InstanceStorageTest.smallAngryPlanet;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.CANCELLED;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.COMPLETED;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getLastReindexEvent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.BeforeClass;
import org.junit.Test;

public class ReindexInstanceAPITest extends TestBaseWithInventoryUtil {
  private static final List<String> allInstanceIds = new ArrayList<>();

  @BeforeClass
  public static void createInstances() {
    var instances = IntStream.range(0, 2000)
      .mapToObj(notUsed -> smallAngryPlanet(randomUUID()))
      .collect(toList());

    instancesStorageSyncClient
      .create(new JsonObject().put("instances", new JsonArray(instances)));

    instances.forEach(instance -> allInstanceIds.add(instance.getString("id")));
  }

  @Test
  public void canReindexInstances() {
    var jobId = instanceReindex.submitReindex().getId();

    await().until(() -> instanceReindex.getReindexJob(jobId).getJobStatus() == COMPLETED);

    var reindexJob = instanceReindex.getReindexJob(jobId);

    assertThat(reindexJob.getPublished(), is(allInstanceIds.size()));
    assertThat(reindexJob.getJobStatus(), is(COMPLETED));
    assertThat(reindexJob.getSubmittedDate(), notNullValue());

    await().untilAsserted(() -> {
      var instanceId = allInstanceIds.get(0);
      var lastInstanceEvent = getLastReindexEvent(instanceId);
      assertThat(lastInstanceEvent.getPayload().getString("type"), is("REINDEX"));
      assertThat(lastInstanceEvent.getPayload().getString("id"), is(instanceId));
      assertThat(lastInstanceEvent.getPayload().getString("tenant"), is(TENANT_ID));
    });
  }

  @Test
  public void canCancelReindexing() {
    var jobId = instanceReindex.submitReindex().getId();

    instanceReindex.cancelReindexJob(jobId);

    await().until(() -> instanceReindex.getReindexJob(jobId).getJobStatus() == CANCELLED);

    var reindexJob = instanceReindex.getReindexJob(jobId);

    assertThat(reindexJob.getJobStatus(), is(CANCELLED));
    assertThat(reindexJob.getPublished(), lessThan(allInstanceIds.size()));
    assertThat(reindexJob.getSubmittedDate(), notNullValue());
  }
}
