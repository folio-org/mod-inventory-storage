package org.folio;

import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.kafka.services.KafkaTopic;

public enum InventoryKafkaTopic implements KafkaTopic {

  INSTANCE("instance"),
  ITEM("item"),
  HOLDINGS_RECORD("holdings-record"),
  INSTANCE_CONTRIBUTION("instance-contribution"),
  BOUND_WITH("bound-with"),
  ASYNC_MIGRATION("async-migration"),
  SERVICE_POINT("service-point"),
  CLASSIFICATION_TYPE("classification-type"),
  LOCATION("location"),
  LIBRARY("library"),
  CAMPUS("campus"),
  INSTITUTION("institution");

  private static final String DEFAULT_NUM_PARTITIONS_PROPERTY = "KAFKA_DOMAIN_TOPIC_NUM_PARTITIONS";
  private static final String DEFAULT_NUM_PARTITIONS_VALUE = "50";

  /**
   * Map where a key is {@link InventoryKafkaTopic} and value is a {@link Pair} of
   * environment variable name that specifies number of partitions for the topic and default value is not specified.
   */
  private static final Map<InventoryKafkaTopic, Pair<String, String>> TOPIC_PARTITION_MAP = Map.of(
    CLASSIFICATION_TYPE, Pair.of("KAFKA_CLASSIFICATION_TYPE_TOPIC_NUM_PARTITIONS", "1"),
    LOCATION, Pair.of("KAFKA_LOCATION_TOPIC_NUM_PARTITIONS", "1"),
    LIBRARY, Pair.of("KAFKA_LIBRARY_TOPIC_NUM_PARTITIONS", "1"),
    CAMPUS, Pair.of("KAFKA_CAMPUS_TOPIC_NUM_PARTITIONS", "1"),
    INSTITUTION, Pair.of("KAFKA_INSTITUTION_TOPIC_NUM_PARTITIONS", "1")
  );

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
    return Optional.ofNullable(TOPIC_PARTITION_MAP.get(this))
      .map(pair -> getNumberOfPartitions(pair.getKey(), pair.getValue()))
      .orElse(getNumberOfPartitions(DEFAULT_NUM_PARTITIONS_PROPERTY, DEFAULT_NUM_PARTITIONS_VALUE));
  }

  private int getNumberOfPartitions(String propertyName, String defaultNumPartitions) {
    return Integer.parseInt(StringUtils.firstNonBlank(
      System.getenv(propertyName),
      System.getProperty(propertyName),
      System.getProperty(propertyName.toLowerCase().replace('_', '-')), defaultNumPartitions));
  }

}
