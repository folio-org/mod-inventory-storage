package org.folio.rest.support.messages;

import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InstanceEventMessage {
  public static InstanceEventMessage fromConsumerRecord(
    KafkaConsumerRecord<String, JsonObject> consumerRecord) {

    final var value = consumerRecord.value();

    return new InstanceEventMessage(value.getString("type"),
      value.getString("tenant"), value.getJsonObject("new"),
      value.getJsonObject("old"));
  }

  private final String type;
  private final String tenant;
  private final JsonObject newRepresentation;
  private final JsonObject oldRepresentation;
}
