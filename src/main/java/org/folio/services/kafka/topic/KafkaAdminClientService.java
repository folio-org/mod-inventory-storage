package org.folio.services.kafka.topic;

import static io.vertx.core.Future.succeededFuture;
import static io.vertx.kafka.admin.KafkaAdminClient.create;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.services.kafka.KafkaConfigHelper.getKafkaProperties;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;
import org.folio.util.ResourceUtil;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.admin.KafkaAdminClient;
import io.vertx.kafka.admin.NewTopic;

public final class KafkaAdminClientService {
  private static final Logger log = getLogger(KafkaAdminClientService.class);
  private static final String KAFKA_TOPICS_FILE = "kafka-topics.json";

  public static void createKafkaTopicsAsync(Vertx vertx) {
    final KafkaAdminClient kafkaAdminClient = create(vertx, getKafkaProperties());
    createKafkaTopicsAsync(kafkaAdminClient).onComplete(result -> {
      if (result.succeeded()) {
        log.info("Topics created successfully");
      } else {
        log.error("Unable to create topics", result.cause());
      }

      kafkaAdminClient.close(closeResult -> {
        if (closeResult.failed()) {
          log.error("Failed to close kafka admin client", closeResult.cause());
        }
      });
    });
  }

  static Future<Void> createKafkaTopicsAsync(KafkaAdminClient kafkaAdminClient) {
    final List<NewTopic> newTopics = readTopics();

    return kafkaAdminClient.listTopics().compose(topics -> {
      final List<NewTopic> topicsToCreate = newTopics.stream()
        .filter(newTopic -> !topics.contains(newTopic.getName()))
        .collect(Collectors.toList());

      if (topicsToCreate.isEmpty()) {
        log.info("All topics already exists, skipping creation");
        return succeededFuture();
      }

      log.info("Creating topics {}", topicsToCreate);
      return kafkaAdminClient.createTopics(topicsToCreate);
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
