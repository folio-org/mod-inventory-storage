package org.folio.services.kafka.topic;

public class KafkaTopic {
  public static KafkaTopic instance() {
    return new KafkaTopic(prefixWithEnvironment("inventory.instance"));
  }

  public static KafkaTopic holdingsRecord() {
    return new KafkaTopic(prefixWithEnvironment("inventory.holdings-record"));
  }

  public static KafkaTopic item() {
    return new KafkaTopic(prefixWithEnvironment("inventory.item"));
  }

  private final String topicName;

  KafkaTopic(String topicName) {
    this.topicName = topicName;
  }

  public String getTopicName() {
    return topicName;
  }

  private static String prefixWithEnvironment(String topicName) {
    final var environmentName = System.getenv().getOrDefault("ENV", "folio");

    return environmentName + "." + topicName;
  }
}
