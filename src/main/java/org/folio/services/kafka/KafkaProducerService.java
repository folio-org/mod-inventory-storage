package org.folio.services.kafka;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Promise.promise;
import static io.vertx.kafka.client.producer.KafkaProducerRecord.create;
import static org.folio.dbschema.ObjectMapperTool.getMapper;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.RecordMetadata;

public class KafkaProducerService {
  private final KafkaProducer<String, String> kafkaProducer;

  KafkaProducerService(KafkaProducer<String, String> kafkaProducer) {
    this.kafkaProducer = kafkaProducer;
  }

  /**
   * @throws IllegalArgumentException if unable to serialize the value to string.
   */
  public Future<Void> sendMessage(KafkaMessage<?> message) {
    try {
      final String payload = getMapper().writeValueAsString(message.getPayload());
      return sendMessageInternal(message.withPayload(payload));
    } catch (JsonProcessingException ex) {
      return failedFuture(new IllegalArgumentException("Unable to deserialize message", ex));
    }
  }

  private Future<Void> sendMessageInternal(KafkaMessage<String> message) {
    final Promise<RecordMetadata> result = promise();
    final var kafkaRecord = create(message.getTopic().getTopicName(), message.getKey(),
      message.getPayload());

    message.getHeaders().forEach(kafkaRecord::addHeader);

    kafkaProducer.send(kafkaRecord, result);

    return result.future().map(notUsed -> null);
  }
}
