package org.folio.rest.support.messages;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.rest.support.AwaitConfiguration.awaitDuring;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getMessagesForInstance;
import static org.folio.services.domainevent.CommonDomainEventPublisher.NULL_ID;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.stream.Collectors;

import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.folio.rest.support.messages.matchers.EventMessageMatchers;

import io.vertx.core.json.JsonObject;

public class InstanceEventMessageChecks {
  private static final EventMessageMatchers eventMessageMatchers
    = new EventMessageMatchers(TENANT_ID, vertxUrl(""));

  private InstanceEventMessageChecks() { }

  public static void noInstanceMessagesPublished(String instanceId) {
    awaitDuring(1, SECONDS)
      .until(() -> getMessagesForInstance(instanceId), is(empty()));
  }

  public static void instanceCreatedMessagePublished(JsonObject instance) {
    final String instanceId = getId(instance);

    awaitAtMost().until(() -> getMessagesForInstance(instanceId),
      eventMessageMatchers.hasCreateEventMessageFor(instance));
  }

  public static void instanceCreatedMessagesPublished(List<JsonObject> instances) {
    final var instanceIds = instances.stream()
      .map(InstanceEventMessageChecks::getId)
      .collect(Collectors.toList());

    // This is a compromise because checking a large number of messages in
    // one go seems to cause instability in the Jenkins builds
    awaitAtMost().until(() -> FakeKafkaConsumer.getMessagesForInstances(instanceIds),
      hasSize(instances.size()));

    instances.forEach(instance -> {
      assertThat(FakeKafkaConsumer.getMessagesForInstances(instanceIds),
        eventMessageMatchers.hasCreateEventMessageFor(instance));
    });
  }

  public static void instancedUpdatedMessagePublished(JsonObject oldInstance, JsonObject newInstance) {
    final String instanceId = getId(oldInstance);

    awaitAtMost().until(() -> getMessagesForInstance(instanceId),
      eventMessageMatchers.hasUpdateEventMessageFor(oldInstance, newInstance));
  }

  public static void noInstanceUpdatedMessagePublished(String instanceId) {
    awaitDuring(1, SECONDS)
      .until(() -> getMessagesForInstance(instanceId),
        eventMessageMatchers.hasNoUpdateEventMessage());
  }

  public static void instanceDeletedMessagePublished(JsonObject instance) {
    final String instanceId = getId(instance);

    awaitAtMost().until(() -> getMessagesForInstance(instanceId),
      eventMessageMatchers.hasDeleteEventMessageFor(instance));
  }

  public static void noInstanceDeletedMessagePublished(String instanceId) {
    awaitDuring(1, SECONDS)
      .until(() -> getMessagesForInstance(instanceId),
        eventMessageMatchers.hasNoDeleteEventMessage());
  }

  public static void allInstancesDeletedMessagePublished() {
    awaitAtMost()
      .until(() -> getMessagesForInstance(NULL_ID),
        eventMessageMatchers.hasDeleteAllEventMessage());
  }

  private static String getId(JsonObject json) {
    return json.getString("id");
  }
}
