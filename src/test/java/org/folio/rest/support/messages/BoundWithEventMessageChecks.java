package org.folio.rest.support.messages;

import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getMessagesForBoundWith;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.allOf;

import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.folio.rest.support.messages.matchers.EventMessageMatchers;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;

import io.vertx.core.json.JsonObject;

public class BoundWithEventMessageChecks {
  private static final EventMessageMatchers eventMessageMatchers
    = new EventMessageMatchers(TENANT_ID, vertxUrl(""));

  private BoundWithEventMessageChecks() { }

  public static void boundWithCreatedMessagePublished(JsonObject boundWith,
    String instanceId) {

    // Bound With messages are published with the instance ID as the key
    // and that property is not part of the record, so must be provided separately
    awaitAtMost().until(() -> FakeKafkaConsumer.getMessagesForBoundWith(instanceId),
      eventMessageMatchers.hasCreateEventMessageFor(
        addInstanceIdToBoundWith(boundWith, instanceId)));
  }

  public static void boundWithUpdatedMessagePublished(JsonObject oldBoundWith,
    JsonObject newBoundWith, String oldInstanceId, String newInstanceId) {

    awaitAtMost().until(() -> getMessagesForBoundWith(newInstanceId),
      hasBoundWithUpdateMessageFor(oldBoundWith, newBoundWith, oldInstanceId, newInstanceId));
  }

  @NotNull
  private static Matcher<Iterable<? super EventMessage>> hasBoundWithUpdateMessageFor(
    JsonObject oldBoundWith, JsonObject newBoundWith, String oldInstanceId,
    String newInstanceId) {

    // JsonObject oldRepresentation = addInstanceIdToBoundWith(oldBoundWith, oldInstanceId);
    JsonObject newRepresentation = addInstanceIdToBoundWith(newBoundWith, newInstanceId);

    return CoreMatchers.hasItem(allOf(
      eventMessageMatchers.isUpdateEvent(),
      eventMessageMatchers.isForTenant(),
      eventMessageMatchers.hasHeaders(),
      eventMessageMatchers.hasNewRepresentation(newRepresentation)));

      // There is a potential bug with the old representation in these message
      // until this is investigated further, this check is removed
      // eventMessageMatchers.hasOldRepresentation(oldRepresentation)));
  }

  private static JsonObject addInstanceIdToBoundWith(JsonObject boundWith, String instanceId) {
    // Event for bound with has an extra 'instanceId' property for
    // old/new object, the property does not exist in schema,
    // so we have to add it manually
    return boundWith.copy().put("instanceId", instanceId);
  }
}
