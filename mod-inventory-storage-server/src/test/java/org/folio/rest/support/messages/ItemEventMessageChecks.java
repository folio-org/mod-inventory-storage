package org.folio.rest.support.messages;

import static java.util.UUID.fromString;
import static org.folio.rest.api.TestBase.holdingsClient;
import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.services.domainevent.CommonDomainEventPublisher.NULL_ID;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;

import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.UUID;
import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.folio.rest.support.messages.matchers.EventMessageMatchers;

public class ItemEventMessageChecks {

  private final EventMessageMatchers eventMessageMatchers;
  private final FakeKafkaConsumer kafkaConsumer;

  public ItemEventMessageChecks(FakeKafkaConsumer kafkaConsumer) {
    this.kafkaConsumer = kafkaConsumer;
    this.eventMessageMatchers = new EventMessageMatchers(TENANT_ID, vertxUrl(""));
  }

  public ItemEventMessageChecks(FakeKafkaConsumer kafkaConsumer, String urlHeader) {
    this.kafkaConsumer = kafkaConsumer;
    try {
      this.eventMessageMatchers = new EventMessageMatchers(TENANT_ID, URI.create(urlHeader).toURL());
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  public void createdMessagePublished(JsonObject item) {
    final var itemId = getId(item);
    final var instanceId = getInstanceIdForItem(item);

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForItem(itemId),
      eventMessageMatchers.hasCreateEventMessageFor(addInstanceIdToItem(item, instanceId)));
  }

  public void createdMessagePublished(String itemId) {
    awaitAtMost().until(() -> kafkaConsumer.getMessagesForItem(itemId),
      hasItem(hasProperty("type", is("CREATE"))));
  }

  public void updatedMessagePublished(JsonObject oldItem, JsonObject newItem) {
    final var oldInstanceId = getInstanceIdForItem(oldItem);

    updatedMessagePublished(oldItem, newItem, oldInstanceId);
  }

  public void updatedMessagePublished(JsonObject oldItem,
                                      JsonObject newItem, String oldInstanceId) {

    final var itemId = getId(newItem);
    final var newInstanceId = getInstanceIdForItem(newItem);

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForItem(itemId),
      eventMessageMatchers.hasUpdateEventMessageFor(
        addInstanceIdToItem(oldItem, oldInstanceId),
        addInstanceIdToItem(newItem, newInstanceId)));
  }

  public void deletedMessagePublished(JsonObject item) {
    final var itemId = getId(item);
    final var instanceId = getInstanceIdForItem(item);

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForItem(itemId),
      eventMessageMatchers.hasDeleteEventMessageFor(addInstanceIdToItem(item, instanceId)));
  }

  public void allItemsDeletedMessagePublished() {
    awaitAtMost()
      .until(() -> kafkaConsumer.getMessagesForItemWithInstanceIdKey(NULL_ID, null),
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
