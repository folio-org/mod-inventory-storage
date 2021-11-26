package org.folio.services.kafka.topic;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.admin.KafkaAdminClient;
import io.vertx.kafka.admin.NewTopic;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.KafkaConfig;
import org.folio.services.kafka.KafkaProperties;
import org.folio.util.ResourceUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.vertx.kafka.admin.KafkaAdminClient.create;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.services.kafka.KafkaProperties.getReplicationFactor;

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

  public Future<Void> createKafkaTopics(String tenantId, String environmentName) {
    final List<NewTopic> topics = readTopics()
        .map(topic -> qualifyName(topic, environmentName, tenantId))
        .map(topic -> topic.setReplicationFactor(getReplicationFactor()))
        .collect(Collectors.toList());
    final KafkaAdminClient kafkaAdminClient = clientFactory.get();
    return createKafkaTopics(1, topics, kafkaAdminClient)
      .eventually(x -> kafkaAdminClient.close())
      .onSuccess(result -> log.info("Topics created successfully"))
      .onFailure(cause -> log.error("Unable to create topics", cause));
  }

  private Future<Void> createKafkaTopics(int attempt, List<NewTopic> topics,
      KafkaAdminClient kafkaAdminClient) {

    return kafkaAdminClient.listTopics()
        .compose(existingTopics -> {
          final List<NewTopic> newTopics = new ArrayList<>(topics);
          newTopics.removeIf(t -> existingTopics.contains(t.getName()));
          if (newTopics.isEmpty()) {
            return Future.succeededFuture();
          }
          return kafkaAdminClient.createTopics(newTopics);
        })
        .recover(e -> {
          if (! (e instanceof org.apache.kafka.common.errors.TopicExistsException)) {
            return Future.failedFuture(e);
          }
          if (attempt >= 30) {
            log.error("attempt {} failed: {}", attempt, e.getMessage(), e);
            return Future.failedFuture(e);
          }
          log.info("Create topic attempt {} failed, sleeping and trying next attempt.", attempt);
          return sleep().compose(x -> createKafkaTopics(attempt + 1, topics, kafkaAdminClient));
        });
  }

  /**
   * Sleep for 100 milliseconds.
   */
  private Future<Void> sleep() {
    Context context = Vertx.currentContext();
    Vertx vertx = context == null ? Vertx.vertx() : context.owner();
    return Future.future(promise -> vertx.setTimer(100, x -> promise.tryComplete()));
  }

  public Future<Void> deleteKafkaTopics(String tenantId, String environmentName) {
    List<String> topicsToDelete = readTopics()
      .map(topic -> qualifyName(topic, environmentName, tenantId))
      .map(NewTopic::getName)
      .collect(Collectors.toList());
    return withKafkaAdminClient(kafkaAdminClient -> kafkaAdminClient.deleteTopics(topicsToDelete))
      .onSuccess(x -> log.info("Topics deleted successfully"))
      .onFailure(e -> log.error("Unable to delete topics", e));
  }

  private <T> Future<T> withKafkaAdminClient(Function<KafkaAdminClient, Future<T>> function) {
    final KafkaAdminClient kafkaAdminClient = clientFactory.get();
    return function.apply(kafkaAdminClient)
      .eventually(x ->
        kafkaAdminClient.close()
          .onFailure(e -> log.error("Failed to close kafka admin client", e)));
  }

  private NewTopic qualifyName(NewTopic topic, String environmentName, String tenantId) {
    return topic.setName(String.join(".", environmentName, tenantId, topic.getName()));
  }

  private Stream<NewTopic> readTopics() {
    final JsonObject topics = new JsonObject(ResourceUtil.asString(KAFKA_TOPICS_FILE));

    return topics.getJsonArray("topics", new JsonArray()).stream()
      .map(obj -> (JsonObject) obj)
      .map(NewTopic::new);
  }
}
