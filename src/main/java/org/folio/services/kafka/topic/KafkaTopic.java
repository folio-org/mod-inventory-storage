package org.folio.services.kafka.topic;

public class KafkaTopic {
  public static KafkaTopic instance(String tenantId, String environmentName) {
    return new KafkaTopic(qualifyName("inventory.instance", environmentName, tenantId));
  }

  public static KafkaTopic holdingsRecord(String tenantId, String environmentName) {
    return new KafkaTopic(qualifyName("inventory.holdings-record", environmentName, tenantId));
  }

  public static KafkaTopic item(String tenantId, String environmentName) {
    return new KafkaTopic(qualifyName("inventory.item", environmentName, tenantId));
  }

  private final String topicName;

  KafkaTopic(String topicName) {
    this.topicName = topicName;
  }

  public String getTopicName() {
    return topicName;
  }

  private static String qualifyName(String name, String environmentName, String tenantId) {
    return String.join(".", environmentName, tenantId, name);
  }
}
