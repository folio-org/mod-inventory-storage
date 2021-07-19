package org.folio.services.kafka.topic;

public class KafkaTopic {
  public static KafkaTopic instance(String environmentName) {
    return new KafkaTopic(qualifyName("inventory.instance", environmentName));
  }

  public static KafkaTopic holdingsRecord(String environmentName) {
    return new KafkaTopic(qualifyName("inventory.holdings-record", environmentName));
  }

  public static KafkaTopic item(String environmentName) {
    return new KafkaTopic(qualifyName("inventory.item", environmentName));
  }

  private final String topicName;

  KafkaTopic(String topicName) {
    this.topicName = topicName;
  }

  public String getTopicName() {
    return topicName;
  }

  private static String qualifyName(String name, String environmentName) {
    return String.join(".", environmentName, name);
  }
}
