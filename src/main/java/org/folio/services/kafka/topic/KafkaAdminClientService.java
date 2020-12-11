package org.folio.services.kafka.topic;

import static io.vertx.kafka.admin.KafkaAdminClient.create;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.services.kafka.KafkaConfigHelper.getKafkaProperties;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;
import org.folio.util.ResourceUtil;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.admin.KafkaAdminClient;
import io.vertx.kafka.admin.NewTopic;

public final class KafkaAdminClientService {
  private static final Logger log = getLogger(KafkaAdminClientService.class);
  public static final String KAFKA_TOPICS_FILE = "kafka-topics.json";

  public static void createKafkaTopicsAsync(Vertx vertx) {
    final KafkaAdminClient kafkaAdminClient = create(vertx, getKafkaProperties());
    final List<NewTopic> newTopics = readTopics();

    kafkaAdminClient.listTopics(topics -> {
      if (topics.failed()) {
        log.error("Unable to retrieve kafka topics", topics.cause());
        return;
      }

      final List<NewTopic> topicsToCreate = newTopics.stream()
        .filter(newTopic -> !topics.result().contains(newTopic.getName()))
        .collect(Collectors.toList());

      log.info("Creating topics {}", topicsToCreate);
      kafkaAdminClient.createTopics(topicsToCreate, result -> {
        if (result.failed()) {
          log.warn("Unable to close kafka admin client", result.cause());
        } else {
          log.info("Kafka admin client closed successfully");
        }
      });
    });
  }

  private static List<NewTopic> readTopics() {
    final JsonObject topics = new JsonObject(ResourceUtil.asString(KAFKA_TOPICS_FILE));

    return topics.getJsonArray("topics", new JsonArray()).stream()
      .map(obj -> (JsonObject) obj)
      .map(NewTopic::new)
      .collect(Collectors.toList());
  }
}
