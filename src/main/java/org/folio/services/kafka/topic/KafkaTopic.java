package org.folio.services.kafka.topic;

import static org.folio.Environment.getEnvironmentName;

public class KafkaTopic {
  public static KafkaTopic instance(String tenantId) {
    return new KafkaTopic(prefixWith(getEnvironmentName(),
      prefixWith(tenantId, "inventory.instance")));
  }

  public static KafkaTopic holdingsRecord(String tenantId) {
    return new KafkaTopic(prefixWith(getEnvironmentName(),
      prefixWith(tenantId, "inventory.holdings-record")));
  }

  public static KafkaTopic item(String tenantId) {
    return new KafkaTopic(prefixWith(getEnvironmentName(),
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
