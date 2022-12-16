package org.folio.rest.support.kafka;

import static io.vertx.kafka.client.consumer.KafkaConsumer.create;
import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.rest.support.messages.EventMessage;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.serialization.JsonObjectDeserializer;
import lombok.AllArgsConstructor;
import lombok.Getter;

public final class FakeKafkaConsumer {
  // These definitions are deliberately separate to the production definitions
  // This is so these can be changed independently to demonstrate
  // tests failing for the right reason prior to changing the production code
  final static String INSTANCE_TOPIC_NAME = "folio.test_tenant.inventory.instance";
  final static String HOLDINGS_TOPIC_NAME = "folio.test_tenant.inventory.holdings-record";
  final static String ITEM_TOPIC_NAME = "folio.test_tenant.inventory.item";
  final static String AUTHORITY_TOPIC_NAME = "folio.test_tenant.inventory.authority";
  final static String BOUND_WITH_TOPIC_NAME = "folio.test_tenant.inventory.bound-with";

  private final static Map<String, List<EventMessage>> instanceEvents = new ConcurrentHashMap<>();
  private final static TopicConsumer instanceTopicConsumer = new TopicConsumer(INSTANCE_TOPIC_NAME,
    instanceEvents, KafkaConsumerRecord::key);
  private final static Map<String, List<EventMessage>> holdingsEvents = new ConcurrentHashMap<>();
  private final static TopicConsumer holdingsTopicConsumer = new TopicConsumer(HOLDINGS_TOPIC_NAME,
    holdingsEvents, FakeKafkaConsumer::instanceAndIdKey);
  private final static Map<String, List<EventMessage>> itemEvents = new ConcurrentHashMap<>();
  private final static TopicConsumer itemTopicConsumer = new TopicConsumer(ITEM_TOPIC_NAME,
    itemEvents, FakeKafkaConsumer::instanceAndIdKey);
  private final static Map<String, List<EventMessage>> authorityEvents = new ConcurrentHashMap<>();
  private final static TopicConsumer authorityTopicConsumer = new TopicConsumer(AUTHORITY_TOPIC_NAME,
    authorityEvents, KafkaConsumerRecord::key);
  private final static Map<String, List<EventMessage>> boundWithEvents = new ConcurrentHashMap<>();
  private final static TopicConsumer boundWithTopicConsumer = new TopicConsumer(BOUND_WITH_TOPIC_NAME,
    boundWithEvents, KafkaConsumerRecord::key);

  // Provide a strong reference to reduce the chances of deallocation before
  // all clients are properly unsubscribed.
  private KafkaConsumer<String, JsonObject> consumer;

  public FakeKafkaConsumer consume(Vertx vertx) {
    final KafkaConsumer<String, JsonObject> consumer = create(vertx, consumerProperties());

    final var topicConsumers = Set.of(instanceTopicConsumer, holdingsTopicConsumer,
      itemTopicConsumer, authorityTopicConsumer, boundWithTopicConsumer);

    consumer.subscribe(topicConsumers.stream()
      .map(TopicConsumer::getTopicName)
      .collect(Collectors.toSet()));

    consumer.handler(message -> {
      topicConsumers.forEach(topicConsumer -> {
        if (topicConsumer.accepts(message)) {
          topicConsumer.acceptMessage(message);
        }
      });
    });

    // Assign the created consumer to the class being returned.
    // The caller of this function may then be able to call unsubscribe()
    // as needed. This ensures that the reference remains strong once
    // this once out of the scope of this function.
    this.setConsumer(consumer);

    return this;
  }

  public void unsubscribe() {
    consumer.unsubscribe();
  }

  public static void discardAllMessages() {
    itemTopicConsumer.discardCollectedMessages();
    instanceTopicConsumer.discardCollectedMessages();
    holdingsTopicConsumer.discardCollectedMessages();
    authorityTopicConsumer.discardCollectedMessages();
    boundWithTopicConsumer.discardCollectedMessages();
  }

  public static int getAllPublishedAuthoritiesCount() {
    return authorityEvents.size();
  }

  public static Collection<EventMessage> getMessagesForAuthority(String authorityId) {
    return authorityTopicConsumer.getMessagesByKey(authorityId);
  }

  public static int getAllPublishedInstanceIdsCount() {
    return instanceEvents.size();
  }

  public static Collection<EventMessage> getMessagesForInstance(String instanceId) {
    return instanceTopicConsumer.getMessagesByKey(instanceId);
  }

  public static Collection<EventMessage> getMessagesForInstances(List<String> instanceIds) {
    return instanceIds.stream()
      .map(FakeKafkaConsumer::getMessagesForInstance)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  public static Collection<EventMessage> getMessagesForHoldings(
    String instanceId, String holdingsId) {

    return holdingsTopicConsumer.getMessagesByKey(instanceAndIdKey(instanceId, holdingsId));
  }

  public static Collection<EventMessage> getMessagesForItem(
    String instanceId, String itemId) {

    return itemTopicConsumer.getMessagesByKey(instanceAndIdKey(instanceId, itemId));
  }

  public static Collection<EventMessage> getMessagesForBoundWith(String instanceId) {
    return boundWithTopicConsumer.getMessagesByKey(instanceId);
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

  private void setConsumer(KafkaConsumer<String, JsonObject> consumer) {
    this.consumer = consumer;
  }

  private Map<String, String> consumerProperties() {
    Map<String, String> config = new HashMap<>();
    config.put("bootstrap.servers", KafkaEnvironmentProperties.host() + ":" + KafkaEnvironmentProperties.port());
    config.put("key.deserializer", StringDeserializer.class.getName());
    config.put("value.deserializer", JsonObjectDeserializer.class.getName());
    config.put("group.id", "folio_test");
    config.put("auto.offset.reset", "earliest");
    config.put("enable.auto.commit", "false");

    return config;
  }

  @AllArgsConstructor
  public static class TopicConsumer {
    @Getter
    private final String topicName;
    private final Map<String, List<EventMessage>> collectedMessages;
    private final Function<KafkaConsumerRecord<String, JsonObject>, String> keyMap;

    private void acceptMessage(KafkaConsumerRecord<String, JsonObject> message) {
      final var collectedMessages = this.collectedMessages.computeIfAbsent(
        this.keyMap.apply(message), v -> new ArrayList<>());

      collectedMessages.add(EventMessage.fromConsumerRecord(message));
    }

    private boolean accepts(KafkaConsumerRecord<String, JsonObject> message) {
      return Objects.equals(message.topic(), topicName);
    }

    private Collection<EventMessage> getMessagesByKey(String key) {
      return collectedMessages.getOrDefault(key, emptyList());
    }

    private void discardCollectedMessages() {
      collectedMessages.clear();
    }
  }
}
