package org.folio.services.kafka;

import static org.apache.commons.lang3.StringUtils.isNoneBlank;
import static org.apache.logging.log4j.LogManager.getLogger;

import java.io.InputStream;
import java.util.Properties;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.logging.log4j.Logger;

public final class KafkaConfigHelper {
  private static final Logger log = getLogger(KafkaConfigHelper.class);

  private static final String KAFKA_HOST = "KAFKA_HOST";
  private static final String KAFKA_PORT = "KAFKA_PORT";
  private static final String KAFKA_CONFIG_FILENAME = "kafka-config.properties";

  private KafkaConfigHelper() {}

  /**
   * @throws IllegalArgumentException - if kafka config can not be read
   */
  public static Properties getKafkaProperties() {
    return getKafkaProperties(KAFKA_CONFIG_FILENAME);
  }

  /**
   * @throws IllegalArgumentException - if kafka config can not be read
   */
  static Properties getKafkaProperties(String filePath) {
    try (final InputStream propertiesIo = KafkaConfigHelper.class.getClassLoader()
      .getResourceAsStream(filePath)) {

      final Properties properties = new Properties();

      properties.load(propertiesIo);
      updateKafkaAddress(properties);

      log.info("Kafka config {}", properties);

      return properties;
    } catch (Exception ex) {
      log.error("Unable to load kafka config", ex);
      throw new IllegalArgumentException("Unable to load kafka config", ex);
    }
  }

  private static void updateKafkaAddress(Properties kafkaConfig) {
    if (hasKafkaHostAndPortParameter()) {
      log.info("Updating kafka host with {}", getKafkaAddress());
      kafkaConfig.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaAddress());
    }
  }

  private static boolean hasKafkaHostAndPortParameter() {
    return isNoneBlank(getKafkaHostParameter(), getKafkaPortParameter());
  }

  private static String getKafkaAddress() {
    return getKafkaHostParameter() + ":" + getKafkaPortParameter();
  }

  private static String getKafkaHostParameter() {
    return System.getenv(KAFKA_HOST);
  }

  private static String getKafkaPortParameter() {
    return System.getenv(KAFKA_PORT);
  }
}
