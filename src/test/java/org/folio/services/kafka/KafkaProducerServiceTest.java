package org.folio.services.kafka;

import static org.folio.services.kafka.topic.KafkaTopic.INVENTORY_INSTANCE;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import io.vertx.kafka.client.producer.KafkaProducer;

public class KafkaProducerServiceTest {
  @Test
  @SuppressWarnings("unchecked")
  public void canThrowJacksonExceptionWhenCannotParseObject(){
    final var producer = new KafkaProducerService(mock(KafkaProducer.class));

    assertThrows("Unable to deserialize message", IllegalArgumentException.class,
      () -> producer.sendMessage("id", new Object(), INVENTORY_INSTANCE)
      .get(1, TimeUnit.SECONDS));
  }
}
