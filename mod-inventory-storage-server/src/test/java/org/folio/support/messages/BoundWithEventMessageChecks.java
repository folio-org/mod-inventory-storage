package org.folio.support.messages;

import static org.folio.support.AwaitConfiguration.awaitAtMost;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.allOf;

import io.vertx.core.json.JsonObject;
import java.net.URL;
import org.folio.support.kafka.FakeKafkaConsumer;
import org.folio.support.messages.matchers.EventMessageMatchers;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;

public class BoundWithEventMessageChecks {
  private final EventMessageMatchers eventMessageMatchers;
  private final FakeKafkaConsumer kafkaConsumer;

  public BoundWithEventMessageChecks(FakeKafkaConsumer kafkaConsumer, URL expectedUrl) {
    this.kafkaConsumer = kafkaConsumer;
    this.eventMessageMatchers = new EventMessageMatchers(TENANT_ID, expectedUrl);
  }

  public void createdMessagePublished(JsonObject boundWith, String instanceId) {

    // Bound With messages are published with the instance ID as the key
    // and that property is not part of the record, so must be provided separately
    awaitAtMost().until(() -> kafkaConsumer.getMessagesForBoundWith(instanceId),
      eventMessageMatchers.hasCreateEventMessageFor(
        addInstanceIdToBoundWith(boundWith, instanceId)));
  }

  public void updatedMessagePublished(JsonObject oldBoundWith,
                                      JsonObject newBoundWith, String oldInstanceId, String newInstanceId) {

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForBoundWith(newInstanceId),
      hasBoundWithUpdateMessageFor(oldBoundWith, newBoundWith, oldInstanceId, newInstanceId));
  }

  @NotNull
  private Matcher<Iterable<? super EventMessage>> hasBoundWithUpdateMessageFor(
    JsonObject oldBoundWith, JsonObject newBoundWith, String oldInstanceId,
    String newInstanceId) {

    JsonObject newRepresentation = addInstanceIdToBoundWith(newBoundWith, newInstanceId);

    return CoreMatchers.hasItem(allOf(
      eventMessageMatchers.isUpdateEvent(),
      eventMessageMatchers.isForTenant(),
      eventMessageMatchers.hasHeaders(),
      eventMessageMatchers.hasNewRepresentation(newRepresentation)));
  }

  private JsonObject addInstanceIdToBoundWith(JsonObject boundWith, String instanceId) {
    // Event for bound with has an extra 'instanceId' property for
    // old/new object, the property does not exist in schema,
    // so we have to add it manually
    return boundWith.copy().put("instanceId", instanceId);
  }
}
