package org.folio.rest.api;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.kafka.admin.KafkaAdminClient;
import io.vertx.kafka.admin.NewTopic;
import org.folio.kafka.KafkaConfig;
import org.folio.services.kafka.KafkaProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(VertxUnitRunner.class)
public class KafkaTopicsExistsTest extends TestBase {
  final short REPLICATION_FACTOR = 1;

  KafkaAdminClient kafkaAdminClient;

  @Before
  public void before() {
    Vertx vertx = StorageTestSuite.getVertx();
    kafkaAdminClient = KafkaAdminClient.create(vertx, KafkaConfig.builder()
      .kafkaHost(KafkaProperties.getHost())
      .kafkaPort(KafkaProperties.getPort())
      .build().getProducerProps());
  }

  @After
  public void after(TestContext context) {
    kafkaAdminClient.deleteTopics(List.of("T1", "T2", "T3"))
      .compose(x -> kafkaAdminClient.close())
      .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void topics(TestContext context) {
    Future<Void> future = kafkaAdminClient.createTopics(
      List.of(
        new NewTopic("T1", 1, REPLICATION_FACTOR),
        new NewTopic("T2", 1, REPLICATION_FACTOR)
      ));
    future = future.compose(x -> kafkaAdminClient.createTopics(
      List.of(
        new NewTopic("T1", 1, REPLICATION_FACTOR),
        new NewTopic("T3", 1, REPLICATION_FACTOR)
      ))
    ).otherwise(cause -> {
      if (cause instanceof org.apache.kafka.common.errors.TopicExistsException) {
        return null;
      }
      throw new RuntimeException(cause);
    });
    // test that T3 got inserted, while T1 already being present
    future.compose(x -> kafkaAdminClient.listTopics())
      .onComplete(context.asyncAssertSuccess(topics ->
        context.assertTrue(topics.containsAll(List.of("T1", "T2", "T3")))
      )).mapEmpty();
  }
}
