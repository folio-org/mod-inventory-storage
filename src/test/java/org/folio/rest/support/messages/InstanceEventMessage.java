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

    return new InstanceEventMessage(consumerRecord.value().getString("type"));
  }
  private final String type;
}
