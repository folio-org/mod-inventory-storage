package org.folio.support.messages;

import static org.folio.support.AwaitConfiguration.awaitAtMost;
import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import java.net.URI;
import org.folio.support.kafka.FakeKafkaConsumer;
import org.folio.support.messages.matchers.EventMessageMatchers;

public class ItemEventMessageChecks {

  private final EventMessageMatchers eventMessageMatchers;
  private final FakeKafkaConsumer kafkaConsumer;

  public ItemEventMessageChecks(FakeKafkaConsumer kafkaConsumer, String urlHeader) {
    this.kafkaConsumer = kafkaConsumer;
    try {
      this.eventMessageMatchers = new EventMessageMatchers(TENANT_ID, URI.create(urlHeader).toURL());
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Same, but with both
   * instance ids supplied explicitly rather than resolved via {@code holdingsClient} - which is
   * only initialized on the legacy {@code rest.api} stack, not the shared {@code *IT} verticle.
   */
  public void updatedMessagePublished(JsonObject oldItem,
                                      JsonObject newItem, String oldInstanceId, String newInstanceId) {

    final var itemId = getId(newItem);

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForItem(itemId),
      eventMessageMatchers.hasUpdateEventMessageFor(
        addInstanceIdToItem(oldItem, oldInstanceId),
        addInstanceIdToItem(newItem, newInstanceId)));
  }

  private static String getId(JsonObject item) {
    return item.getString("id");
  }

  private static JsonObject addInstanceIdToItem(JsonObject item, String instanceId) {
    // Event for item has an extra 'instanceId' property for
    // old/new object, the property does not exist in schema,
    // so we have to add it manually
    return item.copy().put("instanceId", instanceId);
  }
}
