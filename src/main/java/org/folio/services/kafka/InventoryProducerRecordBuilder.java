package org.folio.services.kafka;

import static io.vertx.kafka.client.producer.KafkaProducerRecord.create;
import static org.folio.dbschema.ObjectMapperTool.getMapper;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.URL;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.folio.services.kafka.topic.KafkaTopic;

public final class InventoryProducerRecordBuilder {
  private static final Set<String> FORWARDER_HEADERS = Set.of(URL.toLowerCase(),
    TENANT.toLowerCase());

  private Object value;
  private String key;
  private String topic;
  private final Map<String, String> headers = new HashMap<>();

  public InventoryProducerRecordBuilder value(Object value) {
    this.value = value;
    return this;
  }

  public InventoryProducerRecordBuilder key(String key) {
    this.key = key;
    return this;
  }

  public InventoryProducerRecordBuilder topic(KafkaTopic topic) {
    this.topic = topic.getTopicName();
    return this;
  }

  public InventoryProducerRecordBuilder header(String key, String value) {
    this.headers.put(key, value);
    return this;
  }

  public InventoryProducerRecordBuilder propagateOkapiHeaders(Map<String, String> okapiHeaders) {
    okapiHeaders.entrySet().stream()
      .filter(entry -> FORWARDER_HEADERS.contains(entry.getKey().toLowerCase()))
      .forEach(entry -> header(entry.getKey(), entry.getValue()));

    return this;
  }

  public KafkaProducerRecord<String, String> build() {
    try {
      var valueAsString = value instanceof String ? (String) value : getMapper().writeValueAsString(value);
      var record = create(topic, key, valueAsString);
      headers.forEach(record::addHeader);
      return record;
    } catch (JsonProcessingException ex) {
      throw new RuntimeException(ex);
    }
  }
}
