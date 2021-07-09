package org.folio.services.kafka.topic;

public class KafkaTopic {
  public static KafkaTopic instance(String tenantId, String environmentName) {
    return new KafkaTopic(prefixWith(environmentName,
      prefixWith(tenantId, "inventory.instance")));
  }

  public static KafkaTopic holdingsRecord(String tenantId, String environmentName) {
    return new KafkaTopic(prefixWith(environmentName,
      prefixWith(tenantId, "inventory.holdings-record")));
  }

  public static KafkaTopic item(String tenantId, String environmentName) {
    return new KafkaTopic(prefixWith(environmentName,
      prefixWith(tenantId, "inventory.item")));
  }

  private final String topicName;

  KafkaTopic(String topicName) {
    this.topicName = topicName;
  }

  public String getTopicName() {
    return topicName;
  }

  private static String prefixWith(String value, String topicName) {
    return value + "." + topicName;
  }
}
