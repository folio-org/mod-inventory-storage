package org.folio.services.kafka;

import static io.vertx.core.logging.LoggerFactory.getLogger;
import static org.folio.services.kafka.KafkaConfigHelper.getKafkaProperties;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
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

          log.info("Registering shutdown hook to close the kafka producer");
          closeKafkaProducerOnShutdown();
        }
      }
    }

    return kafkaProducerService;
  }

  private static void closeKafkaProducerOnShutdown() {
    Runtime.getRuntime().addShutdownHook(new Thread(
      () -> kafkaProducerService.closeProducer()
        .whenComplete((notUsed, error) -> {
          if (error != null) {
            log.error("Unable to close kafka producer", error);
          } else {
            log.info("Kafka producer closed successfully");
          }
        })));
  }

  /**
   * @throws IllegalArgumentException - if kafka config can not be read
   */
  private static KafkaProducerService createProducer(Vertx vertx) {
    return new KafkaProducerService(KafkaProducer.create(vertx, getKafkaProperties()));
  }
}
