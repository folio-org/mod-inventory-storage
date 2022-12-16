package org.folio.rest.support.messages;

import static java.util.UUID.fromString;
import static org.folio.rest.api.TestBase.holdingsClient;
import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getMessagesForItem;
import static org.folio.services.domainevent.CommonDomainEventPublisher.NULL_ID;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;

import java.util.UUID;

import org.folio.rest.support.messages.matchers.EventMessageMatchers;

import io.vertx.core.json.JsonObject;

public class ItemEventMessageChecks {
  private static final EventMessageMatchers eventMessageMatchers
    = new EventMessageMatchers(TENANT_ID, vertxUrl(""));

  private ItemEventMessageChecks() { }

  public static void itemCreatedMessagePublished(JsonObject item) {
    final var itemId = getId(item);
    final var instanceId = getInstanceIdForItem(item);

    awaitAtMost().until(() -> getMessagesForItem(instanceId, itemId),
      eventMessageMatchers.hasCreateEventMessageFor(
        addInstanceIdToItem(item, instanceId)));
  }

  public static void itemUpdatedMessagePublished(JsonObject oldItem, JsonObject newItem) {
    final var oldInstanceId = getInstanceIdForItem(oldItem);

    itemUpdatedMessagePublished(oldItem, newItem, oldInstanceId);
  }

  public static void itemUpdatedMessagePublished(JsonObject oldItem,
    JsonObject newItem, String oldInstanceId) {

    final var itemId = getId(newItem);
    final var newInstanceId = getInstanceIdForItem(newItem);

    awaitAtMost().until(() -> getMessagesForItem(newInstanceId, itemId),
      eventMessageMatchers.hasUpdateEventMessageFor(
        addInstanceIdToItem(oldItem, oldInstanceId),
        addInstanceIdToItem(newItem, newInstanceId)));
  }

  public static void itemDeletedMessagePublished(JsonObject item) {
    final var itemId = getId(item);
    final var instanceId = getInstanceIdForItem(item);

    awaitAtMost().until(() -> getMessagesForItem(instanceId, itemId),
      eventMessageMatchers.hasDeleteEventMessageFor(
        addInstanceIdToItem(item, instanceId)));
  }

  public static void allItemsDeletedMessagePublished() {
    awaitAtMost()
      .until(() -> getMessagesForItem(NULL_ID, null),
        eventMessageMatchers.hasDeleteAllEventMessage());
  }

  private static String getId(JsonObject item) {
    return item.getString("id");
  }

  private static String getHoldingsRecordIdForItem(JsonObject item) {
    return item.getString("holdingsRecordId");
  }

  private static JsonObject addInstanceIdToItem(JsonObject item, String instanceId) {
    // Event for item has an extra 'instanceId' property for
    // old/new object, the property does not exist in schema,
    // so we have to add it manually
    return item.copy().put("instanceId", instanceId);
  }

  private static String getInstanceIdForItem(JsonObject newItem) {
    final UUID holdingsRecordId = fromString(getHoldingsRecordIdForItem(newItem));

    return holdingsClient.getById(holdingsRecordId).getJson().getString("instanceId");
  }
}
