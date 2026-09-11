package org.folio.services.kafka.topic;

import static org.folio.utility.KafkaUtility.startKafka;
import static org.folio.utility.KafkaUtility.stopKafka;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.kafka.admin.KafkaAdminClient;
import io.vertx.kafka.admin.NewTopic;
import java.util.List;
import org.apache.kafka.common.errors.TopicExistsException;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class KafkaTopicsExistsTest {
  private static final short REPLICATION_FACTOR = 1;

  private KafkaAdminClient kafkaAdminClient;

  @BeforeAll
  static void beforeAll() {
    startKafka();
  }

  @AfterAll
  static void afterAll() {
    stopKafka();
  }

  @BeforeEach
  void beforeEach(Vertx vertx) {
    kafkaAdminClient = KafkaAdminClient.create(vertx, KafkaConfig.builder()
      .kafkaHost(KafkaEnvironmentProperties.host())
      .kafkaPort(KafkaEnvironmentProperties.port())
      .build().getProducerProps());
  }

  @AfterEach
  void afterEach(VertxTestContext context) {
    kafkaAdminClient.deleteTopics(List.of("T1", "T2", "T3"))
      .compose(x -> kafkaAdminClient.close())
      .onComplete(context.succeedingThenComplete());
  }

  @Test
  void topics(VertxTestContext context) {
    kafkaAdminClient.createTopics(
        List.of(
          new NewTopic("T1", 1, REPLICATION_FACTOR),
          new NewTopic("T2", 1, REPLICATION_FACTOR)
        ))
      .compose(x -> kafkaAdminClient.createTopics(
        List.of(
          new NewTopic("T1", 1, REPLICATION_FACTOR),
          new NewTopic("T3", 1, REPLICATION_FACTOR)
        ))
      ).onComplete(context.failing(cause -> context.verify(() ->
        assertInstanceOf(TopicExistsException.class, cause)
      )))
      .otherwiseEmpty()
      .compose(x -> assertTopics(10000, List.of("T1", "T2", "T3")))
      .onComplete(context.succeedingThenComplete());
  }

  private Future<Void> assertTopics(int retries, List<String> expectedTopics) {
    return kafkaAdminClient.listTopics()
      .compose(topics -> {
        if (topics.containsAll(expectedTopics)) {
          return Future.succeededFuture();
        }
        if (retries <= 0) {
          var expected = String.join(", ", expectedTopics);
          var actual = String.join(", ", topics);
          return Future.failedFuture("listTopics() missing expected topics. "
                                     + "Expected: " + expected + ". Actual: " + actual);
        }
        return assertTopics(retries - 1, expectedTopics);
      });
  }
}
