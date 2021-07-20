package org.folio.services.kafka.topic;

public enum KafkaTopic {
  INVENTORY_INSTANCE("inventory.instance"),
  INVENTORY_ITEM("inventory.item"),
  INVENTORY_HOLDINGS_RECORD("inventory.holdings-record");

  private final String topicName;

  KafkaTopic(String topicName) {
    this.topicName = topicName;
  }

  public String getTopicName() {
    return topicName;
  }
}
