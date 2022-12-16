package org.folio.rest.support.kafka;

import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.rest.support.messages.EventMessage;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.serialization.JsonObjectDeserializer;

public class MessageCollectingTopicConsumer {
  private final String topicName;
  private final Map<String, List<EventMessage>> collectedMessages;
  private final Function<KafkaConsumerRecord<String, JsonObject>, String> keyMap;
  private KafkaConsumer<String, JsonObject> consumer;

  public MessageCollectingTopicConsumer(String topicName,
    Function<KafkaConsumerRecord<String, JsonObject>, String> keyMap) {

    this.topicName = topicName;
    this.collectedMessages = new ConcurrentHashMap<>();
    this.keyMap = keyMap;
  }

  void subscribe(Vertx vertx) {
    consumer = KafkaConsumer.create(vertx, consumerProperties());

    consumer.handler(this::acceptMessage);
    consumer.subscribe(topicName);
  }

  private void acceptMessage(KafkaConsumerRecord<String, JsonObject> message) {
    final var collectedMessages = this.collectedMessages.computeIfAbsent(
      this.keyMap.apply(message), v -> new ArrayList<>());

    collectedMessages.add(EventMessage.fromConsumerRecord(message));
  }
  void unsubscribe() {
    if (consumer != null) {
      consumer.unsubscribe();
    }
  }

  Collection<EventMessage> receivedMessagesByKey(String key) {
    return collectedMessages.getOrDefault(key, emptyList());
  }

  int countOfReceivedKeys() {
    return collectedMessages.size();
  }

  void discardCollectedMessages() {
    collectedMessages.clear();
  }

  private static Map<String, String> consumerProperties() {
    Map<String, String> config = new HashMap<>();
    config.put("bootstrap.servers",
      KafkaEnvironmentProperties.host() + ":" + KafkaEnvironmentProperties.port());
    config.put("key.deserializer", StringDeserializer.class.getName());
    config.put("value.deserializer", JsonObjectDeserializer.class.getName());
    config.put("group.id", "folio_test");
    config.put("auto.offset.reset", "earliest");
    config.put("enable.auto.commit", "false");

    return config;
  }
}
