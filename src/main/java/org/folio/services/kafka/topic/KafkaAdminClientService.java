package org.folio.services.kafka.topic;

import static io.vertx.core.Future.succeededFuture;
import static io.vertx.kafka.admin.KafkaAdminClient.create;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.Environment.getEnvironmentName;
import static org.folio.services.kafka.KafkaProperties.getReplicationFactor;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.Logger;
import org.folio.kafka.KafkaConfig;
import org.folio.services.kafka.KafkaProperties;
import org.folio.util.ResourceUtil;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.admin.KafkaAdminClient;
import io.vertx.kafka.admin.NewTopic;

public class KafkaAdminClientService {
  private static final Logger log = getLogger(KafkaAdminClientService.class);
  private static final String KAFKA_TOPICS_FILE = "kafka-topics.json";
  private final Supplier<KafkaAdminClient> clientFactory;

  public KafkaAdminClientService(Vertx vertx) {
    this(() -> create(vertx, KafkaConfig.builder()
      .kafkaHost(KafkaProperties.getHost())
      .kafkaPort(KafkaProperties.getPort())
      .build().getProducerProps()));
  }

  public KafkaAdminClientService(Supplier<KafkaAdminClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  public Future<Void> createKafkaTopics(String tenant) {
    final KafkaAdminClient kafkaAdminClient = clientFactory.get();
    return createKafkaTopics(tenant, kafkaAdminClient).onComplete(result -> {
      if (result.succeeded()) {
        log.info("Topics created successfully");
      } else {
        log.error("Unable to create topics", result.cause());
      }

      kafkaAdminClient.close().onComplete(closeResult -> {
        if (closeResult.failed()) {
          log.error("Failed to close kafka admin client", closeResult.cause());
        }
      });
    });
  }

  private Future<Void> createKafkaTopics(String tenantId, KafkaAdminClient kafkaAdminClient) {
    final List<NewTopic> expectedTopics = readTopics()
      .map(topic -> prefixWith(tenantId, topic))
      .map(topic -> prefixWith(getEnvironmentName(), topic))
      .collect(Collectors.toList());

    return kafkaAdminClient.listTopics().compose(existingTopics -> {
      final List<NewTopic> topicsToCreate = expectedTopics.stream()
        .filter(topic -> !existingTopics.contains(topic.getName()))
        .map(topic -> topic.setReplicationFactor(getReplicationFactor()))
        .collect(Collectors.toList());

      if (topicsToCreate.isEmpty()) {
        log.info("All topics already exists, skipping creation");
        return succeededFuture();
      }

      log.info("Creating topics {}", topicsToCreate);
      return kafkaAdminClient.createTopics(topicsToCreate);
    });
  }

  private NewTopic prefixWith(String value, NewTopic topic) {
    return topic.setName(value + "." + topic.getName());
  }

  private Stream<NewTopic> readTopics() {
    final JsonObject topics = new JsonObject(ResourceUtil.asString(KAFKA_TOPICS_FILE));

    return topics.getJsonArray("topics", new JsonArray()).stream()
      .map(obj -> (JsonObject) obj)
      .map(NewTopic::new);
  }
}
