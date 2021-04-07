package org.folio.services.kafka;

import static java.lang.Short.parseShort;
import static java.lang.System.getenv;

public final class KafkaProperties {
  private static String port = getenv().getOrDefault("KAFKA_PORT", "9092");
  private static String host = getenv().getOrDefault("KAFKA_HOST", "localhost");

  public static void setPort(int port) {
    KafkaProperties.port = String.valueOf(port);
  }

  public static void setHost(String host) {
    KafkaProperties.host = host;
  }

  public static short getReplicationFactor() {
    return parseShort(getenv().getOrDefault("REPLICATION_FACTOR", "1"));
  }

  public static String getPort() {
    return port;
  }

  public static String getHost() {
    return host;
  }
}
