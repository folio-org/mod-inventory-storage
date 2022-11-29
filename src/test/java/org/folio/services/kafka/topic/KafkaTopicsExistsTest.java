package org.folio.services.kafka.topic;

import static org.folio.utility.KafkaUtility.startKafka;
import static org.folio.utility.KafkaUtility.stopKafka;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.ModuleUtility.removeTenant;
import static org.folio.utility.ModuleUtility.startVerticle;
import static org.folio.utility.ModuleUtility.stopVerticle;
import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.kafka.admin.KafkaAdminClient;
import io.vertx.kafka.admin.NewTopic;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.persist.PostgresClient;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class KafkaTopicsExistsTest {
  private static final short REPLICATION_FACTOR = 1;

  private KafkaAdminClient kafkaAdminClient;

  @BeforeClass
  public static void beforeAll()
      throws InterruptedException,
      ExecutionException,
      TimeoutException {

    // tests expect English error messages only, no Danish/German/...
    Locale.setDefault(Locale.US);

    PostgresClient.setPostgresTester(new PostgresTesterContainer());
    startKafka();
    startVerticle(TENANT_ID);
  }

  @AfterClass
  public static void afterAll(TestContext context)
      throws InterruptedException,
      ExecutionException,
      TimeoutException {

    removeTenant(TENANT_ID);
    stopVerticle();
    stopKafka();
    PostgresClient.stopPostgresTester();
  }

  @Before
  public void beforeEach() {
    kafkaAdminClient = KafkaAdminClient.create(getVertx(), KafkaConfig.builder()
      .kafkaHost(KafkaEnvironmentProperties.host())
      .kafkaPort(KafkaEnvironmentProperties.port())
      .build().getProducerProps());
  }

  @After
  public void afterEach(TestContext context)
      throws InterruptedException,
      ExecutionException,
      TimeoutException {

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
      ).otherwise(cause -> {
        if (cause instanceof TopicExistsException) {
          return null;
        }
        throw new RuntimeException(cause);
      }).compose(x -> kafkaAdminClient.listTopics())
      .onComplete(context.asyncAssertSuccess(topics ->
        context.assertTrue(topics.containsAll(List.of("T1", "T2", "T3")))
      ));
  }
}
