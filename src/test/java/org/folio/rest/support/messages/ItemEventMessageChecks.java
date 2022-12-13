package org.folio.rest.support.messages;

import static java.util.UUID.fromString;
import static org.folio.rest.api.TestBase.holdingsClient;
import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getMessagesForItem;
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
      eventMessageMatchers.hasCreateEventMessageFor(addInstanceIdForItem(item, instanceId)));
  }

  private static String getId(JsonObject item) {
    return item.getString("id");
  }

  private static String getInstanceIdForItem(JsonObject newItem) {
    final UUID holdingsRecordId = fromString(getHoldingsRecordIdForItem(newItem));

    return holdingsClient.getById(holdingsRecordId).getJson().getString("instanceId");
  }

  private static String getHoldingsRecordIdForItem(JsonObject item) {
    return item.getString("holdingsRecordId");
  }

  private static JsonObject addInstanceIdForItem(JsonObject item, String instanceId) {
    // Domain event for item has an extra 'instanceId' property for
    // old/new object, the property does not exist in schema,
    // so we have to add it manually
    return item.copy().put("instanceId", instanceId);
  }
}
