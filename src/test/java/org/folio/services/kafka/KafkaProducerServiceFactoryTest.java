package org.folio.services.kafka;

import static org.folio.services.kafka.KafkaProducerServiceFactory.getKafkaProducerService;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.vertx.core.Vertx;

public class KafkaProducerServiceFactoryTest {
  @Test
  public void kafkaProducerIsSingleton() {
    final Vertx vertx = Vertx.vertx();

    final KafkaProducerService firstKafkaProducer = getKafkaProducerService(vertx);
    final KafkaProducerService secondKafkaProducer = getKafkaProducerService(vertx);

    assertEquals(firstKafkaProducer, secondKafkaProducer);
  }
}
