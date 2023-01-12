package org.folio.rest.support.kafka;

import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public interface MessageCollector {
  void acceptMessage(KafkaConsumerRecord<String, JsonObject> message);
}
