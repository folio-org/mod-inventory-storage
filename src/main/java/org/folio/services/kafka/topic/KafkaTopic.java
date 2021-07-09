package org.folio.services.kafka.topic;

public class KafkaTopic {
  public static KafkaTopic instance() {
    return new KafkaTopic("inventory.instance");
  }

  public static KafkaTopic holdingsRecord() {
    return new KafkaTopic("inventory.holdings-record");
  }

  public static KafkaTopic item() {
    return new KafkaTopic("inventory.item");
  }

  private final String topicName;

  KafkaTopic(String topicName) {
    this.topicName = topicName;
  }

  public String getTopicName() {
    return topicName;
  }
}
