package org.folio;

import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.kafka.services.KafkaTopic;

public enum InventoryKafkaTopic implements KafkaTopic {

  ASYNC_MIGRATION("async-migration"),
  BOUND_WITH("bound-with"),
  CAMPUS("campus"),
  CLASSIFICATION_TYPE("classification-type"),
  CALL_NUMBER_TYPE("call-number-type"),
  HOLDINGS_RECORD("holdings-record"),
  INSTANCE("instance"),
  INSTANCE_CONTRIBUTION("instance-contribution"),
  INSTANCE_DATE_TYPE("instance-date-type"),
  INSTITUTION("institution"),
  ITEM("item"),
  LIBRARY("library"),
  LOCATION("location"),
  REINDEX_RECORDS("reindex-records"),
  REINDEX_FILE_READY("reindex.file-ready"),
  SERVICE_POINT("service-point"),
  SUBJECT_SOURCE("subject-source"),
  SUBJECT_TYPE("subject-type");

  private static final String DEFAULT_NUM_PARTITIONS_PROPERTY = "KAFKA_DOMAIN_TOPIC_NUM_PARTITIONS";
  private static final String DEFAULT_NUM_PARTITIONS_VALUE = "50";

  /**
   * Map where a key is {@link InventoryKafkaTopic} and value is a {@link Pair} of
   * environment variable name that specifies number of partitions for the topic and default value is not specified.
   */
  private static final Map<InventoryKafkaTopic, Pair<String, String>> TOPIC_PARTITION_MAP = Map.of(
    CLASSIFICATION_TYPE, Pair.of("KAFKA_CLASSIFICATION_TYPE_TOPIC_NUM_PARTITIONS", "1"),
    CALL_NUMBER_TYPE, Pair.of("KAFKA_CALL_NUMBER_TYPE_TOPIC_NUM_PARTITIONS", "1"),
    LOCATION, Pair.of("KAFKA_LOCATION_TOPIC_NUM_PARTITIONS", "1"),
    LIBRARY, Pair.of("KAFKA_LIBRARY_TOPIC_NUM_PARTITIONS", "1"),
    CAMPUS, Pair.of("KAFKA_CAMPUS_TOPIC_NUM_PARTITIONS", "1"),
    INSTITUTION, Pair.of("KAFKA_INSTITUTION_TOPIC_NUM_PARTITIONS", "1"),
    SUBJECT_TYPE, Pair.of("KAFKA_SUBJECT_TYPE_TOPIC_NUM_PARTITIONS", "1"),
    REINDEX_RECORDS, Pair.of("KAFKA_REINDEX_RECORDS_TOPIC_NUM_PARTITIONS", "16"),
    SUBJECT_SOURCE, Pair.of("KAFKA_SUBJECT_SOURCE_TOPIC_NUM_PARTITIONS", "1"),
    INSTANCE_DATE_TYPE, Pair.of("KAFKA_SUBJECT_SOURCE_TOPIC_NUM_PARTITIONS", "1")
  );

  private static final Map<InventoryKafkaTopic, Pair<String, String>> TOPIC_MESSAGE_RETENTION_MAP = Map.of(
    REINDEX_RECORDS, Pair.of("KAFKA_REINDEX_RECORDS_TOPIC_MESSAGE_RETENTION", "86400000") // 1 day
  );

  private static final Map<InventoryKafkaTopic, Pair<String, String>> TOPIC_MESSAGE_MAX_SIZE_MAP = Map.of(
    REINDEX_RECORDS, Pair.of("KAFKA_REINDEX_RECORDS_TOPIC_MAX_MESSAGE_SIZE", "10485760") // 10 MB
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
      .map(pair -> getPropertyValue(pair.getKey(), pair.getValue()))
      .orElse(getPropertyValue(DEFAULT_NUM_PARTITIONS_PROPERTY, DEFAULT_NUM_PARTITIONS_VALUE));
  }

  @Override
  public Integer messageRetentionTime() {
    return Optional.ofNullable(TOPIC_MESSAGE_RETENTION_MAP.get(this))
      .map(pair -> getPropertyValue(pair.getKey(), pair.getValue()))
      .orElse(null);
  }

  @Override
  public Integer messageMaxSize() {
    return Optional.ofNullable(TOPIC_MESSAGE_MAX_SIZE_MAP.get(this))
      .map(pair -> getPropertyValue(pair.getKey(), pair.getValue()))
      .orElse(null);
  }

  public static InventoryKafkaTopic byTopic(String topic) {
    for (InventoryKafkaTopic kafkaTopic : values()) {
      if (kafkaTopic.topicName().equals(topic)) {
        return kafkaTopic;
      }
    }
    throw new IllegalArgumentException("Unknown topic " + topic);
  }

  private int getPropertyValue(String propertyName, String defaultNumPartitions) {
    return Integer.parseInt(StringUtils.firstNonBlank(
      System.getenv(propertyName),
      System.getProperty(propertyName),
      System.getProperty(propertyName.toLowerCase().replace('_', '-')), defaultNumPartitions));
  }
}
