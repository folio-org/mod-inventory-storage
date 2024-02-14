package org.folio.rest.support.kafka;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.folio.rest.support.messages.EventMessage;
import org.jetbrains.annotations.NotNull;

public final class FakeKafkaConsumer {
  // These definitions are deliberately separate to the production definitions
  // This is so these can be changed independently to demonstrate
  // tests failing for the right reason prior to changing the production code
  static final String INSTANCE_TOPIC_NAME = "folio.test.inventory.instance";
  static final String HOLDINGS_TOPIC_NAME = "folio.test.inventory.holdings-record";
  static final String ITEM_TOPIC_NAME = "folio.test.inventory.item";
  static final String BOUND_WITH_TOPIC_NAME = "folio.test.inventory.bound-with";
  static final String SERVICE_POINT_TOPIC_NAME = "folio.test.inventory.service-point";

  static final String HOLDINGS_TOPIC_NAME_CONSORTIUM_MEMBER_TENANT =
    "folio.consortium.inventory.holdings-record";

  private final GroupedCollectedMessages collectedInstanceMessages = new GroupedCollectedMessages();
  private final GroupedCollectedMessages collectedHoldingsMessages = new GroupedCollectedMessages();
  private final GroupedCollectedMessages collectedItemMessages = new GroupedCollectedMessages();
  private final GroupedCollectedMessages collectedBoundWithMessages = new GroupedCollectedMessages();
  private final GroupedCollectedMessages collectedServicePointMessages = new GroupedCollectedMessages();

  private final VertxMessageCollectingTopicConsumer consumer = createConsumer();

  private static String instanceAndIdKey(String instanceId, String itemId) {
    return instanceId + "_" + itemId;
  }

  private static String instanceAndIdKey(KafkaConsumerRecord<String, JsonObject> message) {
    final JsonObject payload = message.value();
    final var oldOrNew = payload.containsKey("new")
                         ? payload.getJsonObject("new") : payload.getJsonObject("old");

    final var id = oldOrNew != null ? oldOrNew.getString("id") : null;

    return instanceAndIdKey(message.key(), id);
  }

  public void consume(Vertx vertx) {
    consumer.subscribe(vertx);
  }

  public void unsubscribe() {
    consumer.unsubscribe();
  }

  public void discardAllMessages() {
    collectedInstanceMessages.empty();
    collectedHoldingsMessages.empty();
    collectedItemMessages.empty();
    collectedBoundWithMessages.empty();
    collectedServicePointMessages.empty();
  }

  public int getAllPublishedInstanceIdsCount() {
    return collectedInstanceMessages.groupCount();
  }

  public Collection<EventMessage> getMessagesForInstance(String instanceId) {
    return collectedInstanceMessages.messagesByGroupKey(instanceId);
  }

  public Collection<EventMessage> getMessagesForInstances(List<String> instanceIds) {
    return instanceIds.stream()
      .map(this::getMessagesForInstance)
      .flatMap(Collection::stream)
      .toList();
  }

  public Collection<EventMessage> getMessagesForHoldings(String instanceId, String holdingsId) {
    return collectedHoldingsMessages.messagesByGroupKey(instanceAndIdKey(instanceId, holdingsId));
  }

  public Collection<EventMessage> getMessagesForItem(String instanceId, String itemId) {
    return collectedItemMessages.messagesByGroupKey(instanceAndIdKey(instanceId, itemId));
  }

  public Collection<EventMessage> getMessagesForBoundWith(String instanceId) {
    return collectedBoundWithMessages.messagesByGroupKey(instanceId);
  }

  public Collection<EventMessage> getMessagesForServicePoint(String servicePointId) {
    return collectedServicePointMessages.messagesByGroupKey(servicePointId);
  }

  private VertxMessageCollectingTopicConsumer createConsumer() {
    return new VertxMessageCollectingTopicConsumer(
      Set.of(INSTANCE_TOPIC_NAME, HOLDINGS_TOPIC_NAME, ITEM_TOPIC_NAME,
        BOUND_WITH_TOPIC_NAME, SERVICE_POINT_TOPIC_NAME,
        HOLDINGS_TOPIC_NAME_CONSORTIUM_MEMBER_TENANT),
      new AggregateMessageCollector(
        filteredAndGroupedCollector(INSTANCE_TOPIC_NAME,
          KafkaConsumerRecord::key, collectedInstanceMessages),
        filteredAndGroupedCollector(HOLDINGS_TOPIC_NAME,
          FakeKafkaConsumer::instanceAndIdKey, collectedHoldingsMessages),
        filteredAndGroupedCollector(ITEM_TOPIC_NAME,
          FakeKafkaConsumer::instanceAndIdKey, collectedItemMessages),
        filteredAndGroupedCollector(BOUND_WITH_TOPIC_NAME,
          KafkaConsumerRecord::key, collectedBoundWithMessages),
        filteredAndGroupedCollector(SERVICE_POINT_TOPIC_NAME,
          KafkaConsumerRecord::key, collectedServicePointMessages),
        filteredAndGroupedCollector(HOLDINGS_TOPIC_NAME_CONSORTIUM_MEMBER_TENANT,
          FakeKafkaConsumer::instanceAndIdKey, collectedHoldingsMessages)));
  }

  @NotNull
  private TopicFilterIngMessageCollector filteredAndGroupedCollector(
    String topicName,
    Function<KafkaConsumerRecord<String, JsonObject>, String> groupKeyMap,
    GroupedCollectedMessages collectedMessages) {

    return new TopicFilterIngMessageCollector(topicName,
      new GroupedMessageCollector(groupKeyMap, collectedMessages));
  }
}
