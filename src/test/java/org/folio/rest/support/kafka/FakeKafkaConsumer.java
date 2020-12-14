package org.folio.rest.support.kafka;

import static io.vertx.kafka.client.consumer.KafkaConsumer.create;
import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;

public final class FakeKafkaConsumer {
  private final static Map<String, List<JsonObject>> messages = new ConcurrentHashMap<>();

  public final FakeKafkaConsumer consume(Vertx vertx) {
    final KafkaConsumer<String, String> consumer = create(vertx, consumerProperties());

    consumer.subscribe("inventory.instance");
    consumer.handler(message -> {
      final List<JsonObject> objects = messages
        .computeIfAbsent(message.key(), k -> new ArrayList<>());

      objects.add(new JsonObject(message.value()));
    });

    return this;
  }

  public void removeAllMessages() {
    messages.clear();
  }

  public Collection<JsonObject> getAllMessages(String instanceId) {
    return messages.getOrDefault(instanceId, emptyList());
  }

  public JsonObject getLastMessage(String instanceId) {
    final Collection<JsonObject> allMessages = getAllMessages(instanceId);

    return allMessages.stream().skip(allMessages.size() - 1)
      .findFirst().orElse(null);
  }

  public JsonObject getFirstMessage(String instanceId) {
    return getAllMessages(instanceId).stream()
      .findFirst().orElse(null);
  }

  private Map<String, String> consumerProperties() {
    Map<String, String> config = new HashMap<>();
    config.put("bootstrap.servers", "localhost:9092");
    config.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    config.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    config.put("group.id", "folio");
    config.put("auto.offset.reset", "earliest");
    config.put("enable.auto.commit", "true");

    return config;
  }
}
