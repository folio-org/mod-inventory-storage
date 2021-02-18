package org.folio.services.kafka.topic;

import java.util.stream.Stream;

public enum KafkaTopic {
  INVENTORY_INSTANCE("inventory.instance"),
  INVENTORY_ITEM("inventory.item"),
  INVENTORY_HOLDINGS_RECORD("inventory.holdings-record"),
  SEARCH_RESOURCES("search.resources");

  private final String topicName;

  KafkaTopic(String topicName) {
    this.topicName = topicName;
  }

  public String getTopicName() {
    return topicName;
  }

  public static KafkaTopic forName(String name) {
    return Stream.of(values())
      .filter(value -> value.getTopicName().equals(name))
      .findFirst()
      .orElse(null);
  }
}
