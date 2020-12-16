package org.folio.services.kafka.topic;

public enum KafkaTopic {
  INVENTORY_INSTANCE("inventory.instance");

  private final String topicName;

  KafkaTopic(String topicName) {
    this.topicName = topicName;
  }

  public String getTopicName() {
    return topicName;
  }
}
