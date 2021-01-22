package org.folio.rest.support.kafka;

import static io.vertx.kafka.client.consumer.KafkaConsumer.create;
import static java.util.Collections.emptyList;
import static org.folio.services.kafka.topic.KafkaTopic.INVENTORY_ITEM;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.folio.services.kafka.KafkaMessage;
import org.folio.services.kafka.topic.KafkaTopic;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaHeader;

public final class FakeKafkaConsumer {
  private final static Map<String, List<KafkaMessage<JsonObject>>> itemEvents = new ConcurrentHashMap<>();
  private final static Map<String, List<KafkaMessage<JsonObject>>> instanceEvents = new ConcurrentHashMap<>();

  public final FakeKafkaConsumer consume(Vertx vertx) {
    final KafkaConsumer<String, String> consumer = create(vertx, consumerProperties());

    consumer.subscribe(Set.of("inventory.instance", "inventory.item"));
    consumer.handler(message -> {
      final KafkaMessage<JsonObject> kafkaMessage = kafkaRecordToKafkaMessage(message);
      List<KafkaMessage<JsonObject>> storageList;

      if (kafkaMessage.getTopic() == INVENTORY_ITEM) {
        storageList = itemEvents
          .computeIfAbsent(itemKey(kafkaMessage), k -> new ArrayList<>());
      } else {
        storageList = instanceEvents.computeIfAbsent(kafkaMessage.getKey(), k -> new ArrayList<>());
      }

      storageList.add(kafkaMessage);
    });

    return this;
  }

  public void removeAllEvents() {
    itemEvents.clear();
    instanceEvents.clear();
  }

  public static Collection<KafkaMessage<JsonObject>> getInstanceEvents(String instanceId) {
    return instanceEvents.getOrDefault(instanceId, emptyList());
  }

  public static Collection<KafkaMessage<JsonObject>> getItemEvents(String instanceId, String itemId) {
    return itemEvents.getOrDefault(itemKey(instanceId, itemId), emptyList());
  }

  private static KafkaMessage<JsonObject> getLastEvent(Collection<KafkaMessage<JsonObject>> events) {
    return events.stream().skip(events.size() - 1)
      .findFirst().orElse(null);
  }

  private static KafkaMessage<JsonObject> getFirstEvent(Collection<KafkaMessage<JsonObject>> events) {
    return events.stream()
      .findFirst().orElse(null);
  }

  public static KafkaMessage<JsonObject> getLastInstanceEvent(String instanceId) {
    return getLastEvent(getInstanceEvents(instanceId));
  }

  public static KafkaMessage<JsonObject> getFirstInstanceEvent(String instanceId) {
    return getFirstEvent(getInstanceEvents(instanceId));
  }

  public static KafkaMessage<JsonObject> getLastItemEvent(String instanceId, String itemId) {
    return getLastEvent(getItemEvents(instanceId, itemId));
  }

  public static KafkaMessage<JsonObject> getFirstItemEvent(String instanceId, String itemId) {
    return getFirstEvent(getItemEvents(instanceId, itemId));
  }

  private static String itemKey(String instanceId, String itemId) {
    return instanceId + "_" + itemId;
  }

  private static String itemKey(KafkaMessage<JsonObject> kafkaMessage) {
    final JsonObject payload = kafkaMessage.getPayload();
    final var item = payload.containsKey("new")
      ? payload.getJsonObject("new") : payload.getJsonObject("old");

    return itemKey(kafkaMessage.getKey(), item.getString("id"));
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

  private KafkaMessage<JsonObject> kafkaRecordToKafkaMessage(KafkaConsumerRecord<String, String> kafkaRecord) {
    final KafkaTopic kafkaTopic = KafkaTopic.forName(kafkaRecord.topic());
    final JsonObject payload = new JsonObject(kafkaRecord.value());
    final Map<String, String> headers = kafkaRecord.headers().stream()
      .collect(Collectors.toMap(KafkaHeader::key, header -> header.value().toString()));

    return KafkaMessage.<JsonObject>builder()
      .key(kafkaRecord.key())
      .payload(payload)
      .topic(kafkaTopic)
      .headers(headers)
      .build();
  }
}
