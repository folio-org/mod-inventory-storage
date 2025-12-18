package org.folio.rest.support.kafka;

import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import java.util.function.Function;
import org.folio.rest.support.messages.EventMessage;

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
