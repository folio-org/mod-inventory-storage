package org.folio.services.domainevent;

import io.vertx.kafka.client.producer.KafkaProducerRecord;

public interface FailureHandler {
  void handleFailure(Throwable error, KafkaProducerRecord<String, String> producerRecord);
}
