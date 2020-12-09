package org.folio.rest.support.kafka;

import static io.vertx.kafka.client.consumer.KafkaConsumer.create;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;

public final class FakeKafkaConsumer {
  private final MultiMap<String, JsonObject> messages = new MultiHashMap<>();

  public final FakeKafkaConsumer consume(Vertx vertx) {
    final KafkaConsumer<String, String> consumer = create(vertx, consumerProperties());

    consumer.subscribe("inventory.instance");
    consumer.handler(message -> messages.put(message.key(), new JsonObject(message.value())));

    return this;
  }

  public void removeAllMessages() {
    messages.clear();
  }

  public Collection<JsonObject> getAllMessages(String instanceId) {
    return messages.get(instanceId);
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
