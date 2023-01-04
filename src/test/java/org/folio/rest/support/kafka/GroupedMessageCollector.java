package org.folio.rest.support.kafka;

import java.util.List;
import java.util.function.Function;

import org.folio.rest.support.messages.EventMessage;

import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

class GroupedMessageCollector {
  private final Function<KafkaConsumerRecord<String, JsonObject>, String> groupKeyMap;
  private final GroupedCollectedMessages groupedCollectedMessages = new GroupedCollectedMessages();

  GroupedMessageCollector(
    Function<KafkaConsumerRecord<String, JsonObject>, String> groupKeyMap) {

    this.groupKeyMap = groupKeyMap;
  }

  void acceptMessage(KafkaConsumerRecord<String, JsonObject> message) {
    final var key = groupKeyMap.apply(message);
    final var eventMessage = EventMessage.fromConsumerRecord(message);

    groupedCollectedMessages.add(key, eventMessage);
  }

  List<EventMessage> messagesByGroupKey(String key) {
    return groupedCollectedMessages.messagesByGroupKey(key);
  }

  int groupCount() {
    return groupedCollectedMessages.groupCount();
  }

  void empty() {
    groupedCollectedMessages.empty();
  }
}
