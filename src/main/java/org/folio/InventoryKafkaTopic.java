package org.folio;

import org.folio.kafka.services.KafkaTopic;

public enum InventoryKafkaTopic implements KafkaTopic {
  INSTANCE("instance"),
  ITEM("item"),
  HOLDINGS_RECORD("holdings-record"),
  AUTHORITY("authority"),
  INSTANCE_CONTRIBUTION("instance-contribution"),
  BOUND_WITH("bound-with"),
  ASYNC_MIGRATION("async-migration");

  private final String topic;

  InventoryKafkaTopic(String topic) {
    this.topic = topic;
  }

  @Override
  public String moduleName() {
    return "inventory";
  }

  @Override
  public String topicName() {
    return topic;
  }
}
