package org.folio.services.domainevent;

import io.vertx.kafka.client.producer.KafkaProducerRecord;
import java.util.Map;

public interface FailureHandler {
  void handleFailure(Throwable error, KafkaProducerRecord<String, String> producerRecord,
                     Map<String, String> headers);
}
