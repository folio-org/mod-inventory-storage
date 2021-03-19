package org.folio.services.kafka;

import static java.lang.Integer.parseInt;
import static java.lang.Short.parseShort;
import static java.lang.System.getenv;

import java.util.Map;
import org.apache.kafka.common.serialization.StringSerializer;

public final class KafkaProperties {
  private static int port = parseInt(getenv().getOrDefault("KAFKA_PORT", "9092"));
  private static String host = getenv().getOrDefault("KAFKA_HOST", "localhost");

  public static void setPort(int port) {
    KafkaProducerServiceFactory.clear();
    KafkaProperties.port = port;
  }

  public static void setHost(String host) {
    KafkaProducerServiceFactory.clear();
    KafkaProperties.host = host;
  }

  public static short getReplicationFactor() {
    return parseShort(getenv().getOrDefault("REPLICATION_FACTOR", "1"));
  }

  private static String getHost() {
    return host;
  }

  public static String getServerAddress() {
    return getHost() + ":" + port;
  }

  public static Map<String, String> getProducerProperties() {
    return Map.of(
      "bootstrap.servers", getServerAddress(),
      "enable.idempotence", "true",
      "key.serializer", StringSerializer.class.getName(),
      "value.serializer", StringSerializer.class.getName()
    );
  }
}
