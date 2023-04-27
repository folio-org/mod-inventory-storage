package org.folio.rest.support.messages;

import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;

import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import java.util.Map;
import lombok.Value;

@Value
public class EventMessage {
  String type;
  String tenant;
  JsonObject newRepresentation;
  JsonObject oldRepresentation;
  Map<String, String> headers;

  public static EventMessage fromConsumerRecord(
    KafkaConsumerRecord<String, JsonObject> consumerRecord) {

    final var value = consumerRecord.value();

    return new EventMessage(
      value.getString("type"),
      value.getString("tenant"),
      value.getJsonObject("new"),
      value.getJsonObject("old"),
      kafkaHeadersToMap(consumerRecord.headers()));
  }
}
