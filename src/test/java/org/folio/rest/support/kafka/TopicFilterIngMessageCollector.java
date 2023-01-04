package org.folio.rest.support.kafka;

import java.util.Objects;

import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

class TopicFilterIngMessageCollector implements MessageCollector {
  private final String topicName;
  private final MessageCollector wrappedMessageCollector;

  TopicFilterIngMessageCollector(String topicName,
    MessageCollector wrappedMessageCollector) {

    this.topicName = topicName;
    this.wrappedMessageCollector = wrappedMessageCollector;
  }

  @Override
  public void acceptMessage(KafkaConsumerRecord<String, JsonObject> message) {
    if (!Objects.equals(message.topic(), topicName)) {
      return;
    }

    wrappedMessageCollector.acceptMessage(message);
  }
}
