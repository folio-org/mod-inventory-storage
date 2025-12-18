package org.folio.rest.support.kafka;

import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import java.util.List;
import lombok.NonNull;

public class AggregateMessageCollector implements MessageCollector {
  @NonNull
  private final List<MessageCollector> messageCollectors;

  public AggregateMessageCollector(MessageCollector... messageCollectors) {
    this(List.of(messageCollectors));
  }

  public AggregateMessageCollector(@NonNull List<MessageCollector> messageCollectors) {
    this.messageCollectors = messageCollectors;
  }

  @Override
  public void acceptMessage(KafkaConsumerRecord<String, JsonObject> message) {
    messageCollectors.forEach(
      messageCollector -> messageCollector.acceptMessage(message));
  }
}
