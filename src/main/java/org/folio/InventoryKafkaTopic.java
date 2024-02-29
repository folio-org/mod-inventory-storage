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
  SERVICE_POINT("service-point"),
  CLASSIFICATION_TYPE("classification-type");

  private static final String DEFAULT_NUM_PARTITIONS_PROPERTY = "KAFKA_DOMAIN_TOPIC_NUM_PARTITIONS";
  private static final String DEFAULT_NUM_PARTITIONS_VALUE = "50";
  private static final String CLASSIFICATION_TYPE_NUM_PARTITIONS_PROPERTY =
    "KAFKA_CLASSIFICATION_TYPE_TOPIC_NUM_PARTITIONS";
  private static final String CLASSIFICATION_TYPE_NUM_PARTITIONS_VALUE = "1";
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
    if (this == CLASSIFICATION_TYPE) {
      return getNumberOfPartitions(CLASSIFICATION_TYPE_NUM_PARTITIONS_PROPERTY,
        CLASSIFICATION_TYPE_NUM_PARTITIONS_VALUE);
    }
    return getNumberOfPartitions(DEFAULT_NUM_PARTITIONS_PROPERTY, DEFAULT_NUM_PARTITIONS_VALUE);
  }

  private int getNumberOfPartitions(String propertyName, String defaultNumPartitions) {
    return Integer.parseInt(StringUtils.firstNonBlank(
      System.getenv(propertyName),
      System.getProperty(propertyName),
      System.getProperty(propertyName.toLowerCase().replace('_', '-')), defaultNumPartitions));
  }

}
