package org.folio.services.kafka;

import static java.lang.Integer.parseInt;
import static java.lang.Short.parseShort;
import static java.lang.System.getenv;

import java.util.Map;
import org.apache.kafka.common.serialization.StringSerializer;

public final class KafkaProperties {
  private static int port = parseInt(getenv().getOrDefault("KAFKA_PORT", "9092"));

  public static int changePort(int port) {
    KafkaProperties.port = port;
    return port;
  }

  public static short getReplicationFactor() {
    return parseShort(getenv().getOrDefault("REPLICATION_FACTOR", "1"));
  }

  private static String getHost() {
    return getenv().getOrDefault("KAFKA_HOST", "localhost");
  }

  private static String getServerAddress() {
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
