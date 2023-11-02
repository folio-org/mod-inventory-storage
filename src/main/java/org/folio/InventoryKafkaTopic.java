package org.folio;

import org.apache.commons.lang3.StringUtils;
import org.folio.kafka.services.KafkaTopic;

public enum InventoryKafkaTopic implements KafkaTopic {
  INSTANCE("instance"),
  ITEM("item"),
  HOLDINGS_RECORD("holdings-record"),
  INSTANCE_CONTRIBUTION("instance-contribution"),
  BOUND_WITH("bound-with"),
  ASYNC_MIGRATION("async-migration"),
  SERVICE_POINT("service-point");

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

  @Override
  public int numPartitions() {
    return Integer.parseInt(StringUtils.firstNonBlank(
      System.getenv("KAFKA_DOMAIN_TOPIC_NUM_PARTITIONS"),
      System.getProperty("KAFKA_DOMAIN_TOPIC_NUM_PARTITIONS"),
      System.getProperty("kafka-domain-topic-num-partitions"), "50"));
  }
}
