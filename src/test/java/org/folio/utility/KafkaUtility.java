package org.folio.utility;

import static java.time.Duration.ofMinutes;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public final class KafkaUtility {
  private static final Logger logger = LogManager.getLogger();

  private static final KafkaContainer KAFKA_CONTAINER
    = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"));

  private KafkaUtility() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }

  public static void startKafka() {
    KAFKA_CONTAINER.start();

    logger.info("starting Kafka host={} port={}",
      KAFKA_CONTAINER.getHost(), KAFKA_CONTAINER.getFirstMappedPort());

    var kafkaHost = KAFKA_CONTAINER.getHost();
    var kafkaPort = String.valueOf(KAFKA_CONTAINER.getFirstMappedPort());
    logger.info("Starting Kafka host={} port={}", kafkaHost, kafkaPort);
    System.setProperty("kafka-port", kafkaPort);
    System.setProperty("kafka-host", kafkaHost);

    await().atMost(ofMinutes(1)).until(KAFKA_CONTAINER::isRunning);

    logger.info("finished starting Kafka");
  }

  public static void stopKafka() {
    if (KAFKA_CONTAINER.isRunning()) {
      logger.info("stopping Kafka host={} port={}",
        KAFKA_CONTAINER.getHost(), KAFKA_CONTAINER.getFirstMappedPort());

      KAFKA_CONTAINER.stop();
      logger.info("finished stopping Kafka");
    } else {
      logger.info("Kafka container already stopped");
    }
  }
}
