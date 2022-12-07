package org.folio.rest.support.messages;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getMessagesForInstance;
import static org.folio.rest.support.matchers.DomainEventAssertions.await;
import static org.folio.services.domainevent.CommonDomainEventPublisher.NULL_INSTANCE_ID;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.awaitility.core.ConditionFactory;
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
    final String instanceId = instance.getString("id");

    await().until(() -> getMessagesForInstance(instanceId),
      eventMessageMatchers.hasCreateEventMessageFor(instance));
  }

  public static void instanceCreatedMessagesPublished(List<JsonObject> instances) {
    final var instanceIds = instances.stream()
      .map(instance -> instance.getString("id"))
      .collect(Collectors.toList());

    // This is a compromise because checking a large number of messages in
    // one go seems to cause instability in the Jenkins builds
    await().until(() -> FakeKafkaConsumer.getMessagesForInstances(instanceIds),
      hasSize(instances.size()));

    instances.forEach(instance -> {
      assertThat(FakeKafkaConsumer.getMessagesForInstances(instanceIds),
        eventMessageMatchers.hasCreateEventMessageFor(instance));
    });
  }

  public static void instancedUpdatedMessagePublished(JsonObject oldInstance, JsonObject newInstance) {
    final String instanceId = oldInstance.getString("id");

    await().until(() -> getMessagesForInstance(instanceId),
      eventMessageMatchers.hasUpdateEventMessageFor(oldInstance, newInstance));
  }

  public static void noInstanceUpdatedMessagePublished(String instanceId) {
    awaitDuring(1, SECONDS)
      .until(() -> getMessagesForInstance(instanceId),
        eventMessageMatchers.hasNoUpdateEventMessage());
  }

  public static void instanceDeletedMessagePublished(JsonObject instance) {
    final String instanceId = instance.getString("id");

    await().until(() -> getMessagesForInstance(instanceId),
      eventMessageMatchers.hasDeleteEventMessageFor(instance));
  }

  public static void noInstanceDeletedMessagePublished(String instanceId) {
    awaitDuring(1, SECONDS)
      .until(() -> getMessagesForInstance(instanceId),
        eventMessageMatchers.hasNoDeleteEventMessage());
  }

  public static void deleteAllEventForInstancesPublished() {
    await()
      .until(() -> getMessagesForInstance(NULL_INSTANCE_ID),
        eventMessageMatchers.hasDeleteAllEventMessage());
  }

  private static ConditionFactory awaitDuring(int timeout, TimeUnit unit) {
    return await().atMost(timeout, unit).during(timeout, unit);
  }
}
