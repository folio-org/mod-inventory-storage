package org.folio.services.kafka;

import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.services.kafka.KafkaConfigHelper.getKafkaProperties;

import org.apache.logging.log4j.Logger;

import io.vertx.core.Vertx;
import io.vertx.kafka.client.producer.KafkaProducer;

public final class KafkaProducerServiceFactory {
  private static final Logger log = getLogger(KafkaProducerServiceFactory.class);

  private static volatile KafkaProducerService kafkaProducerService;

  private KafkaProducerServiceFactory() {}

  /**
   * @throws IllegalArgumentException - if kafka config can not be read
   */
  public static KafkaProducerService getKafkaProducerService(Vertx vertx) {
    if (kafkaProducerService == null) {
      synchronized (KafkaProducerService.class) {
        if (kafkaProducerService == null) {
          log.info("Creating KafkaProducerService...");
          kafkaProducerService = createProducer(vertx);
        }
      }
    }

    return kafkaProducerService;
  }

  /**
   * @throws IllegalArgumentException - if kafka config can not be read
   */
  private static KafkaProducerService createProducer(Vertx vertx) {
    return new KafkaProducerService(KafkaProducer.create(vertx, getKafkaProperties()));
  }
}
