package org.folio.rest.support.kafka;

import java.util.function.Function;

import org.folio.rest.support.messages.EventMessage;

import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

class GroupedMessageCollector implements MessageCollector {
  private final Function<KafkaConsumerRecord<String, JsonObject>, String> groupKeyMap;
  private final GroupedCollectedMessages destination;

  GroupedMessageCollector(
    Function<KafkaConsumerRecord<String, JsonObject>, String> groupKeyMap,
    GroupedCollectedMessages destination) {

    this.groupKeyMap = groupKeyMap;
    this.destination = destination;
  }

  @Override
  public void acceptMessage(KafkaConsumerRecord<String, JsonObject> message) {
    final var key = groupKeyMap.apply(message);
    final var eventMessage = EventMessage.fromConsumerRecord(message);

    destination.add(key, eventMessage);
  }
}
