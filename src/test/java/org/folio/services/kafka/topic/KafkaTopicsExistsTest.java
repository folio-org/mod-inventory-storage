package org.folio.services.kafka.topic;

import static org.folio.utility.KafkaUtility.startKafka;
import static org.folio.utility.KafkaUtility.stopKafka;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import io.vertx.core.Future;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.kafka.admin.KafkaAdminClient;
import io.vertx.kafka.admin.NewTopic;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.kafka.common.errors.TopicExistsException;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class KafkaTopicsExistsTest {
  private static final short REPLICATION_FACTOR = 1;

  @Rule
  public RunTestOnContext rule = new RunTestOnContext();

  private KafkaAdminClient kafkaAdminClient;

  @BeforeClass
  public static void beforeAll() {
    startKafka();
  }

  @AfterClass
  public static void afterAll() {
    stopKafka();
  }

  @Before
  public void beforeEach() {
    kafkaAdminClient = KafkaAdminClient.create(rule.vertx(), KafkaConfig.builder()
      .kafkaHost(KafkaEnvironmentProperties.host())
      .kafkaPort(KafkaEnvironmentProperties.port())
      .build().getProducerProps());
  }

  @After
  public void afterEach(TestContext context) {
    kafkaAdminClient.deleteTopics(List.of("T1", "T2", "T3"))
      .compose(x -> kafkaAdminClient.close())
      .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void topics(TestContext context) {
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
      ).onComplete(context.asyncAssertFailure(cause ->
        assertThat(cause, is(instanceOf(TopicExistsException.class)))
      ))
      .otherwiseEmpty()
      .compose(x -> assertTopics(10000, List.of("T1", "T2", "T3")))
      .onComplete(context.asyncAssertSuccess());
  }

  private <T> Future<Void> assertTopics(int retries, List<String> expectedTopics) {
    return kafkaAdminClient.listTopics()
        .compose(topics -> {
          if (topics.containsAll(expectedTopics)) {
            return Future.succeededFuture();
          }
          if (retries <= 0) {
            var expected = expectedTopics.stream().collect(Collectors.joining(", "));
            var actual = topics.stream().collect(Collectors.joining(", "));
            return Future.failedFuture("listTopics() missing expected topics. "
                + "Expected: " + expected + ". Actual: " + actual);
          }
          return assertTopics(retries - 1, expectedTopics);
        });
  }
}
