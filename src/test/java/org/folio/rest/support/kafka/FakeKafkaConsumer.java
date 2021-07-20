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

import org.apache.kafka.common.serialization.StringDeserializer;
import org.folio.services.kafka.KafkaProperties;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.serialization.JsonObjectDeserializer;

public final class FakeKafkaConsumer {
  private final static Map<String, List<KafkaConsumerRecord<String, JsonObject>>> itemEvents =
    new ConcurrentHashMap<>();
  private final static Map<String, List<KafkaConsumerRecord<String, JsonObject>>> instanceEvents =
    new ConcurrentHashMap<>();
  private final static Map<String, List<KafkaConsumerRecord<String, JsonObject>>> holdingsEvents =
    new ConcurrentHashMap<>();

  public FakeKafkaConsumer consume(Vertx vertx) {
    final KafkaConsumer<String, JsonObject> consumer = create(vertx, consumerProperties());

    // These definitions are deliberately separate to the production definitions
    // This is so these can be changed independently to demonstrate
    // tests failing for the right reason prior to changing the production code
    final var INSTANCE_TOPIC_NAME = "inventory.instance";
    final var HOLDINGS_TOPIC_NAME = "inventory.holdings-record";
    final var ITEM_TOPIC_NAME = "inventory.item";

    consumer.subscribe(Set.of(INSTANCE_TOPIC_NAME, HOLDINGS_TOPIC_NAME, ITEM_TOPIC_NAME));
    consumer.handler(message -> {
      final List<KafkaConsumerRecord<String, JsonObject>> storageList;

      switch (message.topic()) {
        case ITEM_TOPIC_NAME:
          storageList = itemEvents.computeIfAbsent(instanceAndIdKey(message),
            k -> new ArrayList<>());
          break;
        case INSTANCE_TOPIC_NAME:
          storageList = instanceEvents.computeIfAbsent(message.key(),
            k -> new ArrayList<>());
          break;
        case HOLDINGS_TOPIC_NAME:
          storageList = holdingsEvents.computeIfAbsent(instanceAndIdKey(message),
            k -> new ArrayList<>());
          break;
        default:
          throw new IllegalArgumentException("Undefined topic");
      }

      storageList.add(message);
    });

    return this;
  }

  public void removeAllEvents() {
    itemEvents.clear();
    instanceEvents.clear();
    holdingsEvents.clear();
  }

  public static Collection<KafkaConsumerRecord<String, JsonObject> > getInstanceEvents(
    String instanceId) {

    return instanceEvents.getOrDefault(instanceId, emptyList());
  }

  public static Collection<KafkaConsumerRecord<String, JsonObject> > getItemEvents(
    String instanceId, String itemId) {

    return itemEvents.getOrDefault(instanceAndIdKey(instanceId, itemId), emptyList());
  }

  public static Collection<KafkaConsumerRecord<String, JsonObject> > getHoldingsEvents(
    String instanceId, String hrId) {

    return holdingsEvents.getOrDefault(instanceAndIdKey(instanceId, hrId), emptyList());
  }

  private static KafkaConsumerRecord<String, JsonObject>  getLastEvent(
    Collection<KafkaConsumerRecord<String, JsonObject> > events) {

    return events.stream().skip(events.size() - 1).findFirst().orElse(null);
  }

  private static KafkaConsumerRecord<String, JsonObject>  getFirstEvent(
    Collection<KafkaConsumerRecord<String, JsonObject> > events) {

    return events.stream().findFirst().orElse(null);
  }

  public static KafkaConsumerRecord<String, JsonObject> getLastInstanceEvent(
    String instanceId) {

    return getLastEvent(getInstanceEvents(instanceId));
  }

  public static KafkaConsumerRecord<String, JsonObject>  getFirstInstanceEvent(
    String instanceId) {

    return getFirstEvent(getInstanceEvents(instanceId));
  }

  public static KafkaConsumerRecord<String, JsonObject>  getLastItemEvent(
    String instanceId, String itemId) {

    return getLastEvent(getItemEvents(instanceId, itemId));
  }

  public static KafkaConsumerRecord<String, JsonObject>  getFirstItemEvent(
    String instanceId, String itemId) {

    return getFirstEvent(getItemEvents(instanceId, itemId));
  }

  public static KafkaConsumerRecord<String, JsonObject>  getLastHoldingEvent(
    String instanceId, String hrId) {

    return getLastEvent(getHoldingsEvents(instanceId, hrId));
  }

  public static KafkaConsumerRecord<String, JsonObject> getFirstHoldingEvent(
    String instanceId, String hrId) {

    return getFirstEvent(getHoldingsEvents(instanceId, hrId));
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

  private Map<String, String> consumerProperties() {
    Map<String, String> config = new HashMap<>();
    config.put("bootstrap.servers", KafkaProperties.getHost() + ":" + KafkaProperties.getPort());
    config.put("key.deserializer", StringDeserializer.class.getName());
    config.put("value.deserializer", JsonObjectDeserializer.class.getName());
    config.put("group.id", "folio_test");
    config.put("auto.offset.reset", "earliest");
    config.put("enable.auto.commit", "true");

    return config;
  }
}
