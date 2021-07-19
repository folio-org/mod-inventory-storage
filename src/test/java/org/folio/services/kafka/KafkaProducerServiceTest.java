package org.folio.services.kafka;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.folio.services.kafka.topic.KafkaTopic;
import org.junit.Test;

import io.vertx.kafka.client.producer.KafkaProducer;

public class KafkaProducerServiceTest {
  @Test
  @SuppressWarnings("unchecked")
  public void canThrowJacksonExceptionWhenCannotParseObject(){
    final var producer = new KafkaProducerService(mock(KafkaProducer.class));

    final KafkaMessage<Object> message = KafkaMessage.builder()
      .key("id").payload(new Object()).topicName(
        KafkaTopic.instance("folio", "foo-tenant").getTopicName()).build();

    assertThrows("Unable to deserialize message", ExecutionException.class,
      () -> producer.sendMessage(message).toCompletionStage().toCompletableFuture()
        .get(1, TimeUnit.SECONDS));
  }
}
