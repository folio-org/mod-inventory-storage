package org.folio.support.messages;

import static org.folio.it.BaseIntegrationTest.TENANT_ID;
import static org.folio.support.AwaitConfiguration.awaitAtMost;

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
   * Both instances are expected to have {@code instanceSource} as their source.
   */
  public void updatedMessagePublished(JsonObject oldItem, JsonObject newItem,
                                      String oldInstanceId, String newInstanceId, String instanceSource) {

    final var itemId = getId(newItem);

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForItem(itemId),
      eventMessageMatchers.hasUpdateEventMessageFor(
        addInstanceToItem(oldItem, oldInstanceId, instanceSource),
        addInstanceToItem(newItem, newInstanceId, instanceSource)));
  }

  private static String getId(JsonObject item) {
    return item.getString("id");
  }

  private static JsonObject addInstanceToItem(JsonObject item, String instanceId, String instanceSource) {
    // Event for item has extra 'instanceId' and 'instanceSource' properties for
    // old/new object, the properties do not exist in schema,
    // so we have to add them manually
    return item.copy().put("instanceId", instanceId).put("instanceSource", instanceSource);
  }
}
