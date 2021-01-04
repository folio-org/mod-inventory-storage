package org.folio.rest.support.kafka;

import static io.vertx.kafka.client.consumer.KafkaConsumer.create;
import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;

public final class FakeKafkaConsumer {
  private final static Map<String, List<JsonObject>> itemEvents = new ConcurrentHashMap<>();
  private final static Map<String, List<JsonObject>> instanceEvents = new ConcurrentHashMap<>();

  public final FakeKafkaConsumer consume(Vertx vertx) {
    final KafkaConsumer<String, String> consumer = create(vertx, consumerProperties());

    consumer.subscribe(Set.of("inventory.instance", "inventory.item"));
    consumer.handler(message -> {
      final JsonObject payload = new JsonObject(message.value());
      final String instanceId = message.key();
      List<JsonObject> storageList;

      if (message.topic().equals("inventory.item")) {
        storageList = itemEvents
          .computeIfAbsent(itemKey(instanceId, payload), k -> new ArrayList<>());
      } else {
        storageList = instanceEvents.computeIfAbsent(instanceId, k -> new ArrayList<>());
      }

      storageList.add(payload);
    });

    return this;
  }

  public void removeAllEvents() {
    itemEvents.clear();
    instanceEvents.clear();
  }

  public static Collection<JsonObject> getInstanceEvents(String instanceId) {
    return instanceEvents.getOrDefault(instanceId, emptyList());
  }

  public static Collection<JsonObject> getItemEvents(String instanceId, String itemId) {
    return itemEvents.getOrDefault(itemKey(instanceId, itemId), emptyList());
  }

  private static JsonObject getLastEvent(Collection<JsonObject> events) {
    return events.stream().skip(events.size() - 1)
      .findFirst().orElse(null);
  }

  private static JsonObject getFirstEvent(Collection<JsonObject> events) {
    return events.stream()
      .findFirst().orElse(null);
  }

  public static JsonObject getLastInstanceEvent(String instanceId) {
    return getLastEvent(getInstanceEvents(instanceId));
  }

  public static JsonObject getFirstInstanceEvent(String instanceId) {
    return getFirstEvent(getInstanceEvents(instanceId));
  }

  public static JsonObject getLastItemEvent(String instanceId, String itemId) {
    return getLastEvent(getItemEvents(instanceId, itemId));
  }

  public static JsonObject getFirstItemEvent(String instanceId, String itemId) {
    return getFirstEvent(getItemEvents(instanceId, itemId));
  }

  private static String itemKey(String instanceId, String itemId) {
    return instanceId + "_" + itemId;
  }

  private static String itemKey(String instanceId, JsonObject payload) {
    final var item = payload.containsKey("new")
      ? payload.getJsonObject("new") : payload.getJsonObject("old");

    return itemKey(instanceId, item.getString("id"));
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
