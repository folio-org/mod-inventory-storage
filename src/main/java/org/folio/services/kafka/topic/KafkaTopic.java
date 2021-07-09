package org.folio.services.kafka.topic;

public class KafkaTopic {
  public static KafkaTopic instance(String tenantId) {
    return new KafkaTopic(prefixWithEnvironment(
      prefixWithTenantId("inventory.instance", tenantId)));
  }

  public static KafkaTopic holdingsRecord(String tenantId) {
    return new KafkaTopic(prefixWithEnvironment(
      prefixWithTenantId("inventory.holdings-record", tenantId)));
  }

  public static KafkaTopic item(String tenantId) {
    return new KafkaTopic(prefixWithEnvironment(
      prefixWithTenantId("inventory.item", tenantId)));
  }

  private final String topicName;

  KafkaTopic(String topicName) {
    this.topicName = topicName;
  }

  public String getTopicName() {
    return topicName;
  }

  private static String prefixWithTenantId(String topicName, String tenantId) {
    return tenantId + "." + topicName;
  }

  private static String prefixWithEnvironment(String topicName) {
    final var environmentName = System.getenv().getOrDefault("ENV", "folio");

    return environmentName + "." + topicName;
  }
}
