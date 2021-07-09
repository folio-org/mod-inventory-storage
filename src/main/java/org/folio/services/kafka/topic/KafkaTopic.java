package org.folio.services.kafka.topic;

public enum KafkaTopic {
  INVENTORY_INSTANCE("inventory.instance"),
  INVENTORY_ITEM("inventory.item"),
  INVENTORY_HOLDINGS_RECORD("inventory.holdings-record");

  public static KafkaTopic instance() {
    return INVENTORY_INSTANCE;
  }

  public static KafkaTopic holdingsRecord() {
    return INVENTORY_HOLDINGS_RECORD;
  }

  public static KafkaTopic item() {
    return INVENTORY_ITEM;
  }

  private final String topicName;

  KafkaTopic(String topicName) {
    this.topicName = topicName;
  }

  public String getTopicName() {
    return topicName;
  }
}
