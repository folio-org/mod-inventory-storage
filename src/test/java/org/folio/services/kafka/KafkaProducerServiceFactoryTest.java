package org.folio.services.kafka;

import static org.folio.services.kafka.KafkaProducerServiceFactory.getKafkaProducerService;
import static org.junit.Assert.assertEquals;

import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.Test;

import io.vertx.core.Vertx;

public class KafkaProducerServiceFactoryTest {

  static final int KAFKA_TEST_PORT = NetworkUtils.nextFreePort();

  @Test
  public void kafkaProducerIsSingleton() {
    final Vertx vertx = Vertx.vertx();

    KafkaProperties.changePort(KAFKA_TEST_PORT);
    final KafkaProducerService firstKafkaProducer = getKafkaProducerService(vertx);
    final KafkaProducerService secondKafkaProducer = getKafkaProducerService(vertx);

    assertEquals(firstKafkaProducer, secondKafkaProducer);
  }
}
