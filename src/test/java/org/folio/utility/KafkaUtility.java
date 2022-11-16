package org.folio.utility;

import static java.time.Duration.ofMinutes;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public class KafkaUtility {
  private static final Logger logger = LogManager.getLogger();

  private static final KafkaContainer kafkaContainer
    = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"));

  private KafkaUtility() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }

  public static void startKafka() {
    kafkaContainer.start();

    logger.info("starting Kafka host={} port={}",
      kafkaContainer.getHost(), kafkaContainer.getFirstMappedPort());

    var kafkaHost = kafkaContainer.getHost();
    var kafkaPort = String.valueOf(kafkaContainer.getFirstMappedPort());
    logger.info("Starting Kafka host={} port={}", kafkaHost, kafkaPort);
    System.setProperty("kafka-port", kafkaPort);
    System.setProperty("kafka-host", kafkaHost);

    await().atMost(ofMinutes(1)).until(() -> kafkaContainer.isRunning());

    logger.info("finished starting Kafka");
  }

  public static void stopKafka() {
    if (kafkaContainer.isRunning()) {
      logger.info("stopping Kafka host={} port={}",
        kafkaContainer.getHost(), kafkaContainer.getFirstMappedPort());

      kafkaContainer.stop();
      logger.info("finished stopping Kafka");
    } else {
      logger.info("Kafka container already stopped");
    }
  }
}
