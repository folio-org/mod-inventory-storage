package org.folio.services.kafka;

import static io.vertx.kafka.client.producer.KafkaProducerRecord.create;
import static org.folio.dbschema.ObjectMapperTool.getMapper;
import static org.folio.rest.support.CompletableFutureUtil.mapFutureResultToJavaFuture;

import java.util.concurrent.CompletableFuture;

import org.folio.services.kafka.topic.KafkaTopic;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.kafka.client.producer.RecordMetadata;

public class KafkaProducerService {
  private final KafkaProducer<String, String> kafkaProducer;

  KafkaProducerService(KafkaProducer<String, String> kafkaProducer) {
    this.kafkaProducer = kafkaProducer;
  }

  /**
   * @throws IllegalArgumentException if unable to serialize the value to string.
   */
  public CompletableFuture<Void> sendMessage(String entityId, Object value, KafkaTopic kafkaTopic) {
    try {
      final String payload = getMapper().writeValueAsString(value);
      return sendMessage(entityId, payload, kafkaTopic.getTopicName());
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Unable to deserialize message", ex);
    }
  }

  public CompletableFuture<Void> sendMessage(String key, String value, String topic) {
    final CompletableFuture<RecordMetadata> result = new CompletableFuture<>();
    final KafkaProducerRecord<String, String> kafkaRecord = create(topic, key, value);

    kafkaProducer.send(kafkaRecord, mapFutureResultToJavaFuture(result));

    return result.thenApply(rm -> null);
  }
}
