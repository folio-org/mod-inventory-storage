package org.folio.services.kafka;

import static org.folio.services.kafka.KafkaConfigHelper.getKafkaProperties;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.Assert.assertThrows;

import java.util.Properties;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

public class KafkaConfigHelperTest {
  private static final String STRING_SERIALIZER = "org.apache.kafka.common.serialization.StringSerializer";
  private static final String KAFKA_HOST = "KAFKA_HOST";
  private static final String KAFKA_PORT = "KAFKA_PORT";
  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

  @After
  public void removeEnvVariables() {
    environmentVariables.clear(KAFKA_HOST, KAFKA_PORT);
  }

  @Test
  public void canLoadDefaultKafkaProperties() {
    final Properties kafkaProperties = getKafkaProperties();

    assertThat(kafkaProperties, hasEntry("bootstrap.servers", "localhost:9092"));
    assertThat(kafkaProperties, hasEntry("enable.idempotence", "true"));
    assertThat(kafkaProperties, hasEntry("key.serializer", STRING_SERIALIZER));
    assertThat(kafkaProperties, hasEntry("value.serializer", STRING_SERIALIZER));
  }

  @Test
  public void canUseKafkaHostAndPortFromEnvVariables() {
    environmentVariables.set(KAFKA_HOST, "kafka");
    environmentVariables.set(KAFKA_PORT, "9999");

    assertThat(getKafkaProperties(), hasEntry("bootstrap.servers", "kafka:9999"));
  }

  @Test
  public void shouldThrowIllegalArgumentExceptionOnIoError() {
    assertThrows("Unable to load kafka config", IllegalArgumentException.class,
      () -> getKafkaProperties("broken-file.properties"));
  }
}
