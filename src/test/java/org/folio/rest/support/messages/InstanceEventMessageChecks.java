package org.folio.rest.support.messages;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.rest.support.AwaitConfiguration.awaitDuring;
import static org.folio.services.domainevent.CommonDomainEventPublisher.NULL_ID;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;

import io.vertx.core.json.JsonObject;
import java.util.List;
import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.folio.rest.support.messages.matchers.EventMessageMatchers;
import org.hamcrest.Matcher;

public class InstanceEventMessageChecks {
  private static final EventMessageMatchers EVENT_MESSAGE_MATCHERS
    = new EventMessageMatchers(TENANT_ID, vertxUrl(""));
  private final FakeKafkaConsumer kafkaConsumer;

  public InstanceEventMessageChecks(FakeKafkaConsumer kafkaConsumer) {
    this.kafkaConsumer = kafkaConsumer;
  }

  private static String getId(JsonObject json) {
    return json.getString("id");
  }

  public void noMessagesPublished(String instanceId) {
    awaitDuring(1, SECONDS)
      .until(() -> kafkaConsumer.getMessagesForInstance(instanceId), is(empty()));
  }

  public void createdMessagePublished(JsonObject instance) {
    final String instanceId = getId(instance);

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForInstance(instanceId),
      EVENT_MESSAGE_MATCHERS.hasCreateEventMessageFor(instance));
  }

  public void createdMessagePublished(String instanceId) {
    awaitAtMost().until(() -> kafkaConsumer.getMessagesForInstance(instanceId),
      hasItem(hasProperty("type", is(("CREATE")))));
  }

  public void createdMessagesPublished(List<JsonObject> instances) {
    final var instanceIds = instances.stream()
      .map(InstanceEventMessageChecks::getId)
      .toList();

    // This is a compromise because checking a large number of messages in
    // one go seems to cause instability in the Jenkins builds
    awaitAtMost().until(() -> kafkaConsumer.getMessagesForInstances(instanceIds),
      hasSize(instances.size()));

    instances.forEach(instance -> assertThat(kafkaConsumer.getMessagesForInstances(instanceIds),
      EVENT_MESSAGE_MATCHERS.hasCreateEventMessageFor(instance)));
  }

  public void updatedMessagePublished(JsonObject oldInstance, JsonObject newInstance) {
    final String instanceId = getId(oldInstance);

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForInstance(instanceId),
      EVENT_MESSAGE_MATCHERS.hasUpdateEventMessageFor(oldInstance, newInstance));
  }

  public void noUpdatedMessagePublished(String instanceId) {
    awaitDuring(1, SECONDS)
      .until(() -> kafkaConsumer.getMessagesForInstance(instanceId),
        EVENT_MESSAGE_MATCHERS.hasNoUpdateEventMessage());
  }

  public void deletedMessagePublished(JsonObject instance) {
    final String instanceId = getId(instance);

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForInstance(instanceId),
      EVENT_MESSAGE_MATCHERS.hasDeleteEventMessageFor(instance));
  }

  public void noDeletedMessagePublished(String instanceId) {
    awaitDuring(1, SECONDS)
      .until(() -> kafkaConsumer.getMessagesForInstance(instanceId),
        EVENT_MESSAGE_MATCHERS.hasNoDeleteEventMessage());
  }

  public void allInstancesDeletedMessagePublished() {
    awaitAtMost()
      .until(() -> kafkaConsumer.getMessagesForInstance(NULL_ID),
        EVENT_MESSAGE_MATCHERS.hasDeleteAllEventMessage());
  }

  public void countOfAllPublishedInstancesIs(Matcher<Integer> matcher) {
    await().atMost(15, SECONDS)
      .until(kafkaConsumer::getAllPublishedInstanceIdsCount, matcher);
  }
}
