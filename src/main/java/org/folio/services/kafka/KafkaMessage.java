package org.folio.services.kafka;

import java.util.HashMap;
import java.util.Map;

import org.folio.services.kafka.topic.KafkaTopic;

public final class KafkaMessage<T> {
  private final T payload;
  private final String key;
  private final KafkaTopic topic;
  private final Map<String, String> headers;

  private KafkaMessage(T payload, String key, KafkaTopic topic, Map<String, String> headers) {
    this.payload = payload;
    this.key = key;
    this.topic = topic;
    this.headers = new HashMap<>(headers);
  }

  public static <T> KafkaMessageBuilder<T> builder() {
    return new KafkaMessageBuilder<>();
  }

  public <V> KafkaMessage<V> withPayload(V payload) {
    return new KafkaMessage<>(payload, key, topic, headers);
  }

  public T getPayload() {
    return this.payload;
  }

  public String getKey() {
    return this.key;
  }

  public KafkaTopic getTopic() {
    return this.topic;
  }

  public Map<String, String> getHeaders() {
    return this.headers;
  }

  public static class KafkaMessageBuilder<T> {
    private T payload;
    private String key;
    private KafkaTopic topic;
    private final Map<String, String> headers = new HashMap<>();

    public KafkaMessageBuilder<T> payload(T payload) {
      this.payload = payload;
      return this;
    }

    public KafkaMessageBuilder<T> key(String key) {
      this.key = key;
      return this;
    }

    public KafkaMessageBuilder<T> topic(KafkaTopic topic) {
      this.topic = topic;
      return this;
    }

    public KafkaMessageBuilder<T> headers(Map<String, String> headers) {
      this.headers.putAll(headers);
      return this;
    }

    public KafkaMessage<T> build() {
      return new KafkaMessage<>(payload, key, topic, headers);
    }
  }
}
