package org.folio.services.kafka.topic;

public class KafkaTopic {

  public static KafkaTopic instance(String tenantId, String environmentName) {
    return forName("inventory.instance", tenantId, environmentName);
  }

  public static KafkaTopic holdingsRecord(String tenantId, String environmentName) {
    return forName("inventory.holdings-record", tenantId, environmentName);
  }

  public static KafkaTopic item(String tenantId, String environmentName) {
    return forName("inventory.item", tenantId, environmentName);
  }

  public static KafkaTopic authority(String tenantId, String environmentName) {
    return forName("inventory.authority", tenantId, environmentName);
  }

  public static KafkaTopic asyncMigration(String tenantId, String environmentName) {
    return forName("inventory.async-migration", tenantId, environmentName);
  }

  public static KafkaTopic boundWith(String tenantId, String environmentName) {
    return forName("inventory.bound-with", tenantId, environmentName);
  }

  public static KafkaTopic forName(String name, String tenantId, String environmentName) {
    return new KafkaTopic(qualifyName(name, environmentName, tenantId));
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
