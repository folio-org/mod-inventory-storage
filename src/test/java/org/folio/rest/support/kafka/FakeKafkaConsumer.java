package org.folio.rest.support.kafka;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.folio.rest.support.messages.EventMessage;
import org.jetbrains.annotations.NotNull;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public final class FakeKafkaConsumer {
  // These definitions are deliberately separate to the production definitions
  // This is so these can be changed independently to demonstrate
  // tests failing for the right reason prior to changing the production code
  final static String INSTANCE_TOPIC_NAME = "folio.test_tenant.inventory.instance";
  final static String HOLDINGS_TOPIC_NAME = "folio.test_tenant.inventory.holdings-record";
  final static String ITEM_TOPIC_NAME = "folio.test_tenant.inventory.item";
  final static String AUTHORITY_TOPIC_NAME = "folio.test_tenant.inventory.authority";
  final static String BOUND_WITH_TOPIC_NAME = "folio.test_tenant.inventory.bound-with";

  private final GroupedCollectedMessages collectedInstanceMessages = new GroupedCollectedMessages();
  private final GroupedCollectedMessages collectedHoldingsMessages = new GroupedCollectedMessages();
  private final GroupedCollectedMessages collectedItemMessages = new GroupedCollectedMessages();
  private final GroupedCollectedMessages collectedAuthorityMessages = new GroupedCollectedMessages();
  private final GroupedCollectedMessages collectedBoundWithMessages = new GroupedCollectedMessages();

  private final VertxMessageCollectingTopicConsumer consumer = createConsumer();

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
    collectedAuthorityMessages.empty();
    collectedBoundWithMessages.empty();
  }

  public int getAllPublishedAuthoritiesCount() {
    return collectedAuthorityMessages.groupCount();
  }

  public Collection<EventMessage> getMessagesForAuthority(String authorityId) {
    return collectedAuthorityMessages.messagesByGroupKey(authorityId);
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
      .collect(Collectors.toList());
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

  private VertxMessageCollectingTopicConsumer createConsumer() {
    return new VertxMessageCollectingTopicConsumer(
      Set.of(INSTANCE_TOPIC_NAME, HOLDINGS_TOPIC_NAME, ITEM_TOPIC_NAME,
        AUTHORITY_TOPIC_NAME, BOUND_WITH_TOPIC_NAME),
      new AggregateMessageCollector(
        filteredAndGroupedCollector(INSTANCE_TOPIC_NAME,
          KafkaConsumerRecord::key, collectedInstanceMessages),
        filteredAndGroupedCollector(HOLDINGS_TOPIC_NAME,
          FakeKafkaConsumer::instanceAndIdKey, collectedHoldingsMessages),
        filteredAndGroupedCollector(ITEM_TOPIC_NAME,
          FakeKafkaConsumer::instanceAndIdKey, collectedItemMessages),
        filteredAndGroupedCollector(AUTHORITY_TOPIC_NAME,
          KafkaConsumerRecord::key, collectedAuthorityMessages),
        filteredAndGroupedCollector(BOUND_WITH_TOPIC_NAME,
          KafkaConsumerRecord::key, collectedBoundWithMessages)));
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
