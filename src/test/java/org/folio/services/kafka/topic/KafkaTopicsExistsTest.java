package org.folio.services.kafka.topic;

import static org.folio.utility.KafkaUtility.startKafka;
import static org.folio.utility.KafkaUtility.stopKafka;
import static org.folio.utility.VertxUtility.getVertx;
import static org.folio.utility.VertxUtility.removeTenant;
import static org.folio.utility.VertxUtility.startVertx;
import static org.folio.utility.VertxUtility.stopVertx;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.kafka.admin.KafkaAdminClient;
import io.vertx.kafka.admin.NewTopic;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.persist.PostgresClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class KafkaTopicsExistsTest {
  public static final String TENANT_ID = "test_topics_tenant";
  public static final short REPLICATION_FACTOR = 1;

  private KafkaAdminClient kafkaAdminClient;

  @Before
  public void before()
      throws InterruptedException,
      ExecutionException,
      TimeoutException {

    // tests expect English error messages only, no Danish/German/...
    Locale.setDefault(Locale.US);

    PostgresClient.setPostgresTester(new PostgresTesterContainer());
    startKafka();
    startVertx(TENANT_ID);

    kafkaAdminClient = KafkaAdminClient.create(getVertx(), KafkaConfig.builder()
      .kafkaHost(KafkaEnvironmentProperties.host())
      .kafkaPort(KafkaEnvironmentProperties.port())
      .build().getProducerProps());
  }

  @After
  public void after(TestContext context)
      throws InterruptedException,
      ExecutionException,
      TimeoutException {

    kafkaAdminClient.deleteTopics(List.of("T1", "T2", "T3"))
      .compose(x -> kafkaAdminClient.close())
      .onComplete(context.asyncAssertSuccess());

    removeTenant(TENANT_ID);
    stopKafka();
    stopVertx();
    PostgresClient.stopPostgresTester();
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
      ).otherwise(cause -> {
        if (cause instanceof org.apache.kafka.common.errors.TopicExistsException) {
          return null;
        }
        throw new RuntimeException(cause);
      }).compose(x -> kafkaAdminClient.listTopics())
      .onComplete(context.asyncAssertSuccess(topics ->
        context.assertTrue(topics.containsAll(List.of("T1", "T2", "T3")))
      ));
  }
}
