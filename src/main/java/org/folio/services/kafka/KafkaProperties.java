package org.folio.services.kafka;

import io.vertx.kafka.client.serialization.JsonObjectDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.folio.kafka.SimpleConfigurationReader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  public static Map<String, String> getKafkaConsumerProperties(String groupId) {
    Map<String, String> config = new HashMap<>();
    config.put("bootstrap.servers", KafkaProperties.getHost() + ":" + KafkaProperties.getPort());
    config.put("key.deserializer", StringDeserializer.class.getName());
    config.put("max.poll.records", SimpleConfigurationReader.getValue(List.of("kafka.consumer.max.poll.records", "spring.kafka.consumer.max-poll-records"), "100"));
    config.put("value.deserializer", JsonObjectDeserializer.class.getName());
    config.put("group.id", groupId);
    config.put("metadata.max.age.ms", "15000");
    config.put("auto.offset.reset", "earliest");
    config.put("enable.auto.commit", "false");
    return config;
  }
}
