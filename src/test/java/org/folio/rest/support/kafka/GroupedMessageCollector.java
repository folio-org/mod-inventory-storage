package org.folio.rest.support.kafka;

import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.folio.rest.support.messages.EventMessage;

import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

class GroupedMessageCollector {
  private final Function<KafkaConsumerRecord<String, JsonObject>, String> groupKeyMap;
  private final Map<String, List<EventMessage>> collectedMessages;

  GroupedMessageCollector(
    Function<KafkaConsumerRecord<String, JsonObject>, String> groupKeyMap) {

    this.groupKeyMap = groupKeyMap;
    collectedMessages = new HashMap<>();
  }

  void acceptMessage(KafkaConsumerRecord<String, JsonObject> message) {
    final var keyBucket = collectedMessages.computeIfAbsent(
      groupKeyMap.apply(message), v -> new ArrayList<>());

    keyBucket.add(EventMessage.fromConsumerRecord(message));
  }

  List<EventMessage> messagesByGroupKey(String key) {
    return collectedMessages.getOrDefault(key, emptyList());
  }

  int groupCount() {
    return collectedMessages.size();
  }

  void empty() {
    collectedMessages.clear();
  }
}
