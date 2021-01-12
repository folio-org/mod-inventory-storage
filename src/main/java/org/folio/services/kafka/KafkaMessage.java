package org.folio.services.kafka;

import java.util.HashMap;
import java.util.Map;

import org.folio.services.kafka.topic.KafkaTopic;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public final class KafkaMessage<T> {
  private final T payload;
  private final String key;
  private final KafkaTopic topic;
  @Builder.Default
  private final Map<String, String> headers = new HashMap<>();

  public <V> KafkaMessage<V> withPayload(V payload) {
    return new KafkaMessage<>(payload, key, topic, headers);
  }
}
