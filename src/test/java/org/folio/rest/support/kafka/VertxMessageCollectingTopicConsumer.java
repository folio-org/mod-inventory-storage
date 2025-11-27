package org.folio.rest.support.kafka;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.serialization.JsonObjectDeserializer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.rest.api.TestBase;

public class VertxMessageCollectingTopicConsumer {

  private static final Logger LOG = LogManager.getLogger();

  private final Set<String> topicNames;
  private final MessageCollector messageCollector;
  private KafkaConsumer<String, JsonObject> consumer;

  public VertxMessageCollectingTopicConsumer(Set<String> topicNames,
                                             MessageCollector messageCollector) {

    this.topicNames = topicNames;
    this.messageCollector = messageCollector;
  }

  private static Map<String, String> consumerProperties() {
    Map<String, String> config = new HashMap<>();
    config.put("bootstrap.servers",
      KafkaEnvironmentProperties.host() + ":" + KafkaEnvironmentProperties.port());
    config.put("key.deserializer", StringDeserializer.class.getName());
    config.put("value.deserializer", JsonObjectDeserializer.class.getName());
    config.put("group.id", "folio_test");
    config.put("auto.offset.reset", "earliest");
    config.put("enable.auto.commit", "false");

    return config;
  }

  void subscribe(Vertx vertx) {
    consumer = KafkaConsumer.create(vertx, consumerProperties());

    consumer.handler(messageCollector::acceptMessage);
    LOG.info("Subscribing to topics: {}", topicNames);
    TestBase.get(consumer.subscribe(topicNames));
  }

  void unsubscribe() {
    if (consumer != null) {
      LOG.info("Unsubscribing from topics: {}", topicNames);
      TestBase.get(consumer.unsubscribe());
      TestBase.get(consumer.close());
    }
  }
}
