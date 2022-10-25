package org.folio;

import org.folio.kafka.services.KafkaTopic;

public enum InventoryKafkaTopic implements KafkaTopic {
  INSTANCE("instance", 50),
  ITEM("item", 50),
  HOLDINGS_RECORD("holdings-record", 50),
  AUTHORITY("authority", 50),
  INSTANCE_CONTRIBUTION("instance-contribution", 50),
  BOUND_WITH("bound-with", 50),
  ASYNC_MIGRATION("async-migration", 50);

  private final String topic;
  private final int partitions;

  InventoryKafkaTopic(String topic, int partitions) {
    this.topic = topic;
    this.partitions = partitions;
  }

  @Override
  public String moduleName() {
    return "inventory";
  }

  @Override
  public String topicName() {
    return topic;
  }

  @Override
  public int numPartitions() {
    return partitions;
  }

  @Override
  public short replicationFactor() {
    return 0;
  }
}
